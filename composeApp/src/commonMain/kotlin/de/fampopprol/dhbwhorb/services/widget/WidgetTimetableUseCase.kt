// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetDayState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import io.github.aakira.napier.Napier
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * Use case that computes all widget view-states from the timetable data.
 *
 * All public methods are pure data transformations on top of
 * [WidgetLectureRepository]; no Compose or platform-specific code lives here.
 *
 * @param repository Data source – typically [de.fampopprol.dhbwhorb.services.LectureService].
 * @param clock      Returns the current [LocalDateTime]. Injected for deterministic testing.
 */
class WidgetTimetableUseCase(
    private val repository: WidgetLectureRepository,
    private val clock: () -> LocalDateTime = { TimeHelper.now() },
) {
    companion object {
        private const val TAG = "WidgetTimetableUseCase"
        /** How many days ahead the fallback scan will look before giving up. */
        private const val MAX_LOOKAHEAD_DAYS = 14
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * **Up Next** widget variant.
     *
     * Returns the currently running lecture, or – if none is running – the next
     * lecture starting later today. Once all of today's lectures are over (or
     * there are none), returns [WidgetUpNextState.NoMoreClassesToday].
     */
    suspend fun getUpNextState(): WidgetUpNextState {
        val now = clock()
        Napier.d("getUpNextState called, now=$now", tag = TAG)

        val todayClasses = fetchClassesForDate(now.date)
            .filter { it.endTime > now }
            .sortedBy { it.startTime }

        if (todayClasses.isEmpty()) {
            Napier.d("No remaining classes today", tag = TAG)
            return WidgetUpNextState.NoMoreClassesToday
        }

        val running = todayClasses.firstOrNull { it.startTime <= now }
        if (running != null) {
            Napier.d("Currently running: ${running.shortSubjectName}", tag = TAG)
            return WidgetUpNextState.CurrentlyRunning(running.toWidgetClassState(now))
        }

        val next = todayClasses.first()
        Napier.d("Next class: ${next.shortSubjectName} at ${next.startTime}", tag = TAG)
        return WidgetUpNextState.ComingUp(next.toWidgetClassState(now))
    }

    /**
     * **Day Summary** widget variant (Compact / Tall).
     *
     * Returns all classes for today. If today has no classes the result contains
     * the classes of the next calendar day that has at least one lecture (up to
     * [MAX_LOOKAHEAD_DAYS] days ahead). Returns `null` when nothing is found.
     */
    suspend fun getDaySummaryState(): WidgetDayState? {
        val now = clock()
        Napier.d("getDaySummaryState called, now=$now", tag = TAG)
        return findNextDayWithClasses(startingFrom = now.date)
    }

    /**
     * **Multi-Day Summary** widget variant (Wide / Large).
     *
     * Returns exactly **two** [WidgetDayState] entries: the first upcoming day
     * with lectures, then the next day with lectures after that. Returns a
     * single-entry list when only one day with classes is found, and an empty
     * list when none are found within the look-ahead window.
     */
    suspend fun getMultiDaySummaryState(): List<WidgetDayState> {
        val now = clock()
        Napier.d("getMultiDaySummaryState called, now=$now", tag = TAG)

        val firstDay = findNextDayWithClasses(startingFrom = now.date) ?: run {
            Napier.d("No days with classes found within look-ahead", tag = TAG)
            return emptyList()
        }

        val secondDay = findNextDayWithClasses(
            startingFrom = firstDay.date.plus(1, DateTimeUnit.DAY),
        ) ?: run {
            Napier.d("Only one day with classes found", tag = TAG)
            return listOf(firstDay)
        }

        return listOf(firstDay, secondDay)
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the [WidgetDayState] for the first day >= [startingFrom] that has
     * at least one lecture, or `null` if the look-ahead window is exhausted.
     */
    private suspend fun findNextDayWithClasses(startingFrom: LocalDate): WidgetDayState? {
        var candidate = startingFrom
        val horizon = startingFrom.plus(MAX_LOOKAHEAD_DAYS, DateTimeUnit.DAY)

        while (candidate <= horizon) {
            val classes = fetchClassesForDate(candidate)
            if (classes.isNotEmpty()) {
                Napier.d("Found ${classes.size} class(es) on $candidate", tag = TAG)
                val now = clock()
                return WidgetDayState(
                    date = candidate,
                    classes = classes
                        .sortedBy { it.startTime }
                        .map { it.toWidgetClassState(now) },
                )
            }
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }

        Napier.d("No classes found between $startingFrom and $horizon", tag = TAG)
        return null
    }

    /** Fetches all lectures for a given calendar day (00:00 – 23:59:59). */
    private suspend fun fetchClassesForDate(date: LocalDate): List<LectureEventEntity> {
        val start = LocalDateTime(date.year, date.month, date.day, 0, 0, 0)
        val end = LocalDateTime(date.year, date.month, date.day, 23, 59, 59)
        return repository.getLecturesForDateRange(start, end)
    }

    /** Maps a [LectureEventEntity] to the platform-agnostic [WidgetClassState]. */
    private fun LectureEventEntity.toWidgetClassState(now: LocalDateTime): WidgetClassState =
        WidgetClassState(
            name = fullSubjectName ?: shortSubjectName,
            shortName = shortSubjectName,
            formattedStartTime = startTime.formatHHmm(),
            formattedEndTime = endTime.formatHHmm(),
            location = location,
            isTest = isTest,
            isOngoing = startTime <= now && endTime > now,
            startTime = startTime,
            endTime = endTime,
        )

    private fun LocalDateTime.formatHHmm(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

