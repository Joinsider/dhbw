// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.repository.TimetableRepository
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
 * All public methods are pure data transformations on top of the local timetable cache; no
 * Compose and no platform-specific code lives here.
 *
 * Reads go through [TimetableRepository.getCachedLectures], which never touches the network: the
 * widget refreshes from a background worker where there may be neither a session nor connectivity.
 *
 * @param clock Returns the current [LocalDateTime]. Injected for deterministic testing.
 */
class WidgetTimetableUseCase(
    private val repository: TimetableRepository,
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
            .filter { it.end > now }
            .sortedBy { it.start }

        if (todayClasses.isEmpty()) {
            Napier.d("No remaining classes today", tag = TAG)
            return WidgetUpNextState.NoMoreClassesToday
        }

        val running = todayClasses.firstOrNull { it.start <= now }
        if (running != null) {
            Napier.d("Currently running: ${running.shortName}", tag = TAG)
            return WidgetUpNextState.CurrentlyRunning(running.toWidgetClassState(now))
        }

        val next = todayClasses.first()
        Napier.d("Next class: ${next.shortName} at ${next.start}", tag = TAG)
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
        val now = clock()

        while (candidate <= horizon) {
            val classes = fetchClassesForDate(candidate)
            if (classes.isNotEmpty()) {
                // If the candidate day is today, check if all classes have ended.
                // If so, skip today and look for the next day with lectures.
                if (candidate == now.date) {
                    val allClassesEnded = classes.all { it.end <= now }
                    if (allClassesEnded) {
                        Napier.d("All classes for today ($candidate) are over, skipping to next day", tag = TAG)
                        candidate = candidate.plus(1, DateTimeUnit.DAY)
                        continue
                    }
                }

                Napier.d("Found ${classes.size} class(es) on $candidate", tag = TAG)
                return WidgetDayState(
                    date = candidate,
                    classes = classes
                        .sortedBy { it.start }
                        .map { it.toWidgetClassState(now) },
                )
            }
            candidate = candidate.plus(1, DateTimeUnit.DAY)
        }

        Napier.d("No classes found between $startingFrom and $horizon", tag = TAG)
        return null
    }

    /**
     * All lectures on [date] (00:00 – 23:59:59).
     *
     * A cache that cannot be read yields an empty day: the widget has no way to render an error,
     * and a stale-looking empty slot is better than a crashed widget. It is logged so the cause
     * is not lost.
     */
    private suspend fun fetchClassesForDate(date: LocalDate): List<Lecture> {
        val start = LocalDateTime(date.year, date.month, date.day, 0, 0, 0)
        val end = LocalDateTime(date.year, date.month, date.day, 23, 59, 59)

        return when (val outcome = repository.getCachedLectures(start, end)) {
            is Outcome.Ok -> outcome.value.distinctBy { Triple(it.start, it.end, it.shortName) }
            is Outcome.Err -> {
                Napier.w("Widget could not read the timetable cache: ${outcome.error}", tag = TAG)
                emptyList()
            }
        }
    }

    /** Maps a [Lecture] to the platform-agnostic [WidgetClassState]. */
    private fun Lecture.toWidgetClassState(now: LocalDateTime): WidgetClassState =
        WidgetClassState(
            name = displayName,
            shortName = shortName,
            formattedStartTime = start.formatHHmm(),
            formattedEndTime = end.formatHHmm(),
            location = location,
            isTest = isTest,
            isOngoing = start <= now && end > now,
            startTime = start,
            endTime = end,
        )

    private fun LocalDateTime.formatHHmm(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
