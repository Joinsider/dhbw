/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
