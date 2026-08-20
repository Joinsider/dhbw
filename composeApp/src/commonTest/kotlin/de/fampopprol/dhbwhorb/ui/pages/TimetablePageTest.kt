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
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * UI tests for [TimetablePage].
 *
 * Currently disabled: [TimetableViewModel] is a final class whose `uiState` has a private setter,
 * so no state can be injected. The previous version of this file worked around that with an
 * anonymous `object` plus `as? TimetableViewModel`, which always evaluated to `null` — the tests
 * rendered an empty page and asserted nothing meaningful.
 *
 * Re-enable in P4, once TimetablePage renders a `TimetableState` handed in from the outside and a
 * state can simply be constructed for the test.
 */
@OptIn(ExperimentalTestApi::class)
class TimetablePageTest {

    private fun emptyViewModel() = TimetableViewModel(
        lectureService = null,
        lecturerDao = null,
        lectureLecturerCrossRefDao = null
    )

    @Test
    @Ignore // see class doc — needs injectable state (P4)
    fun timetablePage_displaysWeeklyLecturesView() = runComposeUiTest {
        setContent { TimetablePage(viewModel = emptyViewModel(), isLoggedIn = true) }
        waitForIdle()
        onNodeWithText("Sample Lecture").assertIsDisplayed()
    }

    @Test
    @Ignore // see class doc — needs injectable state (P4)
    fun timetablePage_displaysBottomNavigation_whenLoggedIn() = runComposeUiTest {
        setContent { TimetablePage(viewModel = emptyViewModel(), isLoggedIn = true) }
        waitForIdle()
        onNodeWithText("Timetable").assertIsDisplayed()
        onNodeWithText("Grades").assertIsDisplayed()
        onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    @Ignore // see class doc — needs injectable state (P4)
    fun timetablePage_hidesBottomNavigation_whenNotLoggedIn() = runComposeUiTest {
        setContent { TimetablePage(viewModel = emptyViewModel(), isLoggedIn = false) }
        waitForIdle()
        onNodeWithText("Timetable").assertDoesNotExist()
        onNodeWithText("Grades").assertDoesNotExist()
        onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    @Ignore // see class doc — needs injectable state (P4)
    fun timetablePage_displaysMultipleLectures() = runComposeUiTest {
        setContent { TimetablePage(viewModel = emptyViewModel(), isLoggedIn = true) }
        waitForIdle()
        onNodeWithText("Sample Lecture").assertIsDisplayed()
        onNodeWithText("Advanced Topics").assertIsDisplayed()
        onNodeWithText("Practical Session").assertIsDisplayed()
    }
}
