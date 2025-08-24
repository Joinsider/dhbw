/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.settingsScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import de.fampopprol.dhbwhorb.data.permissions.PermissionManager
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingsSection(
    notificationPreferencesManager: NotificationPreferencesManager,
    notificationScheduler: NotificationScheduler,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }
    val scope = rememberCoroutineScope()
    val notificationsEnabled by notificationPreferencesManager.notificationsEnabled.collectAsState(initial = true)
    val timetableNotificationsEnabled by notificationPreferencesManager.timetableNotificationsEnabled.collectAsState(initial = true)
    val gradeNotificationsEnabled by notificationPreferencesManager.gradeNotificationsEnabled.collectAsState(initial = true)

    var hasNotificationPermission by remember { mutableStateOf(permissionManager.hasNotificationPermission()) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission section - show if permissions are missing
        PermissionSettingsSection(
            onPermissionsChanged = {
                hasNotificationPermission = permissionManager.hasNotificationPermission()
            }
        )

        // Notification Settings Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (notificationsEnabled && hasNotificationPermission) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = if (hasNotificationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.notification_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Master notification toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enable_notifications),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasNotificationPermission) {
                                stringResource(R.string.enable_notifications_description)
                            } else {
                                "Grant notification permission above to enable notifications"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (hasNotificationPermission) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(
                        checked = notificationsEnabled && hasNotificationPermission,
                        enabled = hasNotificationPermission,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                notificationPreferencesManager.setNotificationsEnabled(enabled)
                                if (enabled) {
                                    notificationScheduler.schedulePeriodicNotifications()
                                } else {
                                    notificationScheduler.cancelPeriodicNotifications()
                                }
                            }
                        }
                    )
                }
            }
        }

        // Timetable notifications
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.timetable_changes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.timetable_changes_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = timetableNotificationsEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                notificationPreferencesManager.setTimetableNotificationsEnabled(enabled)
                            }
                        }
                    )
                }
            }
        }

        // Grade notifications
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.new_grades),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.new_grades_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = gradeNotificationsEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                notificationPreferencesManager.setGradeNotificationsEnabled(enabled)
                            }
                        }
                    )
                }
            }
        }
    }
}
