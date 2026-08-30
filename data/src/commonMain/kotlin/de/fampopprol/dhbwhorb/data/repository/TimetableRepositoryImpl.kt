/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.dao.SyncMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.SyncMetadataEntity
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.domain.repository.TimetableRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime

/**
 * The timetable, cache first.
 *
 * Replaces `LectureService`, which mixed three jobs: the cache strategy, the widget's read-only
 * database access, and a background refresh nobody could wait for. The widget access is now
 * [getCachedLectures] and the background refresh is a [Deferred] per week that [awaitFullWeek]
 * can join — before, the screen kicked off a background fetch and then immediately started a
 * second, identical one because the cache was still empty.
 */
class TimetableRepositoryImpl(
    private val lectureService: DualisLectureService,
    private val lectureEventDao: LectureEventDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val scope: CoroutineScope
) : TimetableRepository {

    private companion object {
        const val TAG = "TimetableRepositoryImpl"
        const val SYNC_KEY_TIMETABLE = "timetable"

        /** Cached lectures older than this are refreshed, but shown in the meantime. */
        const val SYNC_THRESHOLD_DAYS = 3
    }

    private val refreshMutex = Mutex()

    /** One in-flight full fetch per week offset, so parallel callers share it. */
    private val refreshes = mutableMapOf<Int, Deferred<Outcome<TimetableWeek>>>()

    override suspend fun getWeek(weekOffset: Int): Outcome<TimetableWeek> {
        val (start, end) = TimeHelper.getWeekDatesRelativeToCurrentWeek(weekOffset)

        val cached = when (val result = readCachedLectures(start, end)) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> return result
        }

        if (cached.isNotEmpty()) {
            if (isStale()) {
                Napier.d("Week $weekOffset is stale, refreshing in the background", tag = TAG)
                startRefresh(weekOffset)
            }
            return Outcome.Ok(
                TimetableWeek(weekOffset, start, end, cached, isPartial = false, fromCache = true)
            )
        }

        // Nothing cached: one request for the grid so the screen fills immediately, and the full
        // week — a request per lecture — started in the background for [awaitFullWeek] to join.
        val skeleton = lectureService.getWeeklySkeletonForWeek(start, end)
        startRefresh(weekOffset)

        return when (skeleton) {
            is Outcome.Ok -> Outcome.Ok(
                TimetableWeek(
                    weekOffset = weekOffset,
                    start = start,
                    end = end,
                    lectures = skeleton.value.map { it.toDomain() },
                    isPartial = true,
                    fromCache = false
                )
            )
            is Outcome.Err -> skeleton
        }
    }

    override suspend fun awaitFullWeek(weekOffset: Int): Outcome<TimetableWeek> =
        startRefresh(weekOffset).await()

    override suspend fun refreshWeek(weekOffset: Int): Outcome<TimetableWeek> =
        startRefresh(weekOffset).await()

    override suspend fun getCachedLectures(
        start: LocalDateTime,
        end: LocalDateTime
    ): Outcome<List<Lecture>> = readCachedLectures(start, end)

    /**
     * The running full fetch for [weekOffset], starting one if there is none.
     *
     * A pull-to-refresh during a background refresh joins it rather than sending the same dozen
     * requests a second time.
     */
    private suspend fun startRefresh(weekOffset: Int): Deferred<Outcome<TimetableWeek>> =
        refreshMutex.withLock {
            refreshes[weekOffset]?.takeIf { it.isActive }?.let { return@withLock it }

            val job = scope.async { fetchAndStore(weekOffset) }
            refreshes[weekOffset] = job
            scope.launch {
                job.join()
                refreshMutex.withLock { if (refreshes[weekOffset] === job) refreshes -= weekOffset }
            }
            job
        }

    private suspend fun fetchAndStore(weekOffset: Int): Outcome<TimetableWeek> {
        val (start, end) = TimeHelper.getWeekDatesRelativeToCurrentWeek(weekOffset)
        Napier.d("Fetching week $weekOffset ($start..$end) from Dualis", tag = TAG)

        val fetched = when (val result = lectureService.getWeeklyLecturesForWeek(start, end)) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> return result
        }

        if (fetched.isEmpty()) {
            // A week with no lectures is a normal week, not a failure — semester breaks exist.
            // The cache is still cleared so a cancelled week does not keep showing its lectures.
            Napier.d("Dualis reports no lectures for week $weekOffset", tag = TAG)
        }

        val saved = when (val result = lectureService.saveLecturesToDatabase(fetched, start, end)) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> return result
        }

        markSynced()

        return Outcome.Ok(
            TimetableWeek(
                weekOffset = weekOffset,
                start = start,
                end = end,
                lectures = saved.map { it.toDomain() },
                isPartial = false,
                fromCache = false
            )
        )
    }

    private suspend fun readCachedLectures(
        start: LocalDateTime,
        end: LocalDateTime
    ): Outcome<List<Lecture>> {
        return try {
            val lectures = lectureEventDao.getAllWithLecturers()
                .filter { it.lecture.startTime >= start && it.lecture.endTime <= end }
                .map { it.toDomain() }
            Outcome.Ok(lectures)
        } catch (e: Exception) {
            Napier.e("Could not read the timetable cache: ${e.message}", e, tag = TAG)
            Outcome.Err(AppError.Storage("reading the timetable cache: ${e.message}"))
        }
    }

    private suspend fun isStale(): Boolean {
        return try {
            val metadata = syncMetadataDao.getSyncMetadata(SYNC_KEY_TIMETABLE) ?: return false
            TimeHelper.isDataStale(metadata.lastSyncTimestamp, SYNC_THRESHOLD_DAYS)
        } catch (e: Exception) {
            // Not knowing when we last synced is not a reason to refuse the cached week.
            Napier.w("Could not read the sync timestamp: ${e.message}", tag = TAG)
            false
        }
    }

    private suspend fun markSynced() {
        try {
            syncMetadataDao.insert(
                SyncMetadataEntity(key = SYNC_KEY_TIMETABLE, lastSyncTimestamp = TimeHelper.now())
            )
        } catch (e: Exception) {
            // Losing the timestamp only costs an extra refresh next time.
            Napier.w("Could not update the sync timestamp: ${e.message}", tag = TAG)
        }
    }
}
