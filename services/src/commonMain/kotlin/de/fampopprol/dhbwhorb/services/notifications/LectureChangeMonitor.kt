/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.dao.SyncMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.SyncMetadataEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
import io.github.aakira.napier.Napier
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlin.math.abs

/**
 * Detects what changed between the timetable Dualis serves now and the one in the local cache.
 *
 * **Two speeds, because the two questions cost differently.** A full check of a week is one request
 * for the grid plus one per lecture, because lecturers and rooms only exist on a lecture's own
 * page. The grid alone is a single request. So the current week — where a change matters today —
 * is checked in full on every run, and the next [FUTURE_WEEKS] weeks are checked by their grid
 * only, at most every [FUTURE_SWEEP_HOURS] hours. A future week whose grid moved is then fetched
 * in full, so the detailed comparison still happens; it just happens for one week instead of five.
 * A future week the app has never loaded is fetched in full once and stored without notifying, so
 * that it *has* a previous state to be compared against from then on.
 *
 * **Nothing that has already happened is reported.** Both sides of every comparison drop lectures
 * that ended before now, and no week before the current one is looked at at all. A notification
 * about last Monday helps nobody.
 *
 * **Compares first and writes second:** a week is only replaced in the cache once a change has been
 * found, so an unchanged week costs no database writes and the "old" side is genuinely the
 * previous state.
 */
