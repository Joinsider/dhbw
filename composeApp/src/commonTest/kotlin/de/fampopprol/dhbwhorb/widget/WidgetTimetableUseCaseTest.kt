// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget

import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.widget.WidgetTimetableUseCase.Companion.findNextDateWithLectures
import de.fampopprol.dhbwhorb.widget.WidgetTimetableUseCase.Companion.findNextTwoDatesWithLectures
import de.fampopprol.dhbwhorb.widget.WidgetTimetableUseCase.Companion.formatTime
import de.fampopprol.dhbwhorb.widget.WidgetTimetableUseCase.Companion.getClassesForDate
import de.fampopprol.dhbwhorb.widget.WidgetTimetableUseCase.Companion.resolveDaySummary
import de.fampopprol.dhbwhorb.widget.WidgetTimetableUseCase.Companion.resolveMultiDaySummary
import de.fampopprol.dhbwhorb.widget.WidgetTimetableUseCase.Companion.resolveUpNext
import de.fampopprol.dhbwhorb.widget.WidgetTimetableUseCase.Companion.toWidgetClassState
import de.fampopprol.dhbwhorb.widget.models.WidgetUpNextState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for [WidgetTimetableUseCase] pure processing logic.
 *
 * All tests operate directly on the companion-object functions so no service or
 * database infrastructure is needed.
 */
class WidgetTimetableUseCaseTest {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Build a [LectureEventEntity] with minimal required fields for testing.
     * The [lectureId] defaults to 0 (auto-generate not needed for in-memory testing).
     */
    private fun makeLecture(
        id: Long = 0L,
        shortName: String = "TEST",
        fullName: String? = null,
        startYear: Int = 2024,
        startMonth: Int = 6,
        startDay: Int = 10,
        startHour: Int = 8,
        startMinute: Int = 0,
        endHour: Int = 9,
        endMinute: Int = 30,
        location: String = "Room 101",
        isTest: Boolean = false
    ): LectureEventEntity {
        return LectureEventEntity(
            lectureId = id,
            shortSubjectName = shortName,
            fullSubjectName = fullName,
            startTime = LocalDateTime(startYear, startMonth, startDay, startHour, startMinute, 0),
            endTime = LocalDateTime(startYear, startMonth, startDay, endHour, endMinute, 0),
            location = location,
            isTest = isTest
        )
    }

    // ─── formatTime ──────────────────────────────────────────────────────────

    @Test
    fun formatTime_zeroPadsHourAndMinute() {
        val dt = LocalDateTime(2024, 6, 10, 8, 5, 0)
        assertEquals("08:05", formatTime(dt))
    }

    @Test
    fun formatTime_fullHourAndMinute() {
        val dt = LocalDateTime(2024, 6, 10, 14, 30, 0)
        assertEquals("14:30", formatTime(dt))
    }

    @Test
    fun formatTime_midnight() {
        val dt = LocalDateTime(2024, 6, 10, 0, 0, 0)
        assertEquals("00:00", formatTime(dt))
    }

    @Test
    fun formatTime_endOfDay() {
        val dt = LocalDateTime(2024, 6, 10, 23, 59, 0)
        assertEquals("23:59", formatTime(dt))
    }

    // ─── toWidgetClassState ───────────────────────────────────────────────────

    @Test
    fun toWidgetClassState_usesFullNameWhenAvailable() {
        val lecture = makeLecture(shortName = "SW", fullName = "Software Engineering")
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val state = toWidgetClassState(lecture, now)
        assertEquals("Software Engineering", state.name)
        assertEquals("SW", state.shortName)
    }

    @Test
    fun toWidgetClassState_fallsBackToShortNameWhenFullNameNull() {
        val lecture = makeLecture(shortName = "SW", fullName = null)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val state = toWidgetClassState(lecture, now)
        assertEquals("SW", state.name)
        assertEquals("SW", state.shortName)
    }

