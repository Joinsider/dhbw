/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.settingsScreen.classReminderSection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import kotlinx.coroutines.launch

@Composable
fun ClassReminderToggle(
    notificationPreferencesManager: NotificationPreferencesManager,
    notificationScheduler: NotificationScheduler,
    classReminderNotificationsEnabled: Boolean,
    notificationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.class_reminders),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.class_reminders_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = classReminderNotificationsEnabled,
            enabled = notificationsEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    notificationPreferencesManager.setClassReminderNotificationsEnabled(enabled)
                    if (enabled) {
                        notificationScheduler.scheduleClassReminders()
                    } else {
                        notificationScheduler.cancelClassReminders()
                    }
                }
            }
        )
    }
}
