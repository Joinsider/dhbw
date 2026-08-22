/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.timetable

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.domain.model.Lecture
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reducer is pure, so these tests need no `runTest`, no dispatcher and no fakes doing any
 * work — they call one function and look at what comes back. That is the property the whole split
 * exists for: everything uncertain lives in the effect handler, and the state transitions can be
 * checked in isolation.
 */
class TimetableReducerTest {

    private fun reduce(state: TimetableState, msg: TimetableMsg) = reduceTimetable(state, msg)

    private val monday = LocalDateTime(2026, 3, 2, 0, 0)
    private val sunday = LocalDateTime(2026, 3, 8, 23, 59)

    private fun lecture(name: String) = Lecture(
        id = 1,
        shortName = name,
        fullName = null,
        start = LocalDateTime(2026, 3, 2, 8, 15),
        end = LocalDateTime(2026, 3, 2, 12, 0),
        location = "HOR-120",
        isTest = false
    )

    @Test
    fun focusingAWeek_recordsItsDatesAndMakesItCurrent() {
        val state = reduce(TimetableState(), TimetableMsg.WeekFocused(-3, monday, sunday))

        assertEquals(-3, state.currentWeekOffset)
        assertEquals(monday, state.week(-3).start)
        assertEquals(sunday, state.week(-3).end)
    }

    @Test
    fun focusingAWeek_leavesOtherWeeksAlone() {
        val loaded = reduce(
            TimetableState(),
            TimetableMsg.WeekLoaded(0, listOf(lecture("T4INF")), isPartial = false)
        )

        val state = reduce(loaded, TimetableMsg.WeekFocused(1, monday, sunday))

        assertEquals(1, state.week(0).lectures.size, "Week 0 must keep what it had")
        assertTrue(state.week(1).lectures.isEmpty())
    }

    @Test
    fun loadStarted_distinguishesLoadingFromRefreshing() {
        val loading = reduce(TimetableState(), TimetableMsg.LoadStarted(0, isRefresh = false))
        assertTrue(loading.week(0).isLoading)
        assertFalse(loading.week(0).isRefreshing)

        val refreshing = reduce(TimetableState(), TimetableMsg.LoadStarted(0, isRefresh = true))
        assertFalse(refreshing.week(0).isLoading)
        assertTrue(refreshing.week(0).isRefreshing)
    }

    @Test
    fun startingALoad_clearsThePreviousError() {
        val failed = reduce(TimetableState(), TimetableMsg.LoadFailed(0, AppError.Offline))
        val retrying = reduce(failed, TimetableMsg.LoadStarted(0, isRefresh = false))

        assertNull(retrying.week(0).error, "A retry must not show the error it is retrying")
    }

    @Test
    fun aPartialWeek_isMarkedAsSuch_andCompletedByTheNextLoad() {
        val skeleton = reduce(
            TimetableState(),
            TimetableMsg.WeekLoaded(0, listOf(lecture("T4INF")), isPartial = true)
        )
        assertTrue(skeleton.week(0).isPartial)

        val full = reduce(
            skeleton,
            TimetableMsg.WeekLoaded(0, listOf(lecture("T4INF")), isPartial = false)
        )
        assertFalse(full.week(0).isPartial)
    }

    @Test
    fun failing_keepsTheLecturesAlreadyShown() {
        val loaded = reduce(
            TimetableState(),
            TimetableMsg.WeekLoaded(0, listOf(lecture("T4INF")), isPartial = false)
        )

        val failed = reduce(loaded, TimetableMsg.LoadFailed(0, AppError.SessionExpired))

        assertEquals(1, failed.week(0).lectures.size, "A failed refresh must not blank the week")
        assertEquals(AppError.SessionExpired, failed.week(0).error)
    }

    @Test
    fun finishing_clearsBothLoadingFlags() {
        val busy = reduce(TimetableState(), TimetableMsg.LoadStarted(0, isRefresh = true))
        val done = reduce(busy, TimetableMsg.LoadFinished(0))

        assertFalse(done.week(0).isLoading)
        assertFalse(done.week(0).isRefreshing)
    }

    @Test
    fun theSameMessageTwice_producesTheSameState() {
        val msg = TimetableMsg.WeekLoaded(0, listOf(lecture("T4INF")), isPartial = false)

        assertEquals(
            reduce(TimetableState(), msg),
            reduce(TimetableState(), msg),
            "The reducer has to be a pure function of state and message"
        )
    }

    @Test
    fun selectingALecture_andDismissingIt() {
        val open = reduce(TimetableState(), TimetableMsg.LectureSelected(lecture("T4INF")))
        assertEquals("T4INF", open.selectedLecture?.shortName)

        val closed = reduce(open, TimetableMsg.LectureSelected(null))
        assertNull(closed.selectedLecture)
    }

    @Test
    fun anUntouchedWeek_isDistinguishableFromAnEmptyOne() {
        assertTrue(TimetableState().week(5).isUntouched)

        val loaded = reduce(
            TimetableState(),
            TimetableMsg.WeekLoaded(5, emptyList(), isPartial = false)
        )
        // Semester break: no lectures, but the week has been asked for and must not be re-fetched
        // on every pager settle.
        assertFalse(loaded.week(5).isUntouched)
    }
}