class LectureChangeMonitor(
    private val dualisLectureServiceFactory: () -> DualisLectureService,
    private val lectureEventDao: LectureEventDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val clock: () -> LocalDateTime = { TimeHelper.now() },
) {
    private val dualisLectureService by lazy { dualisLectureServiceFactory() }

    companion object {
        private const val TAG = "LectureChangeMonitor"

        /**
         * How far ahead the grid sweep looks. Beyond a month a timetable is provisional anyway,
         * and pulling to refresh in the app covers the rest.
         */
        const val FUTURE_WEEKS = 4

        /** The grid sweep is this much lazier than the hourly run that carries it. */
        const val FUTURE_SWEEP_HOURS = 4

        private const val SYNC_KEY_FUTURE_SWEEP = "lecture_monitor_future_sweep"
    }

    suspend fun checkForChanges(): MonitorResult {
        return try {
            val now = clock()
            val changes = mutableListOf<LectureChange>()
            var lecturesChecked: Int

            // ── The current week, in full ────────────────────────────────────────────────────
            val (weekStart, weekEnd) = weekAround(now, offset = 0)
            Napier.d("Full check of $weekStart..$weekEnd, ignoring anything before $now", tag = TAG)

            val current = when (val outcome = checkWeekInFull(now, weekStart, weekEnd)) {
                is Outcome.Ok -> outcome.value
                is Outcome.Err -> return MonitorResult.Error(outcome.error)
            }
            changes += current.changes
            lecturesChecked = current.lecturesChecked

            // ── The next few weeks, by their grid ────────────────────────────────────────────
            if (isFutureSweepDue(now)) {
                Napier.d("Grid sweep of the next $FUTURE_WEEKS week(s)", tag = TAG)
                for (offset in 1..FUTURE_WEEKS) {
                    when (val outcome = checkFutureWeekByGrid(now, offset)) {
                        is Outcome.Ok -> {
                            changes += outcome.value.changes
                            lecturesChecked += outcome.value.lecturesChecked
                        }
                        // One unreachable week must not discard what the current week found.
                        is Outcome.Err -> Napier.w(
                            "Week +$offset could not be checked: ${outcome.error}",
                            tag = TAG,
                        )
                    }
                }
                recordFutureSweep(now)
            } else {
                Napier.d("Grid sweep not due yet", tag = TAG)
            }

            if (changes.isEmpty()) {
                MonitorResult.NoChanges(lecturesChecked)
            } else {
                changes.groupBy { it::class.simpleName }.forEach { (type, list) ->
                    Napier.d("$type: ${list.size}", tag = TAG)
                }
                MonitorResult.Changes(changes)
            }
        } catch (e: Exception) {
            Napier.e("Exception during change check: ${e.message}", e, tag = TAG)
            MonitorResult.Error(AppError.Unexpected(e.message ?: "lecture monitoring"))
        }
    }

    private data class WeekOutcome(val changes: List<LectureChange>, val lecturesChecked: Int)

    /**
     * Fetches [weekStart]..[weekEnd] with lecturers and rooms and compares it against the cache.
     */
    private suspend fun checkWeekInFull(
        now: LocalDateTime,
        weekStart: LocalDateTime,
        weekEnd: LocalDateTime,
    ): Outcome<WeekOutcome> {
        val old = cachedLectures(weekStart, weekEnd).stillAhead(now)

        val fetched = when (val outcome = dualisLectureService.getWeeklyLecturesForWeek(weekStart, weekEnd)) {
            is Outcome.Ok -> outcome.value
            is Outcome.Err -> return outcome
        }

        val changes = diff(old, fetched.filter { it.endTime > now })
        if (changes.isNotEmpty()) {
            // The whole week is written back, not just the part that is still ahead: the cache
            // holds the timetable, and half a week would be a worse answer than a stale one.
            when (val saved = dualisLectureService.saveLecturesToDatabase(fetched, weekStart, weekEnd)) {
                is Outcome.Ok -> Unit
                is Outcome.Err -> {
                    // The changes were found against a state that is now not persisted, so the
                    // next run would find them again. Better to say so than to notify twice.
                    Napier.e("Could not persist the new lectures: ${saved.error}", tag = TAG)
                    return Outcome.Err(saved.error)
                }
            }
        }
        return Outcome.Ok(WeekOutcome(changes, old.size))
    }

    /**
     * Checks one future week using the weekly grid, and only pays for the detail pages if the grid
     * says something moved.
     *
     * The grid knows no lecturers, and its room string is not the one the cache holds (the cache
     * keeps the rooms from each lecture's own page). So the grid comparison looks only at what both
     * sides describe the same way — subject, start, end, exam flag — and any difference at all is
     * the signal to go and look properly.
     *
     * A week with nothing cached has no previous state to compare against, so it is fetched in
     * full once and stored — see [seedFutureWeek].
     */
    private suspend fun checkFutureWeekByGrid(now: LocalDateTime, weekOffset: Int): Outcome<WeekOutcome> {
        val (start, end) = weekAround(now, weekOffset)
        val old = cachedLectures(start, end).stillAhead(now)
        if (old.isEmpty()) return seedFutureWeek(weekOffset, start, end)

        val grid = when (val outcome = dualisLectureService.getWeeklySkeletonForWeek(start, end)) {
            is Outcome.Ok -> outcome.value.filter { it.endTime > now }
            is Outcome.Err -> return outcome
        }

        val before = old.map { it.lecture.gridShape() }.toSet()
        val after = grid.map { it.gridShape() }.toSet()
        if (before == after) {
            Napier.d("Week +$weekOffset unchanged in the grid", tag = TAG)
            return Outcome.Ok(WeekOutcome(emptyList(), old.size))
        }

        Napier.d("Week +$weekOffset moved — fetching it in full", tag = TAG)
        return checkWeekInFull(now, start, end)
    }

    /**
     * Fills the cache for a week the app has never loaded, without saying a word about it.
     *
     * Comparing against an empty cache would report the entire week as new lectures, which is not
     * news — that is what "skip it" used to mean here. But skipping also meant the week stayed
     * invisible until the user happened to page to it, so a change in week +3 went unnoticed for
     * as long as nobody looked. Fetching it once closes that: from the next sweep on it has a
     * previous state and the ordinary grid comparison applies.
     *
     * The full fetch rather than the grid, because the grid alone would be a downgrade — it has no
     * lecturers and no detail rooms, and storing it would make the next comparison report both as
     * having appeared out of nowhere.
     *
     * A week that is genuinely empty (semester break) stays uncached and is fetched again next
     * time. That costs exactly what its grid request would have cost, since there are no lectures
     * to ask detail pages for.
     */
    private suspend fun seedFutureWeek(
        weekOffset: Int,
        start: LocalDateTime,
        end: LocalDateTime,
    ): Outcome<WeekOutcome> {
        Napier.d("Week +$weekOffset has never been loaded — fetching it in full to compare later", tag = TAG)

        val fetched = when (val outcome = dualisLectureService.getWeeklyLecturesForWeek(start, end)) {
            is Outcome.Ok -> outcome.value
            is Outcome.Err -> return outcome
        }

        return when (val saved = dualisLectureService.saveLecturesToDatabase(fetched, start, end)) {
            is Outcome.Ok -> Outcome.Ok(WeekOutcome(emptyList(), fetched.size))
            is Outcome.Err -> Outcome.Err(saved.error)
        }
    }

    // ── Matching ────────────────────────────────────────────────────────────────────────────

    /**
     * Pairs the cached lectures with the fetched ones and describes the difference.
     *
     * Pairing used to be a lookup by `subject_start_end`, which meant a lecture that moved could
     * never be *recognised* as having moved: its key changed, so the old entry found no partner
     * and the new one looked unrelated. Every shift arrived as a cancellation plus a new lecture —
     * two notifications for one event, and the most common event there is.
     *
     * Now the pairing happens per subject, in two passes:
     *
     * 1. **Exact slots first.** Anything that still sits at the same start and end is paired
     *    before anything else. This is what keeps a weekly lecture on Monday *and* Wednesday from
     *    being read as "Monday moved to Wednesday" when only Monday was cancelled.
     * 2. **Then the nearest survivors.** Whatever is left is paired closest-first by how far apart
     *    the start times are, which is what a moved lecture looks like.
     *
     * What no pass claims is a cancellation (old side) or a new lecture (new side).
     */
    private fun diff(old: List<LectureWithLecturers>, new: List<LectureEventEntity>): List<LectureChange> {
        val changes = mutableListOf<LectureChange>()
        val subjects = (old.map { it.lecture.shortSubjectName } + new.map { it.shortSubjectName }).toSet()

        for (subject in subjects) {
            val remainingOld = old.filter { it.lecture.shortSubjectName == subject }.toMutableList()
            val remainingNew = new.filter { it.shortSubjectName == subject }.toMutableList()

            // Pass 1 — same slot.
            for (candidate in remainingOld.toList()) {
                val match = remainingNew.firstOrNull {
                    it.startTime == candidate.lecture.startTime && it.endTime == candidate.lecture.endTime
                } ?: continue
                remainingOld.remove(candidate)
                remainingNew.remove(match)
                changes += detailChanges(candidate, match)
            }

            // Pass 2 — closest survivor wins.
            while (remainingOld.isNotEmpty() && remainingNew.isNotEmpty()) {
                val pair = remainingOld
                    .flatMap { o -> remainingNew.map { n -> o to n } }
                    .minBy { (o, n) -> abs(o.lecture.startTime.asMinutes() - n.startTime.asMinutes()) }
                remainingOld.remove(pair.first)
                remainingNew.remove(pair.second)

                val moved = pair.first.lecture
                val to = pair.second
                changes += LectureChange.TimeChange(
                    lectureId = moved.lectureId,
                    courseName = moved.shortSubjectName,
                    oldStartTime = moved.startTime,
                    newStartTime = to.startTime,
                    oldEndTime = moved.endTime,
                    newEndTime = to.endTime,
                )
                changes += detailChanges(pair.first, to)
            }

            remainingOld.forEach {
                changes += LectureChange.Cancellation(
                    lectureId = it.lecture.lectureId,
                    courseName = it.lecture.shortSubjectName,
                    cancelledLecture = it.lecture,
                )
            }
            remainingNew.forEach {
                changes += LectureChange.NewLecture(
                    // The row does not exist yet; the id is assigned when the week is written.
                    lectureId = 0,
                    courseName = it.shortSubjectName,
                    lecture = it,
                )
            }
        }

        return changes
    }

    /** Everything about a paired lecture other than when it happens. */
    private fun detailChanges(
        old: LectureWithLecturers,
        new: LectureEventEntity,
    ): List<LectureChange> {
        val changes = mutableListOf<LectureChange>()
        val lecture = old.lecture

        if (lecture.location != new.location) {
            changes += LectureChange.LocationChange(
                lectureId = lecture.lectureId,
                courseName = lecture.shortSubjectName,
                occursAt = new.startTime,
                oldLocation = lecture.location,
                newLocation = new.location,
            )
        }

        if (lecture.isTest != new.isTest) {
            changes += LectureChange.TypeChange(
                lectureId = lecture.lectureId,
                courseName = lecture.shortSubjectName,
                occursAt = new.startTime,
                oldIsTest = lecture.isTest,
                newIsTest = new.isTest,
            )
        }

        val oldLecturers = old.lecturers.map { it.lecturerName }.sorted()
        val newLecturers = new.lecturers.orEmpty().sorted()
        if (oldLecturers != newLecturers) {
            changes += LectureChange.LecturerChange(
                lectureId = lecture.lectureId,
                courseName = lecture.shortSubjectName,
                occursAt = new.startTime,
                oldLecturers = oldLecturers,
                newLecturers = newLecturers,
            )
        }

        return changes
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    /**
     * Monday 00:00 to Sunday 23:59 of the week [offset] weeks from the one containing [now].
     *
     * Derived from the injected clock rather than from `TimeHelper`, which reads the system clock:
     * with half the time source injected and half of it not, a test could set the hour but not the
     * week, and every comparison silently ran against an empty window.
     */
    private fun weekAround(now: LocalDateTime, offset: Int): Pair<LocalDateTime, LocalDateTime> {
        val monday = now.date.plus(-now.dayOfWeek.ordinal + offset * 7, DateTimeUnit.DAY)
        val sunday = monday.plus(6, DateTimeUnit.DAY)
        return monday.atTime(0, 0, 0) to sunday.atTime(23, 59, 59)
    }

    private suspend fun cachedLectures(
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<LectureWithLecturers> = lectureEventDao.getAllWithLecturers().filter {
        it.lecture.startTime < end && it.lecture.endTime > start
    }

    private fun List<LectureWithLecturers>.stillAhead(now: LocalDateTime) =
        filter { it.lecture.endTime > now }

    /** What the weekly grid and the cache describe identically. */
    private fun LectureEventEntity.gridShape() =
        "$shortSubjectName|$startTime|$endTime|$isTest"

    /** Minutes since the epoch, for "how far did this move". */
    private fun LocalDateTime.asMinutes(): Long =
        date.toEpochDays().toLong() * 24 * 60 + hour * 60 + minute

    private suspend fun isFutureSweepDue(now: LocalDateTime): Boolean {
        val last = syncMetadataDao.getSyncMetadata(SYNC_KEY_FUTURE_SWEEP)?.lastSyncTimestamp
            ?: return true
        val elapsedHours = (now.asMinutes() - last.asMinutes()) / 60
        return elapsedHours >= FUTURE_SWEEP_HOURS
    }

    private suspend fun recordFutureSweep(now: LocalDateTime) {
        syncMetadataDao.insert(SyncMetadataEntity(SYNC_KEY_FUTURE_SWEEP, now))
    }
}
