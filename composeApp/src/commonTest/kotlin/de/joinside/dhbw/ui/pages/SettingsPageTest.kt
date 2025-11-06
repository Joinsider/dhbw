/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SettingsPageTest {

    @Test
    fun settingsPage_displaysBottomNavigation_whenLoggedIn() = runComposeUiTest {
        setContent {
            SettingsPage(
                isLoggedIn = true
            )
        }

        waitForIdle()

        // Page title should be visible
        onNodeWithTag("settingsPageTitle").assertIsDisplayed()

        // Bottom navigation items should be visible when logged in
        onNodeWithText("Timetable").assertIsDisplayed()
        onNodeWithText("Grades").assertIsDisplayed()
    }

    @Test
    fun settingsPage_hidesBottomNavigation_whenNotLoggedIn() = runComposeUiTest {
        setContent {
            SettingsPage(
                isLoggedIn = false
            )
        }

        waitForIdle()

        // Page title should still be visible
        onNodeWithTag("settingsPageTitle").assertIsDisplayed()

        // Bottom navigation items should not be visible when not logged in
        onNodeWithText("Timetable").assertDoesNotExist()
        onNodeWithText("Grades").assertDoesNotExist()
    }
}