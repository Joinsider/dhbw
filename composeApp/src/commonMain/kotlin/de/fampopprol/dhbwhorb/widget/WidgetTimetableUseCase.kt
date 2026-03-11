// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget

import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.services.LectureService
import de.fampopprol.dhbwhorb.widget.models.WidgetClassState
import de.fampopprol.dhbwhorb.widget.models.WidgetDaySummaryState
import de.fampopprol.dhbwhorb.widget.models.WidgetMultiDaySummaryState
import de.fampopprol.dhbwhorb.widget.models.WidgetUpNextState
import io.github.aakira.napier.Napier
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * Use case that computes all widget view states from the timetable data.
 *
 * This class provides the shared business logic for home screen timetable widgets.
 * It is designed for **mobile platforms only** (Android and iOS). Desktop is out of scope.
 *
 * All time-filtering logic is encapsulated in the companion object as pure functions so
 * that they can be unit-tested independently of any service or database layer.
 *
 * Business rules:
 * - **Up Next**: The currently running class, or the next upcoming class, from [now] onwards.
 * - **Day Summary**: Classes for [today]. Falls back to the next day with lectures if today is empty.
 * - **Multi-Day Summary**: Classes for today + tomorrow. If either day is empty, the use case
 *   falls back to the next two days (from today) that each have at least one lecture.
 */
