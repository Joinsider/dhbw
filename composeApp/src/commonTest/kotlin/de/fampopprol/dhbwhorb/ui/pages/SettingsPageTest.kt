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
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.navItemTestTag
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsPageTest {

    @Test
    fun settingsPage_displaysBottomNavigation_whenLoggedIn() = runComposeUiTest {
        setContent {
            WithTestKoin {
                SettingsPage(
                    isLoggedIn = true
                )
            }
        }

        waitForIdle()

        // Page title should be visible
        onNodeWithTag("settingsPageTitle").assertIsDisplayed()

        // Bottom navigation items should be visible when logged in
        // Tags instead of labels: nav captions are localised string resources.
        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertIsDisplayed()
        onNodeWithTag(navItemTestTag(BottomNavItem.GRADES)).assertIsDisplayed()
    }

    @Test
    fun settingsPage_hidesBottomNavigation_whenNotLoggedIn() = runComposeUiTest {
        setContent {
            WithTestKoin {
                SettingsPage(
                    isLoggedIn = false
                )
            }
        }

        waitForIdle()

        // Page title should still be visible
        onNodeWithTag("settingsPageTitle").assertIsDisplayed()

        // Bottom navigation items should not be visible when not logged in
        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertDoesNotExist()
        onNodeWithTag(navItemTestTag(BottomNavItem.GRADES)).assertDoesNotExist()
    }

    @Test
    fun settingsPage_displaysThemeButtons() = runComposeUiTest {
        setContent {
            WithTestKoin {
                SettingsPage(
                    currentThemeMode = ThemeMode.SYSTEM
                )
            }
        }

        waitForIdle()

        // All three theme buttons should be visible
        onNodeWithTag("themeLightButton").assertIsDisplayed()
        onNodeWithTag("themeDarkButton").assertIsDisplayed()
        onNodeWithTag("themeSystemButton").assertIsDisplayed()
    }

    @Test
    fun settingsPage_themeSelection_callsCallback() = runComposeUiTest {
        var selectedTheme: ThemeMode? = null

        setContent {
            WithTestKoin {
                SettingsPage(
                    currentThemeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = { selectedTheme = it }
                )
            }
        }

        waitForIdle()

        // Click on Light theme button
        onNodeWithTag("themeLightButton").performClick()
        waitForIdle()
        assertEquals(ThemeMode.LIGHT, selectedTheme)

        // Click on Dark theme button
        onNodeWithTag("themeDarkButton").performClick()
        waitForIdle()
        assertEquals(ThemeMode.DARK, selectedTheme)

        // Click on System theme button
        onNodeWithTag("themeSystemButton").performClick()
        waitForIdle()
        assertEquals(ThemeMode.SYSTEM, selectedTheme)
    }

    @Test
    fun settingsPage_displaysLogoutButton_whenLoggedIn() = runComposeUiTest {
        setContent {
            WithTestKoin {
                SettingsPage(
                    isLoggedIn = true
                )
            }
        }

        waitForIdle()

        // Logout button should be visible when logged in
        onNodeWithTag("logoutButton").assertIsDisplayed()
    }

    @Test
    fun settingsPage_hidesLogoutButton_whenNotLoggedIn() = runComposeUiTest {
        setContent {
            WithTestKoin {
                SettingsPage(
                    isLoggedIn = false
                )
            }
        }

        waitForIdle()

        // Logout button should not be visible when not logged in
        onNodeWithTag("logoutButton").assertDoesNotExist()
    }

    @Test
    fun settingsPage_logoutButton_callsCallback() = runComposeUiTest {
        var logoutCalled = false

        setContent {
            WithTestKoin {
                SettingsPage(
                    isLoggedIn = true,
                    onLogout = { logoutCalled = true }
                )
            }
        }

        waitForIdle()

        // Click logout button
        onNodeWithTag("logoutButton").performClick()
        waitForIdle()
        assertEquals(true, logoutCalled)
    }
}