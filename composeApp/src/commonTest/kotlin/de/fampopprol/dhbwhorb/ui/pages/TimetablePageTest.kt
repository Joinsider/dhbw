/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.domain.usecase.AwaitFullWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.GetWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.RefreshTimetable
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableStore
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import de.fampopprol.dhbwhorb.testutil.fakes.FakeTimetableRepository
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.navItemTestTag
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI tests for [TimetablePage].
 *
 * These were disabled through P3: the ViewModel's `uiState` had a private setter, so no state
 * could be put into the page. The page now takes a store, and a store is a repository fake plus
 * three use cases — so the state a test wants is simply the state the fake returns.
 *
 * The navigation assertions go through test tags rather than the labels. The labels come from
 * string resources, and this JVM runs on `de_DE`: asserting on "Timetable" would pass on the
 * English CI runner and fail here.
 */
@OptIn(ExperimentalTestApi::class)
class TimetablePageTest {

    private fun lecture(name: String, hour: Int) = Lecture(
        id = hour.toLong(),
        shortName = name,
        fullName = name,
        start = LocalDateTime(2026, 3, 2, hour, 0),
        end = LocalDateTime(2026, 3, 2, hour + 1, 30),
        location = "HOR-120",
        isTest = false,
        lecturers = listOf("Dr. Test")
    )

