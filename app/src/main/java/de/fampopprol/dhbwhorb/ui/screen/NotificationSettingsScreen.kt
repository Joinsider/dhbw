package de.fampopprol.dhbwhorb.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.notification.DHBWNotificationManager
import de.fampopprol.dhbwhorb.data.notification.ExactAlarmPermissionDialog
import de.fampopprol.dhbwhorb.data.notification.ExactAlarmPermissionHelper
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingsScreen(
    dualisService: DualisService,
    notificationScheduler: NotificationScheduler,
    notificationPreferencesManager: NotificationPreferencesManager,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val notificationsEnabled by notificationPreferencesManager.notificationsEnabled.collectAsState(initial = true)
    val timetableNotificationsEnabled by notificationPreferencesManager.timetableNotificationsEnabled.collectAsState(initial = true)
    val gradeNotificationsEnabled by notificationPreferencesManager.gradeNotificationsEnabled.collectAsState(initial = true)
    val classReminderNotificationsEnabled by notificationPreferencesManager.classReminderNotificationsEnabled.collectAsState(initial = false)
    val classReminderTimeMinutes by notificationPreferencesManager.classReminderTimeMinutes.collectAsState(initial = 30)

    // State for exact alarm permission dialog
    var showExactAlarmPermissionDialog by remember { mutableStateOf(false) }
    val exactAlarmPermissionHelper = remember { ExactAlarmPermissionHelper(context) }

    // Check if we're in demo mode
    val isDemoMode = dualisService.isDemoMode()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
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
                            text = stringResource(R.string.enable_notifications_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
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

        // Class reminder notifications
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            if (enabled) {
                                // Check if exact alarm permission is needed
                                if (exactAlarmPermissionHelper.shouldRequestPermission()) {
                                    // Show permission dialog first
                                    showExactAlarmPermissionDialog = true
                                } else {
                                    // Permission already granted or not needed, proceed
                                    scope.launch {
                                        notificationPreferencesManager.setClassReminderNotificationsEnabled(enabled)
                                        notificationScheduler.scheduleClassReminders()
                                    }
                                }
                            } else {
                                // Disabling reminders doesn't need permission check
                                scope.launch {
                                    notificationPreferencesManager.setClassReminderNotificationsEnabled(enabled)
                                    notificationScheduler.cancelClassReminders()
                                }
                            }
                        }
                    )
                }

                // Time picker for reminder timing
                if (classReminderNotificationsEnabled && notificationsEnabled) {
                    var showDropdown by remember { mutableStateOf(false) }
                    val timeOptions = listOf(5, 10, 15, 30, 45, 60, 90, 120)

                    Card(
                        modifier = Modifier
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
            }
        }

        // Demo notification test buttons (only show in demo mode)
        if (isDemoMode) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.demo_mode_test_notifications),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Text(
                        text = stringResource(R.string.test_notifications_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )

                    // Test Timetable Change Notification Button
                    Button(
                        onClick = {
                            val notificationManager = DHBWNotificationManager(context)
                            val demoChanges = listOf(
                                "Software Engineering moved to room HOR-120",
                                "Database Systems time changed to 10:00-11:30"
                            )
                            notificationManager.showTimetableChangeNotification(demoChanges)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.test_timetable_change))
                    }

                    // Test Grade Change Notification Button
                    Button(
                        onClick = {
                            val notificationManager = DHBWNotificationManager(context)
                            val demoGrades = listOf(
                                "New grade available: Software Engineering - 1.3",
                                "New grade available: Database Systems - 1.7"
                            )
                            notificationManager.showGradeChangeNotification(demoGrades)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.test_grade_change))
                    }

                    // Test Class Reminder Notification Button
                    Button(
                        onClick = {
                            val notificationManager = DHBWNotificationManager(context)
                            val reminderText = "Software Engineering starts in 30 minutes in room HOR-120"
                            notificationManager.showClassReminderNotification(reminderText, 3001)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.test_class_reminder))
                    }
                }
            }
        }

        // Information card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.how_it_works),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.how_it_works_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Exact alarm permission dialog
        ExactAlarmPermissionDialog(
            isVisible = showExactAlarmPermissionDialog,
            onDismiss = { showExactAlarmPermissionDialog = false },
            onPermissionGranted = {
                // Permission granted, enable class reminders
                showExactAlarmPermissionDialog = false
                scope.launch {
                    notificationPreferencesManager.setClassReminderNotificationsEnabled(true)
                    notificationScheduler.scheduleClassReminders()
                }
            },
            onPermissionDenied = {
                showExactAlarmPermissionDialog = false
                // Don't enable class reminders if permission denied
            }
        )
    }
}
