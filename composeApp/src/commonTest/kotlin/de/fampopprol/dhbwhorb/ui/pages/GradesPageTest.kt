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
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.navItemTestTag
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GradesPageTest {

    @Test
    fun gradesPage_displaysBottomNavigation_whenLoggedIn() = runComposeUiTest {
        setContent {
            WithTestKoin {
                GradesPage(
                    isLoggedIn = true
                )
            }
        }

        waitForIdle()

        // Page title should be visible
        onNodeWithTag("gradesPageTitle").assertIsDisplayed()

        // Bottom navigation should be visible when logged in
        // Tags instead of labels: nav captions are localised string resources.
        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertIsDisplayed()
        onNodeWithTag(navItemTestTag(BottomNavItem.SETTINGS)).assertIsDisplayed()
    }

    @Test
    fun gradesPage_hidesBottomNavigation_whenNotLoggedIn() = runComposeUiTest {
        setContent {
            WithTestKoin {
                GradesPage(
                    isLoggedIn = false
                )
            }
        }

        waitForIdle()

        // Page title should still be visible
        onNodeWithTag("gradesPageTitle").assertIsDisplayed()

        // Bottom navigation should not be visible when not logged in
        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertDoesNotExist()
        onNodeWithTag(navItemTestTag(BottomNavItem.SETTINGS)).assertDoesNotExist()
    }
}

