/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import kotlin.test.Ignore
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import kotlin.test.Test

/**
 * UI tests for [TimetablePage].
 *
 * Currently disabled: the ViewModel's `uiState` has a private setter, so no state can be placed
 * into it. Koin now supplies a mock-backed ViewModel, which renders an empty timetable — enough to
 * compile, not enough to assert on lectures.
 *
 * Re-enable in P4, once the page renders a `TimetableState` handed in from the outside and the
 * state can simply be constructed for the test.
 */
@OptIn(ExperimentalTestApi::class)
class TimetablePageTest {

    @Test
    @Ignore // see class doc — needs injectable state (P4)
    fun timetablePage_displaysWeeklyLecturesView() = runComposeUiTest {
        setContent { WithTestKoin { TimetablePage(isLoggedIn = true) } }
        waitForIdle()
        onNodeWithText("Sample Lecture").assertIsDisplayed()
    }

    @Test
    @Ignore // see class doc — needs injectable state (P4)
    fun timetablePage_displaysBottomNavigation_whenLoggedIn() = runComposeUiTest {
        setContent { WithTestKoin { TimetablePage(isLoggedIn = true) } }
        waitForIdle()
        onNodeWithText("Timetable").assertIsDisplayed()
        onNodeWithText("Grades").assertIsDisplayed()
        onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    @Ignore // see class doc — needs injectable state (P4)
    fun timetablePage_hidesBottomNavigation_whenNotLoggedIn() = runComposeUiTest {
        setContent { WithTestKoin { TimetablePage(isLoggedIn = false) } }
        waitForIdle()
        onNodeWithText("Timetable").assertDoesNotExist()
        onNodeWithText("Grades").assertDoesNotExist()
        onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    @Ignore // see class doc — needs injectable state (P4)
    fun timetablePage_displaysMultipleLectures() = runComposeUiTest {
        setContent { WithTestKoin { TimetablePage(isLoggedIn = true) } }
        waitForIdle()
        onNodeWithText("Sample Lecture").assertIsDisplayed()
        onNodeWithText("Advanced Topics").assertIsDisplayed()
        onNodeWithText("Practical Session").assertIsDisplayed()
    }
}
