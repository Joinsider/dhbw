package de.joinside.dhbw.data.helpers

import kotlinx.datetime.*
import kotlin.time.ExperimentalTime

/**
 * Singleton helper object for time-related operations.
 * Provides utility functions for date calculations and comparisons.
 */
object TimeHelper {

    /**
     * Get the current LocalDateTime in the system's default timezone.
     *
     * @return LocalDateTime - Current date and time
     */
    @OptIn(ExperimentalTime::class)
    fun now(): LocalDateTime {
        return kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }

    /**
     * Calculate the difference in days between two LocalDateTime instances.
     *
     * @param start LocalDateTime - The earlier date
     * @param end LocalDateTime - The later date
     * @return Int - The number of days between the two dates (returns 0 if end is before start)
     */
    fun calculateDaysDifference(start: LocalDateTime, end: LocalDateTime): Int {
        // Simple day calculation based on date components
        val startDayOfYear = dayOfYear(start)
        val endDayOfYear = dayOfYear(end)
        val yearDiff = end.year - start.year

        val diff = when {
            yearDiff == 0 -> endDayOfYear - startDayOfYear
            yearDiff > 0 -> {
                // Calculate days remaining in start year
                val isLeapYearStart =
                    (start.year % 4 == 0 && start.year % 100 != 0) || (start.year % 400 == 0)
                val daysInStartYear = if (isLeapYearStart) 366 else 365
                val daysRemainingInStartYear = daysInStartYear - startDayOfYear

                // Add full years in between
                var daysInBetween = 0
                for (year in (start.year + 1) until end.year) {
                    val isLeapYear = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
                    daysInBetween += if (isLeapYear) 366 else 365
                }

                // Add days in end year
                daysRemainingInStartYear + daysInBetween + endDayOfYear
            }

            else -> endDayOfYear - startDayOfYear // negative, will be caught below
        }

        return if (diff < 0) 0 else diff
    }

    /**
     * Calculate approximate day of year for a given LocalDateTime.
     *
     * @param date LocalDateTime - The date to calculate day of year for
     * @return Int - Approximate day of year (1-365/366)
     */
    fun dayOfYear(date: LocalDateTime): Int {
        val isLeapYear = (date.year % 4 == 0 && date.year % 100 != 0) || (date.year % 400 == 0)
        val daysInMonths = if (isLeapYear) {
            intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335)
        } else {
            intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        }
        return daysInMonths[date.month.ordinal] + date.day
    }

    /**
     * Get current week start and end dates.
     * Returns a pair representing the start (Monday) and end (Sunday) dates of the current week.
     *
     * @return Pair<LocalDateTime, LocalDateTime> - Monday and Sunday of current week
     */
    fun getCurrentWeekDates(): Pair<LocalDateTime, LocalDateTime> {
        val now = now()
        val currentDate = now.date

        // Calculate Monday of the current week (ISO 8601: Monday is first day)
        val daysFromMonday = now.dayOfWeek.ordinal
        val mondayDate = currentDate.plus(-daysFromMonday, DateTimeUnit.DAY)
        val sundayDate = mondayDate.plus(6, DateTimeUnit.DAY)

        val monday = LocalDateTime(
            year = mondayDate.year,
            month = mondayDate.month,
            day = mondayDate.day,
            hour = 0,
            minute = 0,
            second = 0
        )

        val sunday = LocalDateTime(
            year = sundayDate.year,
            month = sundayDate.month,
            day = sundayDate.day,
            hour = 23,
            minute = 59,
            second = 59
        )

        return Pair(monday, sunday)
    }

    /**
     * Check if data is stale based on the number of days since last update.
     *
     * @param lastUpdate LocalDateTime - The last update timestamp
     * @param thresholdDays Int - Number of days after which data is considered stale
     * @return Boolean - True if data is stale, false otherwise
     */
    fun isDataStale(lastUpdate: LocalDateTime, thresholdDays: Int): Boolean {
        val now = now()
        val daysDifference = calculateDaysDifference(lastUpdate, now)
        return daysDifference >= thresholdDays
    }

    /**
     * Get dates for a specific week relative to the current week.
     * So -1 is the previous week, 0 is the current week, and 1 is the next week.
     * @param relativeWeek Int - Number of weeks relative to the current week
     * @return Pair<LocalDateTime, LocalDateTime> - Monday and Sunday of the specified week
     */
    fun getWeekDatesRelativeToCurrentWeek(relativeWeek: Int): Pair<LocalDateTime, LocalDateTime> {
        val currentWeekDates = getCurrentWeekDates()
        val monday = currentWeekDates.first
        val sunday = currentWeekDates.second

        // Calculate offset in days (7 days per week)
        val dayOffset = relativeWeek * 7

        val adjustedMondayDate = monday.date.plus(dayOffset, DateTimeUnit.DAY)
        val adjustedSundayDate = sunday.date.plus(dayOffset, DateTimeUnit.DAY)

        val adjustedMonday = LocalDateTime(
            year = adjustedMondayDate.year,
            month = adjustedMondayDate.month,
            day = adjustedMondayDate.day,
            hour = monday.hour,
            minute = monday.minute,
            second = monday.second
        )

        val adjustedSunday = LocalDateTime(
            year = adjustedSundayDate.year,
            month = adjustedSundayDate.month,
            day = adjustedSundayDate.day,
            hour = sunday.hour,
            minute = sunday.minute,
            second = sunday.second
        )

        return Pair(adjustedMonday, adjustedSunday)
    }
}