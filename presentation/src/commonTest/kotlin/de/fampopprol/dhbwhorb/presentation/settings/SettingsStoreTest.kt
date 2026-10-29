/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.settings

import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.domain.model.LectureCheck
import de.fampopprol.dhbwhorb.domain.model.LectureCheckOutcome
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
    fun accentChoice_isPersistedAndSurvivesAReload() = runTest {
        val preferences = FakePreferencesRepository()
        val store = store(preferences)

        store.dispatch(SettingsIntent.AccentChoiceChanged("pink"))
        assertEquals("pink", store.state.value.accentChoice)

        val reloaded = store(preferences)
        reloaded.dispatch(SettingsIntent.Load)
        assertEquals("pink", reloaded.state.value.accentChoice)

        store.close()
        reloaded.close()
    }

    @Test
    fun accentChoice_unknownIdFallsBackToBrand() = runTest {
        val preferences = FakePreferencesRepository()
        val store = store(preferences)

        store.dispatch(SettingsIntent.AccentChoiceChanged("orange-of-the-future"))

        assertEquals(SettingsState.DEFAULT_ACCENT_CHOICE, store.state.value.accentChoice)
        assertEquals(SettingsState.DEFAULT_ACCENT_CHOICE, preferences.getAccentChoice())
        store.close()
    }

    @Test
    fun loading_replacesAStoredUnknownAccentWithBrand() = runTest {
        val store = store(FakePreferencesRepository(accentChoice = "removed-colour"))

        store.dispatch(SettingsIntent.Load)

        assertEquals("brand", store.state.value.accentChoice)
        store.close()
    }

    @Test
    fun reducer_accentChoiceChanged_replacesOnlyTheAccent() {
        val before = SettingsState(themeMode = ThemeMode.DARK, accentChoice = "brand")

        val after = reduceSettings(before, SettingsMsg.AccentChoiceChanged("teal"))

        assertEquals("teal", after.accentChoice)
        assertEquals(ThemeMode.DARK, after.themeMode)
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

    @Test
    fun materialYou_isPersistedAndSurvivesAReload() = runTest {
        val preferences = FakePreferencesRepository()
        val store = store(preferences)

        store.dispatch(SettingsIntent.MaterialYouChanged(false))
        assertFalse(store.state.value.materialYouEnabled)

        val reloaded = store(preferences)
        reloaded.dispatch(SettingsIntent.Load)
        assertFalse(reloaded.state.value.materialYouEnabled)

        store.close()
        reloaded.close()
    }

    @Test
    fun theReminderLead_isPersistedAndSurvivesAReload() = runTest {
        val preferences = FakePreferencesRepository()
        val store = store(preferences)

        store.dispatch(SettingsIntent.ReminderLeadChanged(30))
        assertEquals(30, store.state.value.reminderLeadMinutes)

        val reloaded = store(preferences)
        reloaded.dispatch(SettingsIntent.Load)
        assertEquals(30, reloaded.state.value.reminderLeadMinutes)

        store.close()
        reloaded.close()
    }

    @Test
    fun loading_readsTheLastLectureCheck() = runTest {
        val check = LectureCheck(epochMillis = 1_700_000_000_000, outcome = LectureCheckOutcome.OFFLINE)
        val store = store(FakePreferencesRepository(lastLectureCheck = check))

        store.dispatch(SettingsIntent.Load)

        assertEquals(check, store.state.value.lastLectureCheck)
        store.close()
    }

    @Test
    fun watchingTheLastLectureCheck_showsEachFinishedRunWithoutReloading() = runTest {
        val preferences = FakePreferencesRepository()
        val store = store(preferences)
        store.dispatch(SettingsIntent.WatchLastLectureCheck)
        assertEquals(null, store.state.value.lastLectureCheck)

        val run = LectureCheck(epochMillis = 1_700_000_500_000, outcome = LectureCheckOutcome.SUCCESS)
        preferences.recordLectureCheck(run)

        assertEquals(run, store.state.value.lastLectureCheck)
        store.close()
    }

    @Test
    fun reducer_lastLectureCheckChanged_replacesOnlyTheCheck() {
        val check = LectureCheck(epochMillis = 42, outcome = LectureCheckOutcome.FAILED)
        val before = SettingsState(themeMode = ThemeMode.DARK, reminderLeadMinutes = 30)

        val after = reduceSettings(before, SettingsMsg.LastLectureCheckChanged(check))

        assertEquals(check, after.lastLectureCheck)
        assertEquals(ThemeMode.DARK, after.themeMode)
        assertEquals(30, after.reminderLeadMinutes)
    }

    @Test
    fun reducer_lastLectureCheckChangedToNull_clearsIt() {
        val before = SettingsState(lastLectureCheck = LectureCheck(7, LectureCheckOutcome.SUCCESS))

        val after = reduceSettings(before, SettingsMsg.LastLectureCheckChanged(null))

        assertEquals(null, after.lastLectureCheck)
    }
}