    @Test
    fun toWidgetClassState_formatsTimesCorrectly() {
        val lecture = makeLecture(startHour = 9, startMinute = 0, endHour = 10, endMinute = 45)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val state = toWidgetClassState(lecture, now)
        assertEquals("09:00", state.startTime)
        assertEquals("10:45", state.endTime)
    }

    @Test
    fun toWidgetClassState_isOngoing_whenNowIsDuringClass() {
        val lecture = makeLecture(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30)
        val now = LocalDateTime(2024, 6, 10, 8, 45, 0)
        val state = toWidgetClassState(lecture, now)
        assertTrue(state.isOngoing)
    }

    @Test
    fun toWidgetClassState_isOngoing_whenNowIsExactlyAtStart() {
        val lecture = makeLecture(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30)
        val now = LocalDateTime(2024, 6, 10, 8, 0, 0)
        val state = toWidgetClassState(lecture, now)
        assertTrue(state.isOngoing)
    }

    @Test
    fun toWidgetClassState_notOngoing_whenNowIsBeforeStart() {
        val lecture = makeLecture(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30)
        val now = LocalDateTime(2024, 6, 10, 7, 59, 59)
        val state = toWidgetClassState(lecture, now)
        assertFalse(state.isOngoing)
    }

    @Test
    fun toWidgetClassState_notOngoing_whenNowIsExactlyAtEnd() {
        val lecture = makeLecture(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30)
        val now = LocalDateTime(2024, 6, 10, 9, 30, 0)
        val state = toWidgetClassState(lecture, now)
        assertFalse(state.isOngoing)
    }

    @Test
    fun toWidgetClassState_notOngoing_whenNowIsAfterEnd() {
        val lecture = makeLecture(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30)
        val now = LocalDateTime(2024, 6, 10, 10, 0, 0)
        val state = toWidgetClassState(lecture, now)
        assertFalse(state.isOngoing)
    }

    @Test
    fun toWidgetClassState_setsRoomCorrectly() {
        val lecture = makeLecture(location = "B204")
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val state = toWidgetClassState(lecture, now)
        assertEquals("B204", state.room)
    }

    // ─── getClassesForDate ────────────────────────────────────────────────────

    @Test
    fun getClassesForDate_returnsOnlyMatchingDay() {
        val target = LocalDate(2024, 6, 10)
        val other = LocalDate(2024, 6, 11)
        val lectures = listOf(
            makeLecture(startDay = 10),
            makeLecture(startDay = 11),
            makeLecture(startDay = 10)
        )
        val result = getClassesForDate(lectures, target)
        assertEquals(2, result.size)
        assertTrue(result.all { it.startTime.date == target })
    }

    @Test
    fun getClassesForDate_returnsEmptyWhenNoMatch() {
        val target = LocalDate(2024, 6, 15)
        val lectures = listOf(
            makeLecture(startDay = 10),
            makeLecture(startDay = 11)
        )
        val result = getClassesForDate(lectures, target)
        assertTrue(result.isEmpty())
    }

    @Test
    fun getClassesForDate_sortsByStartTime() {
        val target = LocalDate(2024, 6, 10)
        val lectures = listOf(
            makeLecture(startDay = 10, startHour = 14),
            makeLecture(startDay = 10, startHour = 8),
            makeLecture(startDay = 10, startHour = 11)
        )
        val result = getClassesForDate(lectures, target)
        assertEquals(3, result.size)
        assertEquals(8, result[0].startTime.hour)
        assertEquals(11, result[1].startTime.hour)
        assertEquals(14, result[2].startTime.hour)
    }

    @Test
    fun getClassesForDate_returnsEmptyOnEmptyInput() {
        val result = getClassesForDate(emptyList(), LocalDate(2024, 6, 10))
        assertTrue(result.isEmpty())
    }

    // ─── resolveUpNext ────────────────────────────────────────────────────────

