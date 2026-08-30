/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.preferences

import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
import de.fampopprol.dhbwhorb.testutil.TestPlatformSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [NotificationPreferencesInteractor] wraps [NotificationPreferences] in observable
 * [kotlinx.coroutines.flow.StateFlow]s. The interesting behaviour is the state flows staying in
 * sync with the backing store, and [NotificationPreferencesInteractor.shouldProcessLectureAlerts]'
 * combination of the two flags.
 */
class NotificationPreferencesInteractorTest {

    private class NoOpSecureStorage : SecureStorageInterface {
        override fun setString(key: String, value: String) {}
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun remove(key: String) {}
        override fun clear() {}
    }

    private lateinit var preferences: NotificationPreferences
    private lateinit var interactor: NotificationPreferencesInteractor

    @BeforeTest
    fun setup() {
        val settingsStorage = SettingsStorage(TestPlatformSettings(), NoOpSecureStorage())
        preferences = NotificationPreferences(settingsStorage)
        interactor = NotificationPreferencesInteractor(preferences)
    }

    @Test
    fun defaults_areAllOff() {
        assertFalse(interactor.getNotificationsEnabled())
        assertFalse(interactor.getLectureAlertsEnabled())
        assertEquals(0, interactor.getReminderLeadMinutes())
        assertFalse(interactor.shouldProcessLectureAlerts())
    }

    @Test
    fun setNotificationsEnabled_updatesTheGetterAndTheFlow() {
        interactor.setNotificationsEnabled(true)

        assertTrue(interactor.getNotificationsEnabled())
        assertTrue(interactor.notificationsEnabled.value)
        // And it persisted through the backing preferences, not just the in-memory flow.
        assertTrue(preferences.getNotificationsEnabled())
    }

    @Test
    fun setLectureAlertsEnabled_updatesTheGetterAndTheFlow() {
        interactor.setLectureAlertsEnabled(true)

        assertTrue(interactor.getLectureAlertsEnabled())
        assertTrue(interactor.lectureAlertsEnabled.value)
        assertTrue(preferences.getLectureAlertsEnabled())
    }

    @Test
    fun shouldProcessLectureAlerts_requiresBothFlagsEnabled() {
        assertFalse(interactor.shouldProcessLectureAlerts(), "both off")

        interactor.setNotificationsEnabled(true)
        assertFalse(interactor.shouldProcessLectureAlerts(), "only the master switch is on")

        interactor.setLectureAlertsEnabled(true)
        assertTrue(interactor.shouldProcessLectureAlerts(), "both on")

        interactor.setNotificationsEnabled(false)
        assertFalse(interactor.shouldProcessLectureAlerts(), "master switch turned back off")
    }

    @Test
    fun reminderLeadMinutes_roundTripsThroughTheFlow() {
        interactor.setReminderLeadMinutes(30)

        assertEquals(30, interactor.getReminderLeadMinutes())
        assertEquals(30, interactor.reminderLeadMinutes.value)
        assertEquals(30, preferences.getReminderLeadMinutes())
    }

    @Test
    fun refresh_pullsChangesMadeDirectlyOnThePreferences_intoTheFlows() {
        // Simulate an external change - e.g. another interactor instance, or a settings screen
        // writing straight to the store - that this interactor's flows have not seen yet.
        preferences.setNotificationsEnabled(true)
        preferences.setLectureAlertsEnabled(true)
        assertFalse(interactor.getNotificationsEnabled(), "the flow has not picked it up yet")

        interactor.refresh()

        assertTrue(interactor.getNotificationsEnabled())
        assertTrue(interactor.getLectureAlertsEnabled())
    }

    @Test
    fun aFreshInteractor_readsWhateverWasAlreadyStored() {
        preferences.setNotificationsEnabled(true)
        preferences.setReminderLeadMinutes(60)

        val freshInteractor = NotificationPreferencesInteractor(preferences)

        assertTrue(freshInteractor.getNotificationsEnabled())
        assertEquals(60, freshInteractor.getReminderLeadMinutes())
    }
}
