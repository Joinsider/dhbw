/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TimetablePageTest {

    @Test
    fun timetablePage_displaysWeeklyLecturesView() = runComposeUiTest {
        setContent {
            TimetablePage(
                isLoggedIn = true
            )
        }

        waitForIdle()

        // Verify page is rendered (check for sample lectures)
        onNodeWithText("Sample Lecture").assertIsDisplayed()
    }

    @Test
    fun timetablePage_displaysBottomNavigation_whenLoggedIn() = runComposeUiTest {
        setContent {
            TimetablePage(
                isLoggedIn = true
            )
        }

        waitForIdle()

        // Bottom navigation should be visible when logged in
        onNodeWithText("Timetable").assertIsDisplayed()
        onNodeWithText("Grades").assertIsDisplayed()
        onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun timetablePage_hidesBottomNavigation_whenNotLoggedIn() = runComposeUiTest {
        setContent {
            TimetablePage(
                isLoggedIn = false
            )
        }

        waitForIdle()

        // Bottom navigation should not be visible when not logged in
        onNodeWithText("Timetable").assertDoesNotExist()
        onNodeWithText("Grades").assertDoesNotExist()
        onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun timetablePage_displaysMultipleLectures() = runComposeUiTest {
        setContent {
            TimetablePage(
                isLoggedIn = true
            )
        }

        waitForIdle()

        // Check that multiple lectures are displayed
        onNodeWithText("Sample Lecture").assertIsDisplayed()
        onNodeWithText("Advanced Topics").assertIsDisplayed()
        onNodeWithText("Practical Session").assertIsDisplayed()
    }
}