    @Test
    fun resolveUpNext_returnsOngoingClass() {
        val now = LocalDateTime(2024, 6, 10, 8, 30, 0)
        val lectures = listOf(makeLecture(startHour = 8, endHour = 9))
        val result = resolveUpNext(lectures, now)
        assertTrue(result is WidgetUpNextState.HasClass)
        assertTrue((result as WidgetUpNextState.HasClass).nextClass.isOngoing)
    }

    @Test
    fun resolveUpNext_returnsNextUpcomingClass_whenNoneOngoing() {
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val lectures = listOf(
            makeLecture(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30),
            makeLecture(startHour = 11, startMinute = 0, endHour = 12, endMinute = 30)
        )
        val result = resolveUpNext(lectures, now)
        assertTrue(result is WidgetUpNextState.HasClass)
        assertEquals("08:00", (result as WidgetUpNextState.HasClass).nextClass.startTime)
    }

    @Test
    fun resolveUpNext_returnsEarliestUpcomingClass() {
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val lectures = listOf(
            makeLecture(startHour = 11, shortName = "LATER"),
            makeLecture(startHour = 8, shortName = "FIRST")
        )
        val result = resolveUpNext(lectures, now)
        assertTrue(result is WidgetUpNextState.HasClass)
        assertEquals("FIRST", (result as WidgetUpNextState.HasClass).nextClass.shortName)
    }

    @Test
    fun resolveUpNext_returnsNoUpcomingClass_whenAllEnded() {
        val now = LocalDateTime(2024, 6, 10, 18, 0, 0)
        val lectures = listOf(
            makeLecture(startHour = 8, endHour = 9),
            makeLecture(startHour = 11, endHour = 12)
        )
        val result = resolveUpNext(lectures, now)
        assertTrue(result is WidgetUpNextState.NoUpcomingClass)
    }

    @Test
    fun resolveUpNext_returnsNoUpcomingClass_whenEmpty() {
        val now = LocalDateTime(2024, 6, 10, 8, 0, 0)
        val result = resolveUpNext(emptyList(), now)
        assertTrue(result is WidgetUpNextState.NoUpcomingClass)
    }

    @Test
    fun resolveUpNext_excludesClassEndingExactlyNow() {
        // A class ending exactly at "now" should not be returned (endTime > now is false)
        val now = LocalDateTime(2024, 6, 10, 9, 30, 0)
        val lectures = listOf(makeLecture(startHour = 8, endHour = 9, endMinute = 30))
        val result = resolveUpNext(lectures, now)
        assertTrue(result is WidgetUpNextState.NoUpcomingClass)
    }

    @Test
    fun resolveUpNext_handlesMultipleDays() {
        // All today's classes have ended; tomorrow's class should be returned
        val now = LocalDateTime(2024, 6, 10, 22, 0, 0)
        val lectures = listOf(
            makeLecture(startDay = 10, startHour = 8, endHour = 9),
            makeLecture(startDay = 11, startHour = 8, endHour = 9, shortName = "TOMORROW")
        )
        val result = resolveUpNext(lectures, now)
        assertTrue(result is WidgetUpNextState.HasClass)
        assertEquals("TOMORROW", (result as WidgetUpNextState.HasClass).nextClass.shortName)
    }

    // ─── findNextDateWithLectures ─────────────────────────────────────────────

    @Test
    fun findNextDateWithLectures_findsFirstDayWithLectures() {
        val from = LocalDate(2024, 6, 10)
        val lectures = listOf(makeLecture(startDay = 12))
        val result = findNextDateWithLectures(lectures, from)
        assertEquals(LocalDate(2024, 6, 12), result)
    }

    @Test
    fun findNextDateWithLectures_returnsFromDate_whenItHasLectures() {
        val from = LocalDate(2024, 6, 10)
        val lectures = listOf(makeLecture(startDay = 10))
        val result = findNextDateWithLectures(lectures, from)
        assertEquals(from, result)
    }

