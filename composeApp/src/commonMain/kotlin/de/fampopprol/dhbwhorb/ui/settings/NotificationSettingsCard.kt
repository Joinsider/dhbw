/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.allow_notifications
import de.fampopprol.dhbwhorb.resources.allow_notifications_description
import de.fampopprol.dhbwhorb.resources.lecture_change_notification
import de.fampopprol.dhbwhorb.resources.lecture_change_notification_description
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.resources.lecture_reminder
import de.fampopprol.dhbwhorb.resources.lecture_reminder_allow_exact
import de.fampopprol.dhbwhorb.resources.lecture_reminder_description
import de.fampopprol.dhbwhorb.resources.lecture_reminder_hour
import de.fampopprol.dhbwhorb.resources.lecture_reminder_inexact
import de.fampopprol.dhbwhorb.resources.lecture_reminder_minutes
import de.fampopprol.dhbwhorb.resources.lecture_reminder_off
import de.fampopprol.dhbwhorb.resources.notifications
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsCard(
    notificationsEnabled: Boolean = false,
    onNotificationsEnabledChange: (Boolean) -> Unit = {},
    lectureAlertsEnabled: Boolean = false,
    onLectureAlertsEnabledChange: (Boolean) -> Unit = {},
    reminderLeadMinutes: Int = 0,
    onReminderLeadChange: (Int) -> Unit = {},
    onManualCheckRequested: (suspend () -> Unit)? = null,  // NEW: callback for manual check
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isCheckingChanges by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<String?>(null) }

    // Platform permission helpers
    val currentPermission = checkNotificationPermission()
    var hasPermission by remember { mutableStateOf(currentPermission) }
    LaunchedEffect(currentPermission) {
        hasPermission = currentPermission
    }
    val requestPermission = rememberNotificationPermissionRequest { granted ->
        hasPermission = granted
        if (granted && !notificationsEnabled) {
            // Auto-enable master toggle when user grants permission
            onNotificationsEnabledChange(true)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Card Title
            Text(
                text = stringResource(Res.string.notifications),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Master Notifications Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (notificationsEnabled) Icons.Default.Notifications
                                         else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(Res.string.allow_notifications),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = stringResource(Res.string.allow_notifications_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 8.dp)
                    )
                }

                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { enabled ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        if (enabled && !hasPermission) {
                            // Trigger platform permission flow; callback will update state
                            requestPermission()
                        } else {
                            onNotificationsEnabledChange(enabled)
                        }
                    },
                    modifier = Modifier.testTag("notificationsEnabledSwitch")
                )
            }

            // Permission Status
            if (!hasPermission && notificationsEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "⚠️ Notification permission denied. Please enable it in system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Lecture Alerts Toggle (visible only when notifications enabled and permission granted)
            AnimatedVisibility(
                visible = notificationsEnabled && hasPermission
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ChangeCircle,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp)
                                )
                                Text(
                                    text = stringResource(Res.string.lecture_change_notification),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(
                                text = stringResource(Res.string.lecture_change_notification_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 24.dp, end = 8.dp)
                            )
                        }

                        Switch(
                            checked = lectureAlertsEnabled,
                            onCheckedChange = { enabled ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                coroutineScope.launch {
                                    onLectureAlertsEnabledChange(enabled)
                                }
                            },
                            modifier = Modifier.testTag("lectureAlertsEnabledSwitch")
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Reminder before a lecture. One picker rather than a switch plus a duration:
                    // for the user it is one choice, and "off" is simply no lead time.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(Res.string.lecture_reminder),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = stringResource(Res.string.lecture_reminder_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                    )

                    // FlowRow, not Row: four chips do not fit side by side on a narrow phone, and
                    // a Row answers that by squeezing the last one into a column of single letters.
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NotificationPreferences.REMINDER_LEAD_CHOICES.forEach { minutes ->
                            FilterChip(
                                selected = reminderLeadMinutes == minutes,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onReminderLeadChange(minutes)
                                },
                                label = { Text(reminderLeadLabel(minutes), maxLines = 1) },
                                modifier = Modifier.testTag(reminderLeadTestTag(minutes))
                            )
                        }
                    }

                    // Only worth saying when it is true, and only Android can make it false.
                    val exactAlarmSettings = rememberExactAlarmSettingsOpener()
                    if (reminderLeadMinutes > 0 && !remindersFireExactly()) {
                        Text(
                            text = stringResource(Res.string.lecture_reminder_inexact),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        if (exactAlarmSettings != null) {
                            TextButton(
                                onClick = exactAlarmSettings,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("allowExactAlarmsButton")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(end = 4.dp)
                                )
                                Text(stringResource(Res.string.lecture_reminder_allow_exact))
                            }
                        }
                    }

                    // Manual Check Button (only if callback provided)
                    if (onManualCheckRequested != null) {
                        Button(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCheckingChanges = true
                                checkResult = null
                                coroutineScope.launch {
                                    try {
                                        onManualCheckRequested()
                                        checkResult = "✅ Check completed"
                                    } catch (e: Exception) {
                                        checkResult = "❌ Error: ${e.message?.take(50)}"
                                    } finally {
                                        isCheckingChanges = false
                                    }
                                }
                            },
                            enabled = !isCheckingChanges,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .testTag("checkNowButton"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            if (isCheckingChanges) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isCheckingChanges) "Checking..." else "Check for Lecture Changes Now",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        // Show check result
                        checkResult?.let { result ->
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.startsWith("✅"))
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

//                    // Test Notification Button
//                    Button(
//                        onClick = {
//                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
//                            coroutineScope.launch {
//                                try {
//                                    val dispatcher = NotificationDispatcher()
//                                    dispatcher.showNotification(
//                                        title = "Test Notification",
//                                        message = "This is a test notification from DHBW Next. If you see this, notifications are working correctly! 🎉",
//                                        lectureId = 0L
//                                    )
//                                } catch (e: Exception) {
//                                    // Handle error - could show a snackbar in a real implementation
//                                    println("Failed to send test notification: ${e.message}")
//                                }
//                            }
//                        },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(top = 12.dp)
//                            .testTag("testNotificationButton"),
//                        colors = ButtonDefaults.buttonColors(
//                            containerColor = MaterialTheme.colorScheme.primaryContainer,
//                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
//                        )
//                    ) {
//                        Text(
//                            text = "Send Test Notification",
//                            style = MaterialTheme.typography.labelLarge
//                        )
//                    }
                }
            }
        }
    }
}

/** "Off", "15 min before", "1 hour before" — the wording the picker shows for a lead time. */
@Composable
private fun reminderLeadLabel(minutes: Int): String = when (minutes) {
    0 -> stringResource(Res.string.lecture_reminder_off)
    60 -> stringResource(Res.string.lecture_reminder_hour)
    else -> stringResource(Res.string.lecture_reminder_minutes, minutes)
}

/**
 * A tag derived from the value rather than the label.
 *
 * The labels are localised, and a UI test that looks for "15 min before" is green on an English
 * runner and red on this machine — see the locale trap in the handoff.
 */
internal fun reminderLeadTestTag(minutes: Int) = "reminderLead_$minutes"
