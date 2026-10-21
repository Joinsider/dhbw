/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.preferences

import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
import de.fampopprol.dhbwhorb.domain.model.LectureCheck
import de.fampopprol.dhbwhorb.domain.model.LectureCheckOutcome
import de.fampopprol.dhbwhorb.testutil.TestPlatformSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationPreferencesTest {

    private val platformSettings = TestPlatformSettings()
    private val preferences = NotificationPreferences(SettingsStorage(platformSettings, FakeSecureStorage()))

    private fun storeRaw(value: String) {
        platformSettings.setString("last_lecture_check", value)
    }

    @Test
    fun lastLectureCheck_isNullBeforeAnyRun() {
        assertNull(preferences.getLastLectureCheck())
    }

    @Test
    fun lastLectureCheck_roundTripsEveryOutcome() {
        LectureCheckOutcome.entries.forEach { outcome ->
            val check = LectureCheck(epochMillis = 1_700_000_123_456L, outcome = outcome)

            preferences.setLastLectureCheck(check)

            assertEquals(check, preferences.getLastLectureCheck(), "outcome $outcome")
        }
    }

    @Test
    fun lastLectureCheck_isStoredAsEpochAndOutcomeName() {
        preferences.setLastLectureCheck(LectureCheck(42L, LectureCheckOutcome.OFFLINE))

        assertEquals("42;OFFLINE", platformSettings.getStringOrNull("last_lecture_check"))
    }

    @Test
    fun lastLectureCheck_secondWriteReplacesTheFirst() {
        preferences.setLastLectureCheck(LectureCheck(1L, LectureCheckOutcome.FAILED))
        preferences.setLastLectureCheck(LectureCheck(2L, LectureCheckOutcome.SUCCESS))

        assertEquals(LectureCheck(2L, LectureCheckOutcome.SUCCESS), preferences.getLastLectureCheck())
    }

    @Test
    fun lastLectureCheck_malformedValuesReadAsNull() {
        val malformed = listOf(
            "",                          // empty
            "1700000000000",             // no outcome part
            "1700000000000;",            // empty outcome
            ";SUCCESS",                  // empty time
            "abc;SUCCESS",               // time is not a number
            "1700000000000;BOGUS",       // unknown outcome
            "1700000000000;success",     // outcome names are case-sensitive
            "1;SUCCESS;extra",           // too many parts
        )

        malformed.forEach { raw ->
            storeRaw(raw)
            assertNull(preferences.getLastLectureCheck(), "stored value \"$raw\"")
        }
    }

    @Test
    fun reminderLeadMinutes_acceptsOnlyTheOfferedChoices() {
        NotificationPreferences.REMINDER_LEAD_CHOICES.forEach { minutes ->
            preferences.setReminderLeadMinutes(minutes)
            assertEquals(minutes, preferences.getReminderLeadMinutes())
        }
    }

    @Test
    fun reminderLeadMinutes_unofferedOrUnreadableValuesFallBackToOff() {
        listOf("45", "-15", "abc", "").forEach { raw ->
            platformSettings.setString("lecture_reminder_lead_minutes", raw)
            assertEquals(0, preferences.getReminderLeadMinutes(), "stored value \"$raw\"")
        }
    }
}
