/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.settingsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.classReminderSection.ClassReminderTimePicker
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.classReminderSection.ClassReminderToggle

@Composable
fun ClassReminderSettingsSection(
    notificationPreferencesManager: NotificationPreferencesManager,
    notificationScheduler: NotificationScheduler,
    notificationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val classReminderNotificationsEnabled by notificationPreferencesManager.classReminderNotificationsEnabled.collectAsState(initial = false)
    val classReminderTimeMinutes by notificationPreferencesManager.classReminderTimeMinutes.collectAsState(initial = 30)

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ClassReminderToggle(
                notificationPreferencesManager = notificationPreferencesManager,
                notificationScheduler = notificationScheduler,
                classReminderNotificationsEnabled = classReminderNotificationsEnabled,
                notificationsEnabled = notificationsEnabled
            )

            // Show time picker only when reminders are enabled and notifications are allowed
            if (classReminderNotificationsEnabled && notificationsEnabled) {
                ClassReminderTimePicker(
                    notificationPreferencesManager = notificationPreferencesManager,
                    notificationScheduler = notificationScheduler,
                    classReminderTimeMinutes = classReminderTimeMinutes
                )
            }
        }
    }
}
