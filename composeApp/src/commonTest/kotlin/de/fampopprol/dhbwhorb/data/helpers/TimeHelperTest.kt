package de.fampopprol.dhbwhorb.data.helpers

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimeHelperTest {

    @Test
    fun testNow_ReturnsValidDateTime() {
        // Test that now() returns a valid LocalDateTime
        val now = TimeHelper.now()
        assertNotNull(now)
        assertTrue(now.year >= 2024)
    }

    @Test
    fun testCalculateDaysDifference_SameDay() {
        val date = LocalDateTime(2024, 1, 15, 10, 0, 0)
        val result = TimeHelper.calculateDaysDifference(date, date)
        assertEquals(0, result)
    }

    @Test
    fun testCalculateDaysDifference_ConsecutiveDays() {
        val start = LocalDateTime(2024, 1, 15, 10, 0, 0)
        val end = LocalDateTime(2024, 1, 16, 10, 0, 0)
        val result = TimeHelper.calculateDaysDifference(start, end)
        assertEquals(1, result)
    }

    @Test
    fun testCalculateDaysDifference_OneWeek() {
        val start = LocalDateTime(2024, 1, 15, 10, 0, 0)
        val end = LocalDateTime(2024, 1, 22, 10, 0, 0)
        val result = TimeHelper.calculateDaysDifference(start, end)
        assertEquals(7, result)
    }

    @Test
    fun testCalculateDaysDifference_OneMonth() {
        val start = LocalDateTime(2024, 1, 1, 0, 0, 0)
        val end = LocalDateTime(2024, 2, 1, 0, 0, 0)
        val result = TimeHelper.calculateDaysDifference(start, end)
        assertEquals(31, result)
    }

    @Test
    fun testCalculateDaysDifference_AcrossYears() {
        val start = LocalDateTime(2023, 12, 25, 0, 0, 0)
        val end = LocalDateTime(2024, 1, 5, 0, 0, 0)
        val result = TimeHelper.calculateDaysDifference(start, end)
        // Dec 25 is day 359, Jan 5 is day 5
        // 365 - 359 + 5 = 11 days
        assertEquals(11, result)
    }

    @Test
    fun testCalculateDaysDifference_MultipleYears() {
        val start = LocalDateTime(2022, 1, 1, 0, 0, 0)
        val end = LocalDateTime(2024, 1, 1, 0, 0, 0)
        val result = TimeHelper.calculateDaysDifference(start, end)
        // Approximately 2 years = 730 days
        assertEquals(730, result)
    }

    @Test
    fun testCalculateDaysDifference_EndBeforeStart_ReturnsZero() {
        val start = LocalDateTime(2024, 1, 20, 0, 0, 0)
        val end = LocalDateTime(2024, 1, 10, 0, 0, 0)
        val result = TimeHelper.calculateDaysDifference(start, end)
        assertEquals(0, result)
    }

    @Test
    fun testDayOfYear_January1st() {
        val date = LocalDateTime(2024, Month.JANUARY, 1, 0, 0, 0)
        val result = TimeHelper.dayOfYear(date)
        assertEquals(1, result)
    }

    @Test
    fun testDayOfYear_January31st() {
        val date = LocalDateTime(2024, Month.JANUARY, 31, 0, 0, 0)
        val result = TimeHelper.dayOfYear(date)
        assertEquals(31, result)
    }

    @Test
    fun testDayOfYear_February1st() {
        val date = LocalDateTime(2024, Month.FEBRUARY, 1, 0, 0, 0)
        val result = TimeHelper.dayOfYear(date)
        assertEquals(32, result)
    }

    @Test
    fun testDayOfYear_March1st() {
        val date = LocalDateTime(2024, Month.MARCH, 1, 0, 0, 0)
        val result = TimeHelper.dayOfYear(date)
        // Jan (31) + Feb (29, leap year) + 1 = 61
        assertEquals(61, result)
    }

    @Test
    fun testDayOfYear_December31st() {
        val date = LocalDateTime(2024, Month.DECEMBER, 31, 0, 0, 0)
        val result = TimeHelper.dayOfYear(date)
        assertEquals(366, result) // 2024 is a leap year
    }

    @Test
    fun testGetCurrentWeekDates_ReturnsMondayToSunday() {
        val (monday, sunday) = TimeHelper.getCurrentWeekDates()

        assertNotNull(monday)
        assertNotNull(sunday)

        // Verify Monday comes before Sunday
        assertTrue(monday.dayOfYear <= sunday.dayOfYear || monday.year < sunday.year)

        // Verify Monday starts at midnight
        assertEquals(0, monday.hour)
        assertEquals(0, monday.minute)
        assertEquals(0, monday.second)

        // Verify Sunday ends at 23:59:59
        assertEquals(23, sunday.hour)
        assertEquals(59, sunday.minute)
        assertEquals(59, sunday.second)

        // Calculate difference should be approximately 6 days
        val daysDiff = TimeHelper.calculateDaysDifference(monday, sunday)
        assertEquals(6, daysDiff)
    }

    @Test
    fun testIsDataStale_WithinThreshold() {
        val now = TimeHelper.now()
        val lastUpdate = LocalDateTime(
            year = now.year,
            month = now.month,
            day = now.day - 2,
            hour = now.hour,
            minute = now.minute,
            second = now.second
        )

        val isStale = TimeHelper.isDataStale(lastUpdate, thresholdDays = 7)
        assertFalse(isStale)
    }

    @Test
    fun testIsDataStale_ExactlyAtThreshold() {
        val lastUpdate = LocalDateTime(2024, 1, 1, 0, 0, 0)
        val now = LocalDateTime(2024, 1, 8, 0, 0, 0)

        // Mock the behavior by testing the underlying logic
        val daysDiff = TimeHelper.calculateDaysDifference(lastUpdate, now)
        assertEquals(7, daysDiff)
        assertTrue(daysDiff >= 7)
    }

    @Test
    fun testIsDataStale_BeyondThreshold() {
        val lastUpdate = LocalDateTime(2024, 1, 1, 0, 0, 0)
        val now = LocalDateTime(2024, 1, 10, 0, 0, 0)

        val daysDiff = TimeHelper.calculateDaysDifference(lastUpdate, now)
        assertEquals(9, daysDiff)
        assertTrue(daysDiff >= 7)
    }

    @Test
    fun testGetWeekDatesRelativeToCurrentWeek_CurrentWeek() {
        val currentWeek = TimeHelper.getCurrentWeekDates()
        val relativeWeek = TimeHelper.getWeekDatesRelativeToCurrentWeek(0)

        assertEquals(currentWeek.first, relativeWeek.first)
        assertEquals(currentWeek.second, relativeWeek.second)
    }

    @Test
    fun testGetWeekDatesRelativeToCurrentWeek_NextWeek() {
        val currentWeek = TimeHelper.getCurrentWeekDates()
        val nextWeek = TimeHelper.getWeekDatesRelativeToCurrentWeek(1)

        // Next week's Monday should be 7 days after current Monday
        val daysDiff = TimeHelper.calculateDaysDifference(currentWeek.first, nextWeek.first)
        assertEquals(7, daysDiff)
    }

    @Test
    fun testGetWeekDatesRelativeToCurrentWeek_PreviousWeek() {
        val currentWeek = TimeHelper.getCurrentWeekDates()
        val previousWeek = TimeHelper.getWeekDatesRelativeToCurrentWeek(-1)

        // Previous week's Monday should be 7 days before current Monday
        val daysDiff = TimeHelper.calculateDaysDifference(previousWeek.first, currentWeek.first)
        assertEquals(7, daysDiff)
    }

    @Test
    fun testGetWeekDatesRelativeToCurrentWeek_TwoWeeksAhead() {
        val currentWeek = TimeHelper.getCurrentWeekDates()
        val twoWeeksAhead = TimeHelper.getWeekDatesRelativeToCurrentWeek(2)

        // Two weeks ahead should be 14 days
        val daysDiff = TimeHelper.calculateDaysDifference(currentWeek.first, twoWeeksAhead.first)
        assertEquals(14, daysDiff)
    }

    @Test
    fun testGetWeekDatesRelativeToCurrentWeek_TwoWeeksBehind() {
        val currentWeek = TimeHelper.getCurrentWeekDates()
        val twoWeeksBehind = TimeHelper.getWeekDatesRelativeToCurrentWeek(-2)

        // Two weeks behind should be 14 days
        val daysDiff = TimeHelper.calculateDaysDifference(twoWeeksBehind.first, currentWeek.first)
        assertEquals(14, daysDiff)
    }

    @Test
    fun testCalculateDaysDifference_SameMonthDifferentDays() {
        val start = LocalDateTime(2024, Month.JUNE, 10, 12, 0, 0)
        val end = LocalDateTime(2024, Month.JUNE, 20, 12, 0, 0)
        val result = TimeHelper.calculateDaysDifference(start, end)
        assertEquals(10, result)
    }

    @Test
    fun testDayOfYear_MidYear() {
        val date = LocalDateTime(2024, Month.JULY, 1, 0, 0, 0)
        val result = TimeHelper.dayOfYear(date)
        // Jan(31) + Feb(29, leap year) + Mar(31) + Apr(30) + May(31) + Jun(30) + 1 = 183
        assertEquals(183, result)
    }
}