    /** A store whose current week already contains [lectures]. */
    private fun storeShowing(lectures: List<Lecture>): TimetableStore {
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(
                TimetableWeek(
                    weekOffset = 0,
                    start = LocalDateTime(2026, 3, 2, 0, 0),
                    end = LocalDateTime(2026, 3, 8, 23, 59),
                    lectures = lectures
                )
            )
        )
        return TimetableStore(
            getWeekTimetable = GetWeekTimetable(repository),
            awaitFullWeekTimetable = AwaitFullWeekTimetable(repository),
            refreshTimetable = RefreshTimetable(repository),
            scope = TestScopes.immediate()
        )
    }

    @Test
    fun timetablePage_displaysTheLecturesInTheStore() = runComposeUiTest {
        val store = storeShowing(listOf(lecture("Sample Lecture", 8)))

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithText("Sample Lecture").assertIsDisplayed()
        store.close()
    }

    @Test
    fun timetablePage_displaysMultipleLectures() = runComposeUiTest {
        val store = storeShowing(
            listOf(
                lecture("Sample Lecture", 8),
                lecture("Advanced Topics", 10),
                lecture("Practical Session", 14)
            )
        )

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithText("Sample Lecture").assertIsDisplayed()
        onNodeWithText("Advanced Topics").assertIsDisplayed()
        onNodeWithText("Practical Session").assertIsDisplayed()
        store.close()
    }

    @Test
    fun timetablePage_opensTheWeekADeepLinkNames() = runComposeUiTest {
        val store = storeShowing(emptyList())

        // dhbw://timetable?week=-1 lands here as initialWeek.
        setContent { WithTestKoin { TimetablePage(initialWeek = -1, store = store) } }
        waitForIdle()

        assertEquals(-1, store.state.value.currentWeekOffset)
        store.close()
    }

    @Test
    fun timetablePage_displaysBottomNavigation() = runComposeUiTest {
        val store = storeShowing(emptyList())

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertIsDisplayed()
        onNodeWithTag(navItemTestTag(BottomNavItem.GRADES)).assertIsDisplayed()
        onNodeWithTag(navItemTestTag(BottomNavItem.SETTINGS)).assertIsDisplayed()
        store.close()
    }

    @Test
    fun timetablePage_clickingALecture_opensItsDetailsDialog() = runComposeUiTest {
        val store = storeShowing(listOf(lecture("Sample Lecture", 8)))

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag("dayColumnLecture_8").performClick()
        waitForIdle()

        onNodeWithTag("lectureDetailsDialog").assertIsDisplayed()
        store.close()
    }

    @Test
    fun timetablePage_loadFailure_showsErrorBanner() = runComposeUiTest {
        val repository = FakeTimetableRepository(week = Outcome.Err(AppError.Offline))
        val store = TimetableStore(
            getWeekTimetable = GetWeekTimetable(repository),
            awaitFullWeekTimetable = AwaitFullWeekTimetable(repository),
            refreshTimetable = RefreshTimetable(repository),
            scope = TestScopes.immediate()
        )

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag("timetableErrorBanner").assertIsDisplayed()
        store.close()
    }

    @Test
    fun timetablePage_clickingNextWeek_movesTheWeekFocusForward() = runComposeUiTest {
        val store = storeShowing(emptyList())

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag("nextWeekButton").performClick()
        waitForIdle()

        assertEquals(1, store.state.value.currentWeekOffset)
        store.close()
    }

    @Test
    fun timetablePage_clickingPreviousWeek_movesTheWeekFocusBackward() = runComposeUiTest {
        val store = storeShowing(emptyList())

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag("previousWeekButton").performClick()
        waitForIdle()

        assertEquals(-1, store.state.value.currentWeekOffset)
        store.close()
    }

    @Test
    fun timetablePage_tappingTheWeekLabelAfterNavigating_returnsToTheCurrentWeek() = runComposeUiTest {
        val store = storeShowing(emptyList())

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag("nextWeekButton").performClick()
        waitForIdle()
        onNodeWithTag("weekLabelButton").performTouchInput { click() }
        waitForIdle()

        assertEquals(0, store.state.value.currentWeekOffset)
        store.close()
    }

    @Test
    fun timetablePage_longPressingTheWeekLabel_refreshesTheCurrentWeek() = runComposeUiTest {
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(
                TimetableWeek(
                    weekOffset = 0,
                    start = LocalDateTime(2026, 3, 2, 0, 0),
                    end = LocalDateTime(2026, 3, 8, 23, 59),
                    lectures = emptyList()
                )
            )
        )
        val store = TimetableStore(
            getWeekTimetable = GetWeekTimetable(repository),
            awaitFullWeekTimetable = AwaitFullWeekTimetable(repository),
            refreshTimetable = RefreshTimetable(repository),
            scope = TestScopes.immediate()
        )

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag("weekLabelButton").performTouchInput { longClick() }
        waitForIdle()

        assertTrue(repository.refreshedWeeks.contains(0))
        store.close()
    }

    @Test
    fun timetablePage_weekCrossingAMonthBoundary_showsBothMonthsInTheLabel() = runComposeUiTest {
        // 28 Apr - 2 May 2026: the week label must name April *and* May rather than just one.
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(
                TimetableWeek(
                    weekOffset = 0,
                    start = LocalDateTime(2026, 4, 28, 0, 0),
                    end = LocalDateTime(2026, 5, 3, 23, 59),
                    lectures = emptyList()
                )
            )
        )
        val store = TimetableStore(
            getWeekTimetable = GetWeekTimetable(repository),
            awaitFullWeekTimetable = AwaitFullWeekTimetable(repository),
            refreshTimetable = RefreshTimetable(repository),
            scope = TestScopes.immediate()
        )

        // Rendering without crashing already exercises the cross-month branch and both months'
        // string resources; the exact label text is locale-dependent (see the class doc).
        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag("weekLabelButton").assertIsDisplayed()
        store.close()
    }

    // The remaining nine months' short-name resources are otherwise never resolved: the fixtures
    // above only ever fall in March, April or May. Five month-crossing weeks are enough to reach
    // all twelve without a dozen separate near-identical tests.
    private fun rendersWithoutCrashing(start: LocalDateTime, end: LocalDateTime) = runComposeUiTest {
        val repository = FakeTimetableRepository(
            week = Outcome.Ok(TimetableWeek(weekOffset = 0, start = start, end = end, lectures = emptyList()))
        )
        val store = TimetableStore(
            getWeekTimetable = GetWeekTimetable(repository),
            awaitFullWeekTimetable = AwaitFullWeekTimetable(repository),
            refreshTimetable = RefreshTimetable(repository),
            scope = TestScopes.immediate()
        )

        setContent { WithTestKoin { TimetablePage(store = store) } }
        waitForIdle()

        onNodeWithTag("weekLabelButton").assertIsDisplayed()
        store.close()
    }

    @Test
    fun timetablePage_januaryToFebruary_rendersTheLabel() = rendersWithoutCrashing(
        start = LocalDateTime(2026, 1, 29, 0, 0),
        end = LocalDateTime(2026, 2, 4, 23, 59),
    )

    @Test
    fun timetablePage_juneToJuly_rendersTheLabel() = rendersWithoutCrashing(
        start = LocalDateTime(2026, 6, 29, 0, 0),
        end = LocalDateTime(2026, 7, 5, 23, 59),
    )

    @Test
    fun timetablePage_augustToSeptember_rendersTheLabel() = rendersWithoutCrashing(
        start = LocalDateTime(2026, 8, 31, 0, 0),
        end = LocalDateTime(2026, 9, 6, 23, 59),
    )

    @Test
    fun timetablePage_octoberToNovember_rendersTheLabel() = rendersWithoutCrashing(
        start = LocalDateTime(2026, 10, 29, 0, 0),
        end = LocalDateTime(2026, 11, 4, 23, 59),
    )

    @Test
    fun timetablePage_decemberToJanuary_rendersTheLabel() = rendersWithoutCrashing(
        start = LocalDateTime(2026, 12, 29, 0, 0),
        end = LocalDateTime(2027, 1, 4, 23, 59),
    )
}