class WidgetTimetableUseCase(
    private val lectureService: LectureService
) {
    companion object {
        private const val TAG = "WidgetTimetableUseCase"

        /**
         * Maximum number of days to look ahead when searching for lectures.
         * This limits the data fetch window and the fallback search range.
         */
        private const val MAX_LOOK_AHEAD_DAYS = 14

        // ─── Pure processing functions (testable without a service) ─────────────

        /**
         * Resolve the Up Next state from a pre-fetched list of lectures.
         *
         * Returns the currently ongoing class (if any), otherwise the earliest upcoming
         * class whose end time is after [now]. If no such class exists, returns
         * [WidgetUpNextState.NoUpcomingClass].
         *
         * @param lectures All lectures available for the look-ahead window
         * @param now The current instant used as the reference point
         */
        fun resolveUpNext(
            lectures: List<LectureEventEntity>,
            now: LocalDateTime
        ): WidgetUpNextState {
            // Only consider lectures that have not yet ended
            val upcoming = lectures
                .filter { it.endTime > now }
                .sortedBy { it.startTime }
                .firstOrNull()

            return if (upcoming != null) {
                WidgetUpNextState.HasClass(toWidgetClassState(upcoming, now))
            } else {
                WidgetUpNextState.NoUpcomingClass
            }
        }

        /**
         * Resolve the Day Summary state from a pre-fetched list of lectures.
         *
         * Returns the classes for [today]. If today has no classes, it finds the next
         * available day (up to [MAX_LOOK_AHEAD_DAYS] days ahead) that has at least one lecture.
         *
         * @param lectures All lectures available for the look-ahead window
         * @param today The reference date (normally today's date)
         * @param now The current instant used to compute [WidgetClassState.isOngoing]
         * @return A [WidgetDaySummaryState] for today or the next available day,
         *         or `null` if no lectures are found within the look-ahead window
         */
        fun resolveDaySummary(
            lectures: List<LectureEventEntity>,
            today: LocalDate,
            now: LocalDateTime
        ): WidgetDaySummaryState? {
            val todayClasses = getClassesForDate(lectures, today)
            if (todayClasses.isNotEmpty()) {
                return WidgetDaySummaryState(
                    date = today,
                    classes = todayClasses.map { toWidgetClassState(it, now) },
                    isToday = true
                )
            }

            // Fallback: find next available day (starting from tomorrow, since today was
            // already confirmed empty by the check above)
            val nextDay = findNextDateWithLectures(lectures, today.plus(1, DateTimeUnit.DAY))
            if (nextDay == null) {
                Napier.d("No lectures found within look-ahead window for Day Summary", tag = TAG)
                return null
            }

            val nextDayClasses = getClassesForDate(lectures, nextDay)
            return WidgetDaySummaryState(
                date = nextDay,
                classes = nextDayClasses.map { toWidgetClassState(it, now) },
                isToday = false
            )
        }

        /**
         * Resolve the Multi-Day Summary state from a pre-fetched list of lectures.
         *
         * Returns classes for today and tomorrow. If either day has no classes, both
         * days are replaced by the next two available days (from [today] onwards) that
         * each have at least one lecture.
         *
         * @param lectures All lectures available for the look-ahead window
         * @param today The reference date (normally today's date)
         * @param now The current instant used to compute [WidgetClassState.isOngoing]
         * @return A [WidgetMultiDaySummaryState] with up to 2 day summaries
         */
        fun resolveMultiDaySummary(
            lectures: List<LectureEventEntity>,
            today: LocalDate,
            now: LocalDateTime
        ): WidgetMultiDaySummaryState {
            val tomorrow = today.plus(1, DateTimeUnit.DAY)
            val todayClasses = getClassesForDate(lectures, today)
            val tomorrowClasses = getClassesForDate(lectures, tomorrow)

            return if (todayClasses.isNotEmpty() && tomorrowClasses.isNotEmpty()) {
                // Normal case: both days have lectures
                WidgetMultiDaySummaryState(
                    days = listOf(
                        WidgetDaySummaryState(
                            date = today,
                            classes = todayClasses.map { toWidgetClassState(it, now) },
                            isToday = true
                        ),
                        WidgetDaySummaryState(
                            date = tomorrow,
                            classes = tomorrowClasses.map { toWidgetClassState(it, now) },
                            isToday = false
                        )
                    )
                )
            } else {
                // Fallback: find the next two days (from today) that each have lectures
                val fallbackDays = findNextTwoDatesWithLectures(lectures, today)
                WidgetMultiDaySummaryState(
                    days = fallbackDays.mapIndexed { index, date ->
                        val classes = getClassesForDate(lectures, date)
                        WidgetDaySummaryState(
                            date = date,
                            classes = classes.map { toWidgetClassState(it, now) },
                            isToday = index == 0 && date == today
                        )
                    }
                )
            }
        }

        /**
         * Return all lectures that fall on [date] (by start time date component).
         * Results are sorted by start time.
         */
        fun getClassesForDate(
            lectures: List<LectureEventEntity>,
            date: LocalDate
        ): List<LectureEventEntity> {
            return lectures
                .filter { it.startTime.date == date }
                .sortedBy { it.startTime }
        }

        /**
         * Find the first date at or after [from] that has at least one lecture,
         * searching up to [MAX_LOOK_AHEAD_DAYS] days from [from].
         *
         * @return The found date, or `null` if none found within the search window
         */
        fun findNextDateWithLectures(
            lectures: List<LectureEventEntity>,
            from: LocalDate
        ): LocalDate? {
            for (offset in 0 until MAX_LOOK_AHEAD_DAYS) {
                val candidate = from.plus(offset, DateTimeUnit.DAY)
                if (getClassesForDate(lectures, candidate).isNotEmpty()) {
                    return candidate
                }
            }
            return null
        }

        /**
         * Find the next two distinct dates starting from [from] that each have at
         * least one lecture, searching up to [MAX_LOOK_AHEAD_DAYS] days.
         *
         * @return A list of up to 2 dates. May contain fewer if insufficient lecture
         *         days are found in the look-ahead window.
         */
        fun findNextTwoDatesWithLectures(
            lectures: List<LectureEventEntity>,
            from: LocalDate
        ): List<LocalDate> {
            val result = mutableListOf<LocalDate>()
            var searchFrom = from

            repeat(2) {
                val next = findNextDateWithLectures(lectures, searchFrom)
                if (next != null) {
                    result.add(next)
                    searchFrom = next.plus(1, DateTimeUnit.DAY)
                }
            }

            return result
        }

        /**
         * Convert a [LectureEventEntity] to a [WidgetClassState].
         *
         * Uses the full subject name when available, falling back to the short name.
         * Determines [WidgetClassState.isOngoing] based on [now].
         */
        fun toWidgetClassState(
            entity: LectureEventEntity,
            now: LocalDateTime
        ): WidgetClassState {
            val isOngoing = entity.startTime <= now && now < entity.endTime
            return WidgetClassState(
                name = entity.fullSubjectName ?: entity.shortSubjectName,
                shortName = entity.shortSubjectName,
                startTime = formatTime(entity.startTime),
                endTime = formatTime(entity.endTime),
                room = entity.location,
                isOngoing = isOngoing
            )
        }

        /**
         * Format a [LocalDateTime] as a zero-padded "HH:MM" string.
         */
        fun formatTime(dateTime: LocalDateTime): String {
            val hour = dateTime.hour.toString().padStart(2, '0')
            val minute = dateTime.minute.toString().padStart(2, '0')
            return "$hour:$minute"
        }
    }

    // ─── Public API methods ───────────────────────────────────────────────────

    /**
     * Get the Up Next widget state.
     *
     * Fetches lectures for the next [MAX_LOOK_AHEAD_DAYS] days starting from [now]
     * and returns the currently running or next upcoming class.
     *
     * @param now Current date-time (defaults to the system clock). Override in tests.
     */
    suspend fun getUpNext(now: LocalDateTime = TimeHelper.now()): WidgetUpNextState {
        Napier.d("Getting UpNext widget state", tag = TAG)
        val lectures = fetchLecturesFromNow(now)
        return resolveUpNext(lectures, now)
    }

    /**
     * Get the Day Summary widget state.
     *
     * Fetches lectures for [today] and, if today is empty, falls back to the next
     * available day with lectures.
     *
     * @param today Reference date (defaults to today). Override in tests.
     * @param now Current date-time used for [WidgetClassState.isOngoing] (defaults to system clock).
     * @return The day summary state, or `null` if no lectures are found in the look-ahead window.
     */
    suspend fun getDaySummary(
        today: LocalDate = TimeHelper.now().date,
        now: LocalDateTime = TimeHelper.now()
    ): WidgetDaySummaryState? {
        Napier.d("Getting Day Summary widget state", tag = TAG)
        val startDate = LocalDateTime(today.year, today.month, today.day, 0, 0, 0)
        val lectures = fetchLecturesForWindow(startDate)
        return resolveDaySummary(lectures, today, now)
    }

    /**
     * Get the Multi-Day Summary widget state.
     *
     * Fetches lectures for today + tomorrow and, if either day is empty, falls back
     * to the next two available days with lectures.
     *
     * @param today Reference date (defaults to today). Override in tests.
     * @param now Current date-time used for [WidgetClassState.isOngoing] (defaults to system clock).
     */
    suspend fun getMultiDaySummary(
        today: LocalDate = TimeHelper.now().date,
        now: LocalDateTime = TimeHelper.now()
    ): WidgetMultiDaySummaryState {
        Napier.d("Getting Multi-Day Summary widget state", tag = TAG)
        val startDate = LocalDateTime(today.year, today.month, today.day, 0, 0, 0)
        val lectures = fetchLecturesForWindow(startDate)
        return resolveMultiDaySummary(lectures, today, now)
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Fetch all lectures for a window of [MAX_LOOK_AHEAD_DAYS] days starting from [now].
     */
    private suspend fun fetchLecturesFromNow(now: LocalDateTime): List<LectureEventEntity> {
        val endDate = now.date.plus(MAX_LOOK_AHEAD_DAYS, DateTimeUnit.DAY)
        val endDateTime = LocalDateTime(endDate.year, endDate.month, endDate.day, 23, 59, 59)
        return try {
            lectureService.getLecturesForDateRange(now, endDateTime)
        } catch (e: Exception) {
            Napier.e("Failed to fetch lectures for widget", e, tag = TAG)
            emptyList()
        }
    }

    /**
     * Fetch all lectures for a window of [MAX_LOOK_AHEAD_DAYS] days starting from [startDate].
     */
    private suspend fun fetchLecturesForWindow(startDate: LocalDateTime): List<LectureEventEntity> {
        val endDate = startDate.date.plus(MAX_LOOK_AHEAD_DAYS, DateTimeUnit.DAY)
        val endDateTime = LocalDateTime(endDate.year, endDate.month, endDate.day, 23, 59, 59)
        return try {
            lectureService.getLecturesForDateRange(startDate, endDateTime)
        } catch (e: Exception) {
            Napier.e("Failed to fetch lectures for widget", e, tag = TAG)
            emptyList()
        }
    }
}
