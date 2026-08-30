/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
import de.fampopprol.dhbwhorb.testutil.TestPlatformSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PreferencesRepositoryImpl] is pure delegation to [ThemePreferences] and
 * [NotificationPreferencesInteractor] - the interesting thing to prove is that each repository
 * method reaches the store it claims to and round-trips a value through it.
 */
class PreferencesRepositoryImplTest {

    private class NoOpSecureStorage : SecureStorageInterface {
        override fun setString(key: String, value: String) {}
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun remove(key: String) {}
        override fun clear() {}
    }

    private fun repository(): PreferencesRepositoryImpl {
        val settingsStorage = SettingsStorage(TestPlatformSettings(), NoOpSecureStorage())
        val themePreferences = ThemePreferences(settingsStorage)
        val notificationPreferences = NotificationPreferencesInteractor(NotificationPreferences(settingsStorage))
        return PreferencesRepositoryImpl(themePreferences, notificationPreferences)
    }

    @Test
    fun themeMode_roundTrips() {
        val repository = repository()
        assertEquals(ThemeMode.SYSTEM, repository.getThemeMode(), "SYSTEM is the documented default")

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.getThemeMode())
    }

    @Test
    fun materialYouEnabled_roundTrips() {
        val repository = repository()

        repository.setMaterialYouEnabled(false)

        assertFalse(repository.isMaterialYouEnabled())
    }

    @Test
    fun customColor_roundTrips() {
        val repository = repository()

        repository.setCustomColor(0xFF00FF00)

        assertEquals(0xFF00FF00, repository.getCustomColor())
    }

    @Test
    fun notificationsEnabled_roundTrips() {
        val repository = repository()
        assertFalse(repository.areNotificationsEnabled())

        repository.setNotificationsEnabled(true)

        assertTrue(repository.areNotificationsEnabled())
    }

    @Test
    fun lectureAlertsEnabled_roundTrips() {
        val repository = repository()

        repository.setLectureAlertsEnabled(true)

        assertTrue(repository.areLectureAlertsEnabled())
    }

    @Test
    fun reminderLeadMinutes_roundTrips() {
        val repository = repository()

        repository.setReminderLeadMinutes(30)

        assertEquals(30, repository.getReminderLeadMinutes())
    }
}
