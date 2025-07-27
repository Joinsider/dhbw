/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.settingsScreen.classReminderSection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import kotlinx.coroutines.launch

@Composable
fun ClassReminderTimePicker(
    notificationPreferencesManager: NotificationPreferencesManager,
    notificationScheduler: NotificationScheduler,
    classReminderTimeMinutes: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showDropdown by remember { mutableStateOf(false) }
    val timeOptions = listOf(5, 10, 15, 30, 45, 60, 90, 120)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDropdown = true },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        text = stringResource(R.string.reminder_time),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.minutes_before_class, classReminderTimeMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) {
            timeOptions.forEach { minutes ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (minutes == 1) stringResource(R.string.one_minute) else stringResource(R.string.x_minutes, minutes),
                            color = if (minutes == classReminderTimeMinutes)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        scope.launch {
                            notificationPreferencesManager.setClassReminderTimeMinutes(minutes)
                            notificationScheduler.scheduleClassReminders() // Reschedule with new time
                        }
                        showDropdown = false
                    }
                )
            }
        }
    }
}
