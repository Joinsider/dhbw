/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `checkNotificationPermission()`/`remindersFireExactly()` are hardwired to `true` on the desktop
 * actuals (see NotificationPermission.desktop.kt) — the permission-denied warning and the inexact-
 * alarm banner are therefore Android-only branches, not reachable from this suite.
 */
@OptIn(ExperimentalTestApi::class)
class NotificationSettingsCardTest {

    @Test
    fun notificationsOff_lectureAlertsSectionIsHidden() = runComposeUiTest {
        setContent {
            NotificationSettingsCard(state = NotificationSettingsState(notificationsEnabled = false))
        }

        onNodeWithTag("notificationsEnabledSwitch").assertIsOff()
        assertFailsWith<AssertionError> { onNodeWithTag("lectureAlertsEnabledSwitch").assertIsDisplayed() }
    }

    @Test
    fun notificationsOn_lectureAlertsSectionIsShown() = runComposeUiTest {
        setContent {
            NotificationSettingsCard(state = NotificationSettingsState(notificationsEnabled = true))
        }
        waitForIdle()

        onNodeWithTag("notificationsEnabledSwitch").assertIsOn()
        onNodeWithTag("lectureAlertsEnabledSwitch").assertIsDisplayed()
    }

    @Test
    fun togglingMasterSwitch_reportsTheNewValue() = runComposeUiTest {
        var enabled: Boolean? = null
        setContent {
            NotificationSettingsCard(
                state = NotificationSettingsState(notificationsEnabled = false),
                callbacks = NotificationSettingsCallbacks(onNotificationsEnabledChange = { enabled = it }),
            )
        }

        onNodeWithTag("notificationsEnabledSwitch").performClick()

        assertEquals(true, enabled)
    }

    @Test
    fun togglingLectureAlertsSwitch_reportsTheNewValue() = runComposeUiTest {
        var enabled: Boolean? = null
        setContent {
            NotificationSettingsCard(
                state = NotificationSettingsState(notificationsEnabled = true, lectureAlertsEnabled = false),
                callbacks = NotificationSettingsCallbacks(onLectureAlertsEnabledChange = { enabled = it }),
            )
        }
        waitForIdle()

        onNodeWithTag("lectureAlertsEnabledSwitch").performClick()
        waitForIdle()

        assertEquals(true, enabled)
    }

    @Test
    fun reminderLeadChoices_areAllOffered() = runComposeUiTest {
        setContent {
            NotificationSettingsCard(state = NotificationSettingsState(notificationsEnabled = true))
        }
        waitForIdle()

        for (minutes in NotificationPreferences.REMINDER_LEAD_CHOICES) {
            onNodeWithTag(reminderLeadTestTag(minutes)).assertIsDisplayed()
        }
    }

    @Test
    fun selectingAReminderLead_reportsIt() = runComposeUiTest {
        var selected: Int? = null
        setContent {
            NotificationSettingsCard(
                state = NotificationSettingsState(notificationsEnabled = true),
                callbacks = NotificationSettingsCallbacks(onReminderLeadChange = { selected = it }),
            )
        }
        waitForIdle()

        onNodeWithTag(reminderLeadTestTag(60)).performClick()

        assertEquals(60, selected)
    }

    @Test
    fun manualCheckButton_absentWhenNoCallbackProvided() = runComposeUiTest {
        setContent {
            NotificationSettingsCard(
                state = NotificationSettingsState(notificationsEnabled = true),
                onManualCheckRequested = null,
            )
        }
        waitForIdle()

        assertFailsWith<AssertionError> { onNodeWithTag("checkNowButton").assertIsDisplayed() }
    }

    @Test
    fun manualCheckButton_click_runsTheCallback() = runComposeUiTest {
        var invoked = false
        setContent {
            NotificationSettingsCard(
                state = NotificationSettingsState(notificationsEnabled = true),
                onManualCheckRequested = { invoked = true },
            )
        }
        waitForIdle()

        onNodeWithTag("checkNowButton").performClick()
        waitForIdle()

        assertTrue(invoked)
    }
}
