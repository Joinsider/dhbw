/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.timetable

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.domain.usecase.AwaitFullWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.GetWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.RefreshTimetable
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.collectEffects
import de.fampopprol.dhbwhorb.testutil.fakes.FakeTimetableRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The effect handler, against a fake repository.
 *
 * These are the tests the old `TimetableViewModel` could not have: its loading state lived in a
 * Compose `mutableStateOf`, so reading it needed a composition.
 */
class TimetableStoreTest {

    private fun store(repository: FakeTimetableRepository) = TimetableStore(
        getWeekTimetable = GetWeekTimetable(repository),
        awaitFullWeekTimetable = AwaitFullWeekTimetable(repository),
        refreshTimetable = RefreshTimetable(repository),
        scope = TestScopes.immediate()
    )

    private fun lecture(name: String) = Lecture(
        id = 1,
        shortName = name,
        fullName = null,
        start = LocalDateTime(2026, 3, 2, 8, 15),
        end = LocalDateTime(2026, 3, 2, 12, 0),
        location = "HOR-120",
        isTest = false
    )

    private fun week(offset: Int, lectures: List<Lecture>, partial: Boolean = false) = TimetableWeek(
        weekOffset = offset,
        start = LocalDateTime(2026, 3, 2, 0, 0),
        end = LocalDateTime(2026, 3, 8, 23, 59),
        lectures = lectures,
        isPartial = partial
    )

    @Test
    fun focusingAnUnloadedWeek_loadsIt() = runTest {
        val repository = FakeTimetableRepository(week = Outcome.Ok(week(0, listOf(lecture("T4INF")))))
        val store = store(repository)

        store.dispatch(TimetableIntent.WeekFocused(0))

        assertEquals(listOf(0), repository.requestedWeeks)
        assertEquals(1, store.state.value.week(0).lectures.size)
        store.close()
    }

    @Test
    fun focusingALoadedWeekAgain_doesNotRefetchIt() = runTest {
        val repository = FakeTimetableRepository(week = Outcome.Ok(week(0, listOf(lecture("T4INF")))))
        val store = store(repository)

        store.dispatch(TimetableIntent.WeekFocused(0))
        store.dispatch(TimetableIntent.WeekFocused(1))
        store.dispatch(TimetableIntent.WeekFocused(0))

        // This is the "tab switch causes no reload" property, at the level of the pager: coming
        // back to a week already in the store costs nothing.
        assertEquals(listOf(0, 1), repository.requestedWeeks)
        store.close()
    }

    @Test
    fun anEmptyWeek_countsAsLoaded() = runTest {
        val repository = FakeTimetableRepository(week = Outcome.Ok(week(0, emptyList())))
        val store = store(repository)

        store.dispatch(TimetableIntent.WeekFocused(0))
        store.dispatch(TimetableIntent.WeekFocused(0))

        // A semester-break week has no lectures and must still not be asked for twice.
        assertEquals(listOf(0), repository.requestedWeeks)
        store.close()
    }

    @Test
    fun aSkeletonWeek_isFollowedByTheCompleteOne() = runTest {
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(week(0, listOf(lecture("T4INF")), partial = true)),
            fullWeek = Outcome.Ok(
                week(0, listOf(lecture("T4INF").copy(lecturers = listOf("B.Sc. Julian Schmidt"))))
            )
        )
        val store = store(repository)

        store.dispatch(TimetableIntent.WeekFocused(0))

        val loaded = store.state.value.week(0)
        assertFalse(loaded.isPartial, "The complete week has to replace the skeleton")
        assertEquals(listOf("B.Sc. Julian Schmidt"), loaded.lectures.single().lecturers)
        assertEquals(listOf(0), repository.awaitedWeeks, "It joins the fetch already running")
        store.close()
    }

    @Test
    fun aCompleteWeek_isNotWaitedForTwice() = runTest {
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(week(0, listOf(lecture("T4INF")), partial = false))
        )
        val store = store(repository)

        store.dispatch(TimetableIntent.WeekFocused(0))

        assertTrue(repository.awaitedWeeks.isEmpty())
        store.close()
    }

    @Test
    fun aFailedLoad_leavesNoWeekMarkedAsLoading() = runTest {
        val repository = FakeTimetableRepository(week = Outcome.Err(AppError.Offline))
        val store = store(repository)

        store.dispatch(TimetableIntent.WeekFocused(0))

        val week = store.state.value.week(0)
        assertEquals(AppError.Offline, week.error)
        assertFalse(week.isLoading, "A finished attempt must clear its own spinner")
        store.close()
    }

    @Test
    fun refreshing_bypassesTheCacheAndKeepsTheOldLecturesOnFailure() = runTest {
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(week(0, listOf(lecture("T4INF")))),
            refreshed = Outcome.Err(AppError.Offline)
        )
        val store = store(repository)
        val effects = mutableListOf<TimetableEffect>()
        val collector = collectEffects(store) { effects += it }

        store.dispatch(TimetableIntent.WeekFocused(0))
        store.dispatch(TimetableIntent.Refresh(0))

        val week = store.state.value.week(0)
        assertEquals(listOf(0), repository.refreshedWeeks)
        assertEquals(1, week.lectures.size, "A failed refresh must not blank the week")
        assertEquals(
            listOf<TimetableEffect>(TimetableEffect.RefreshFailed(AppError.Offline)),
            effects,
            "A refresh the user asked for has to say when it failed"
        )
        collector.cancel()
        store.close()
    }

    @Test
    fun refreshing_succeeds_andReplacesTheWeekWithTheFreshOne() = runTest {
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(week(0, listOf(lecture("T4INF")))),
            refreshed = Outcome.Ok(week(0, listOf(lecture("T4INF"), lecture("PROG"))))
        )
        val store = store(repository)

        store.dispatch(TimetableIntent.WeekFocused(0))
        store.dispatch(TimetableIntent.Refresh(0))

        assertEquals(2, store.state.value.week(0).lectures.size)
        store.close()
    }

    @Test
    fun aSkeletonWeek_whoseFullFetchFails_stillLeavesTheSkeletonVisible() = runTest {
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(week(0, listOf(lecture("T4INF")), partial = true)),
            fullWeek = Outcome.Err(AppError.Offline)
        )
        val store = store(repository)

        store.dispatch(TimetableIntent.WeekFocused(0))

        val loaded = store.state.value.week(0)
        assertEquals(AppError.Offline, loaded.error)
        assertEquals(1, loaded.lectures.size, "the skeleton stays visible even though completing it failed")
        store.close()
    }

    @Test
    fun openingAndDismissingTheLectureDialog() = runTest {
        val repository = FakeTimetableRepository()
        val store = store(repository)

        store.dispatch(TimetableIntent.LectureOpened(lecture("T4INF")))
        assertEquals("T4INF", store.state.value.selectedLecture?.shortName)

        store.dispatch(TimetableIntent.LectureDismissed)
        assertEquals(null, store.state.value.selectedLecture)
        store.close()
    }

    @Test
    fun afterClose_dispatchingChangesNothing() = runTest {
        val repository = FakeTimetableRepository(week = Outcome.Ok(week(0, listOf(lecture("T4INF")))))
        val store = store(repository)

        store.close()
        store.dispatch(TimetableIntent.WeekFocused(0))

        // Replaces the ViewModel cleanup tests: navigating away must not let an in-flight load
        // write to a state nobody is showing any more.
        assertTrue(repository.requestedWeeks.isEmpty())
        assertEquals(TimetableState(), store.state.value)
    }
}