    @Test
    fun findNextDateWithLectures_returnsNull_whenNoneFound() {
        val from = LocalDate(2024, 6, 10)
        val result = findNextDateWithLectures(emptyList(), from)
        assertNull(result)
    }

    @Test
    fun findNextDateWithLectures_respectsLookAheadLimit() {
        // Lecture 15 days ahead should not be found (limit is 14)
        val from = LocalDate(2024, 6, 1)
        val lectures = listOf(makeLecture(startDay = 16)) // 15 days from day 1
        val result = findNextDateWithLectures(lectures, from)
        assertNull(result)
    }

    // ─── findNextTwoDatesWithLectures ─────────────────────────────────────────

    @Test
    fun findNextTwoDatesWithLectures_returnsTwoDays() {
        val from = LocalDate(2024, 6, 10)
        val lectures = listOf(
            makeLecture(startDay = 10),
            makeLecture(startDay = 12)
        )
        val result = findNextTwoDatesWithLectures(lectures, from)
        assertEquals(2, result.size)
        assertEquals(LocalDate(2024, 6, 10), result[0])
        assertEquals(LocalDate(2024, 6, 12), result[1])
    }

    @Test
    fun findNextTwoDatesWithLectures_returnsOneDayWhenOnlyOneAvailable() {
        val from = LocalDate(2024, 6, 10)
        val lectures = listOf(makeLecture(startDay = 10))
        val result = findNextTwoDatesWithLectures(lectures, from)
        assertEquals(1, result.size)
        assertEquals(LocalDate(2024, 6, 10), result[0])
    }

    @Test
    fun findNextTwoDatesWithLectures_returnsEmpty_whenNoLectures() {
        val from = LocalDate(2024, 6, 10)
        val result = findNextTwoDatesWithLectures(emptyList(), from)
        assertTrue(result.isEmpty())
    }

    @Test
    fun findNextTwoDatesWithLectures_skipsEmptyDays() {
        val from = LocalDate(2024, 6, 10)
        // days 10, 11, 12: only 10 and 12 have lectures
        val lectures = listOf(
            makeLecture(startDay = 10, shortName = "A"),
            makeLecture(startDay = 12, shortName = "B")
        )
        val result = findNextTwoDatesWithLectures(lectures, from)
        assertEquals(2, result.size)
        assertEquals(LocalDate(2024, 6, 10), result[0])
        assertEquals(LocalDate(2024, 6, 12), result[1])
    }

    // ─── resolveDaySummary ────────────────────────────────────────────────────

