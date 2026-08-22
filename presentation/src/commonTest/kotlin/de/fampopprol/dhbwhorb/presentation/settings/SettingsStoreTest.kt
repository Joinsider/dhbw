/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.settings

import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.testutil.fakes.FakePreferencesRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {

    private fun store(preferences: FakePreferencesRepository = FakePreferencesRepository()) =
        SettingsStore(preferences, TestScopes.immediate())

    @Test
    fun loading_readsEverySetting() = runTest {
        val preferences = FakePreferencesRepository(
            themeMode = ThemeMode.DARK,
            materialYou = false,
            seedColor = 123,
            notifications = true,
            lectureAlerts = true
        )
        val store = store(preferences)

        store.dispatch(SettingsIntent.Load)

        val state = store.state.value
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertFalse(state.materialYouEnabled)
        assertEquals(123, state.seedColor)
        assertTrue(state.notificationsEnabled)
        assertTrue(state.lectureAlertsEnabled)
        store.close()
    }

    @Test
    fun aChangedSetting_isPersistedAndSurvivesAReload() = runTest {
        val preferences = FakePreferencesRepository()
        val store = store(preferences)

        store.dispatch(SettingsIntent.ThemeModeChanged(ThemeMode.LIGHT))
        assertEquals(ThemeMode.LIGHT, store.state.value.themeMode)

        // Reading it back proves the write happened rather than only the state changing.
        val reloaded = store(preferences)
        reloaded.dispatch(SettingsIntent.Load)
        assertEquals(ThemeMode.LIGHT, reloaded.state.value.themeMode)

        store.close()
        reloaded.close()
    }

    @Test
    fun theSeedColour_isAPlainArgbValue() = runTest {
        val store = store()

        // A Long, not a Compose Color: this state has to survive into Shared.framework, where
        // Compose does not exist.
        store.dispatch(SettingsIntent.SeedColorChanged(0xFF6650a4))

        assertEquals(0xFF6650a4, store.state.value.seedColor)
        store.close()
    }

    @Test
    fun notificationTogglesAreIndependent() = runTest {
        val store = store()

        store.dispatch(SettingsIntent.NotificationsChanged(true))
        assertTrue(store.state.value.notificationsEnabled)
        assertFalse(store.state.value.lectureAlertsEnabled)

        store.dispatch(SettingsIntent.LectureAlertsChanged(true))
        assertTrue(store.state.value.notificationsEnabled)
        assertTrue(store.state.value.lectureAlertsEnabled)
        store.close()
    }
}
