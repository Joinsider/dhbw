/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.settings.SettingsIntent
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import de.fampopprol.dhbwhorb.testutil.fakes.FakePreferencesRepository
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.navItemTestTag
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The page renders inside the logged-in graph, so there is no "not logged in" variant of it any
 * more — the root shows the login screen instead. `AppRoutingTest` covers that.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsPageTest {

    private fun store(preferences: FakePreferencesRepository = FakePreferencesRepository()) =
        SettingsStore(preferences, TestScopes.immediate())

    @Test
    fun settingsPage_displaysBottomNavigation() = runComposeUiTest {
        val store = store()
        setContent { WithTestKoin { SettingsPage(store = store) } }
        waitForIdle()

        onNodeWithTag("settingsPageTitle").assertIsDisplayed()
        // Tags instead of labels: nav captions are localised string resources.
        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertIsDisplayed()
        onNodeWithTag(navItemTestTag(BottomNavItem.GRADES)).assertIsDisplayed()
        store.close()
    }

    @Test
    fun settingsPage_displaysThemeButtons() = runComposeUiTest {
        val store = store()
        setContent { WithTestKoin { SettingsPage(store = store) } }
        waitForIdle()

        onNodeWithTag("themeLightButton").assertIsDisplayed()
        onNodeWithTag("themeDarkButton").assertIsDisplayed()
        onNodeWithTag("themeSystemButton").assertIsDisplayed()
        store.close()
    }

    @Test
    fun settingsPage_themeSelection_reachesTheStore() = runComposeUiTest {
        val preferences = FakePreferencesRepository()
        val store = store(preferences)
        setContent { WithTestKoin { SettingsPage(store = store) } }
        waitForIdle()

        // The button no longer reports to a callback the caller has to wire up — it dispatches,
        // and the state is the assertion.
        onNodeWithTag("themeLightButton").performClick()
        waitForIdle()
        assertEquals(ThemeMode.LIGHT, store.state.value.themeMode)

        onNodeWithTag("themeDarkButton").performClick()
        waitForIdle()
        assertEquals(ThemeMode.DARK, store.state.value.themeMode)

        onNodeWithTag("themeSystemButton").performClick()
        waitForIdle()
        assertEquals(ThemeMode.SYSTEM, store.state.value.themeMode)

        // And it was written through, not only held.
        assertEquals(ThemeMode.SYSTEM, preferences.getThemeMode())
        store.close()
    }

    @Test
    fun settingsPage_showsTheStoredTheme() = runComposeUiTest {
        val store = store(FakePreferencesRepository(themeMode = ThemeMode.DARK))
        store.dispatch(SettingsIntent.Load)

        setContent { WithTestKoin { SettingsPage(store = store) } }
        waitForIdle()

        assertEquals(ThemeMode.DARK, store.state.value.themeMode)
        store.close()
    }

    @Test
    fun settingsPage_displaysLogoutButton() = runComposeUiTest {
        val store = store()
        setContent { WithTestKoin { SettingsPage(store = store) } }
        waitForIdle()

        onNodeWithTag("logoutButton").assertIsDisplayed()
        store.close()
    }

    @Test
    fun settingsPage_logoutButton_callsCallback() = runComposeUiTest {
        var logoutCalled = false
        val store = store()

        setContent {
            WithTestKoin { SettingsPage(onLogout = { logoutCalled = true }, store = store) }
        }
        waitForIdle()

        onNodeWithTag("logoutButton").performClick()
        waitForIdle()
        assertEquals(true, logoutCalled)
        store.close()
    }
}