    @Test
    fun resolveDaySummary_returnsTodayClasses_whenAvailable() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val lectures = listOf(makeLecture(startDay = 10))
        val result = resolveDaySummary(lectures, today, now)
        assertNotNull(result)
        assertEquals(today, result.date)
        assertTrue(result.isToday)
        assertEquals(1, result.classes.size)
    }

    @Test
    fun resolveDaySummary_fallsBackToNextDay_whenTodayEmpty() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val lectures = listOf(makeLecture(startDay = 11)) // tomorrow
        val result = resolveDaySummary(lectures, today, now)
        assertNotNull(result)
        assertEquals(LocalDate(2024, 6, 11), result.date)
        assertFalse(result.isToday)
        assertEquals(1, result.classes.size)
    }

    @Test
    fun resolveDaySummary_skipsMultipleEmptyDays() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        // Next 4 days are empty; lectures on day 14
        val lectures = listOf(makeLecture(startDay = 14))
        val result = resolveDaySummary(lectures, today, now)
        assertNotNull(result)
        assertEquals(LocalDate(2024, 6, 14), result.date)
        assertFalse(result.isToday)
    }

    @Test
    fun resolveDaySummary_returnsNull_whenNoLecturesInWindow() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val result = resolveDaySummary(emptyList(), today, now)
        assertNull(result)
    }

    @Test
    fun resolveDaySummary_includesMultipleLecturesForDay() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val lectures = listOf(
            makeLecture(startDay = 10, startHour = 8, shortName = "A"),
            makeLecture(startDay = 10, startHour = 11, shortName = "B"),
            makeLecture(startDay = 10, startHour = 14, shortName = "C")
        )
        val result = resolveDaySummary(lectures, today, now)
        assertNotNull(result)
        assertEquals(3, result.classes.size)
    }

    @Test
    fun resolveDaySummary_classesAreSortedByTime() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val lectures = listOf(
            makeLecture(startDay = 10, startHour = 14, shortName = "C"),
            makeLecture(startDay = 10, startHour = 8, shortName = "A"),
            makeLecture(startDay = 10, startHour = 11, shortName = "B")
        )
        val result = resolveDaySummary(lectures, today, now)
        assertNotNull(result)
        assertEquals("A", result.classes[0].shortName)
        assertEquals("B", result.classes[1].shortName)
        assertEquals("C", result.classes[2].shortName)
    }

    // ─── resolveMultiDaySummary ───────────────────────────────────────────────

    @Test
    fun resolveMultiDaySummary_returnsTodayAndTomorrow_whenBothHaveClasses() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val lectures = listOf(
            makeLecture(startDay = 10, shortName = "TODAY"),
            makeLecture(startDay = 11, shortName = "TOMORROW")
        )
        val result = resolveMultiDaySummary(lectures, today, now)
        assertEquals(2, result.days.size)
        assertEquals(today, result.days[0].date)
        assertTrue(result.days[0].isToday)
        assertEquals(LocalDate(2024, 6, 11), result.days[1].date)
        assertFalse(result.days[1].isToday)
    }

    @Test
    fun resolveMultiDaySummary_fallsBackToNextTwoDays_whenTodayEmpty() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        // Today (10) is empty; tomorrow (11) and day-after (12) have lectures
        val lectures = listOf(
            makeLecture(startDay = 11, shortName = "DAY1"),
            makeLecture(startDay = 12, shortName = "DAY2")
        )
        val result = resolveMultiDaySummary(lectures, today, now)
        assertEquals(2, result.days.size)
        assertEquals(LocalDate(2024, 6, 11), result.days[0].date)
        assertEquals(LocalDate(2024, 6, 12), result.days[1].date)
    }

    @Test
    fun resolveMultiDaySummary_fallsBackToNextTwoDays_whenTomorrowEmpty() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        // Today (10) has a lecture, tomorrow (11) is empty, day 12 has a lecture
        // Since tomorrow is empty, fallback: find next two days from today
        val lectures = listOf(
            makeLecture(startDay = 10, shortName = "TODAY"),
            makeLecture(startDay = 12, shortName = "DAY_AFTER")
        )
        val result = resolveMultiDaySummary(lectures, today, now)
        assertEquals(2, result.days.size)
        // Fallback picks today (10) and the next day with lectures (12)
        assertEquals(LocalDate(2024, 6, 10), result.days[0].date)
        assertEquals(LocalDate(2024, 6, 12), result.days[1].date)
    }

    @Test
    fun resolveMultiDaySummary_fallsBackToNextTwoDays_whenBothEmpty() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        // Today (10) and tomorrow (11) are empty; days 13 and 15 have lectures
        val lectures = listOf(
            makeLecture(startDay = 13, shortName = "FIRST"),
            makeLecture(startDay = 15, shortName = "SECOND")
        )
        val result = resolveMultiDaySummary(lectures, today, now)
        assertEquals(2, result.days.size)
        assertEquals(LocalDate(2024, 6, 13), result.days[0].date)
        assertEquals(LocalDate(2024, 6, 15), result.days[1].date)
    }

    @Test
    fun resolveMultiDaySummary_returnsEmptyDays_whenNoLecturesAtAll() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val result = resolveMultiDaySummary(emptyList(), today, now)
        assertTrue(result.days.isEmpty())
    }

    @Test
    fun resolveMultiDaySummary_marksTodayCorrectly_whenFallbackStartsFromToday() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        // Today has lectures; tomorrow does not; fallback picks today + next
        val lectures = listOf(
            makeLecture(startDay = 10),
            makeLecture(startDay = 12)
        )
        val result = resolveMultiDaySummary(lectures, today, now)
        assertEquals(2, result.days.size)
        assertTrue(result.days[0].isToday, "First fallback day is today, should be marked isToday=true")
        assertFalse(result.days[1].isToday)
    }

    @Test
    fun resolveMultiDaySummary_marksFutureDayAsNotToday() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        // Both days in fallback are in the future
        val lectures = listOf(
            makeLecture(startDay = 12),
            makeLecture(startDay = 14)
        )
        val result = resolveMultiDaySummary(lectures, today, now)
        assertEquals(2, result.days.size)
        assertFalse(result.days[0].isToday)
        assertFalse(result.days[1].isToday)
    }

    // ─── Date-boundary edge cases ─────────────────────────────────────────────

    @Test
    fun resolveUpNext_acrossMonthBoundary() {
        // June 30 → July 1
        val now = LocalDateTime(2024, 6, 30, 22, 0, 0)
        val lectures = listOf(
            makeLecture(startYear = 2024, startMonth = 7, startDay = 1, startHour = 8, endHour = 9)
        )
        val result = resolveUpNext(lectures, now)
        assertTrue(result is WidgetUpNextState.HasClass)
    }

    @Test
    fun resolveUpNext_acrossYearBoundary() {
        val now = LocalDateTime(2024, 12, 31, 22, 0, 0)
        val lectures = listOf(
            makeLecture(startYear = 2025, startMonth = 1, startDay = 2, startHour = 8, endHour = 9)
        )
        val result = resolveUpNext(lectures, now)
        assertTrue(result is WidgetUpNextState.HasClass)
    }

    @Test
    fun resolveDaySummary_acrossMonthBoundary_fallback() {
        // Querying June 30, lectures only on July 1
        val today = LocalDate(2024, 6, 30)
        val now = LocalDateTime(2024, 6, 30, 7, 0, 0)
        val lectures = listOf(
            makeLecture(startYear = 2024, startMonth = 7, startDay = 1, startHour = 8, endHour = 9)
        )
        val result = resolveDaySummary(lectures, today, now)
        assertNotNull(result)
        assertEquals(LocalDate(2024, 7, 1), result.date)
        assertFalse(result.isToday)
    }

    @Test
    fun resolveMultiDaySummary_acrossMonthBoundary() {
        val today = LocalDate(2024, 6, 30)
        val now = LocalDateTime(2024, 6, 30, 7, 0, 0)
        val lectures = listOf(
            makeLecture(startYear = 2024, startMonth = 6, startDay = 30, startHour = 8, endHour = 9),
            makeLecture(startYear = 2024, startMonth = 7, startDay = 1, startHour = 8, endHour = 9)
        )
        val result = resolveMultiDaySummary(lectures, today, now)
        assertEquals(2, result.days.size)
        assertEquals(today, result.days[0].date)
        assertEquals(LocalDate(2024, 7, 1), result.days[1].date)
    }

    // ─── isToday flag in multi-day summary normal path ────────────────────────

    @Test
    fun resolveMultiDaySummary_normalPath_isTodayFlagCorrect() {
        val today = LocalDate(2024, 6, 10)
        val now = LocalDateTime(2024, 6, 10, 7, 0, 0)
        val lectures = listOf(
            makeLecture(startDay = 10, shortName = "TODAY"),
            makeLecture(startDay = 11, shortName = "TOMORROW")
        )
        val result = resolveMultiDaySummary(lectures, today, now)
        assertTrue(result.days[0].isToday, "Today's summary should have isToday=true")
        assertFalse(result.days[1].isToday, "Tomorrow's summary should have isToday=false")
    }
}
