/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import de.fampopprol.dhbwhorb.data.theme.ThemePreferencesManager
import de.fampopprol.dhbwhorb.data.calendar.CalendarSyncManager
import de.fampopprol.dhbwhorb.data.calendar.CalendarSyncPreferencesManager
import de.fampopprol.dhbwhorb.data.calendar.CalendarSyncService
import de.fampopprol.dhbwhorb.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    dualisService: DualisService,
    notificationScheduler: NotificationScheduler,
    notificationPreferencesManager: NotificationPreferencesManager,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Theme preferences
    val themePreferencesManager = remember { ThemePreferencesManager(context) }
    val materialYouEnabled by themePreferencesManager.materialYouEnabled.collectAsState(initial = true)
    val themeMode by themePreferencesManager.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

    val notificationsEnabled by notificationPreferencesManager.notificationsEnabled.collectAsState(initial = true)
    val timetableNotificationsEnabled by notificationPreferencesManager.timetableNotificationsEnabled.collectAsState(initial = true)
    val gradeNotificationsEnabled by notificationPreferencesManager.gradeNotificationsEnabled.collectAsState(initial = true)
    val classReminderNotificationsEnabled by notificationPreferencesManager.classReminderNotificationsEnabled.collectAsState(initial = false)
    val classReminderTimeMinutes by notificationPreferencesManager.classReminderTimeMinutes.collectAsState(initial = 30)

    // Check if we're in demo mode
    val isDemoMode = dualisService.isDemoMode()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ===== ALLGEMEINE EINSTELLUNGEN (GENERAL SETTINGS) SECTION =====
        // General Settings Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.general_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Material You Theming Toggle (only show on Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                                text = stringResource(R.string.material_you_theming),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.material_you_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = materialYouEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    themePreferencesManager.setMaterialYouEnabled(enabled)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Theme Mode Selection
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = when (themeMode) {
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                            ThemeMode.SYSTEM -> Icons.Default.Palette
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.theme_mode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.theme_mode_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Theme mode selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = themeMode == mode
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    scope.launch {
                                        themePreferencesManager.setThemeMode(mode)
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                        ThemeMode.SYSTEM -> Icons.Default.Palette
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(when (mode) {
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                    }),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== NOTIFICATION SETTINGS SECTION =====
        // Notification Settings Header
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

        // ===== CALENDAR SYNC SECTION =====
        // Calendar Sync Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Kalender-Synchronisation",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar sync preferences
        val calendarSyncPreferencesManager = remember { CalendarSyncPreferencesManager(context) }
        val calendarSyncManager = remember { CalendarSyncManager(context) }
        val calendarSyncService = remember { CalendarSyncService(context) }
        val calendarSyncEnabled by calendarSyncPreferencesManager.calendarSyncEnabled.collectAsState(initial = false)
        val selectedCalendarId by calendarSyncPreferencesManager.selectedCalendarId.collectAsState(initial = -1L)

        var availableCalendars by remember { mutableStateOf(emptyList<de.fampopprol.dhbwhorb.data.calendar.DeviceCalendar>()) }
        var hasCalendarPermissions by remember { mutableStateOf(false) }
        var showPermissionDeniedMessage by remember { mutableStateOf(false) }

        // Permission launcher for calendar permissions
        val calendarPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val readCalendarGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
            val writeCalendarGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false

            hasCalendarPermissions = readCalendarGranted && writeCalendarGranted

            if (hasCalendarPermissions) {
                // Permissions granted, load calendars and sync if needed
                scope.launch {
                    availableCalendars = calendarSyncManager.getAvailableCalendars()
                    showPermissionDeniedMessage = false

                    // Trigger sync if calendar is already selected
                    if (calendarSyncEnabled && selectedCalendarId != -1L) {
                        calendarSyncService.syncTimetableIfEnabled()
                    }
                }
            } else {
                // Permissions denied, show message with settings redirect
                showPermissionDeniedMessage = true
                scope.launch {
                    // Disable sync since permissions were denied
                    calendarSyncPreferencesManager.setCalendarSyncEnabled(false)
                }
            }
        }

        // Check permissions and load calendars when sync is enabled
        androidx.compose.runtime.LaunchedEffect(calendarSyncEnabled) {
            hasCalendarPermissions = calendarSyncManager.hasCalendarPermissions()
            if (calendarSyncEnabled) {
                if (hasCalendarPermissions) {
                    availableCalendars = calendarSyncManager.getAvailableCalendars()
                    showPermissionDeniedMessage = false
                } else {
                    // Request permissions when sync is enabled but permissions not granted
                    calendarPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                        )
                    )
                }
            }
        }

        // Master calendar sync toggle
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
                            text = "Kalender-Synchronisation aktivieren",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Synchronisiert Ihre Dualis Veranstaltungen automatisch mit Ihrem Geräte-Kalender",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = calendarSyncEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                if (enabled) {
                                    // Check if we already have permissions
                                    hasCalendarPermissions = calendarSyncManager.hasCalendarPermissions()
                                    if (hasCalendarPermissions) {
                                        calendarSyncPreferencesManager.setCalendarSyncEnabled(true)
                                        availableCalendars = calendarSyncManager.getAvailableCalendars()
                                        showPermissionDeniedMessage = false
                                    } else {
                                        // Will trigger permission request in LaunchedEffect
                                        calendarSyncPreferencesManager.setCalendarSyncEnabled(true)
                                    }
                                } else {
                                    // Disable sync and remove events
                                    calendarSyncPreferencesManager.setCalendarSyncEnabled(false)
                                    if (selectedCalendarId != -1L) {
                                        calendarSyncManager.removeDHBWEventsFromCalendar(selectedCalendarId)
                                    }
                                    showPermissionDeniedMessage = false
                                }
                            }
                        }
                    )
                }

                // Show permission denied message with settings redirect button
                if (calendarSyncEnabled && showPermissionDeniedMessage) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Kalender-Berechtigung verweigert",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Um die Kalender-Synchronisation zu nutzen, benötigt die App Zugriff auf Ihren Kalender. Sie können die Berechtigung in den Systemeinstellungen erteilen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Button(
                                onClick = {
                                    // Open app settings
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Zu App-Einstellungen")
                            }
                        }
                    }
                }

                // Show general permission warning if calendar sync is enabled but permissions are missing (and not explicitly denied)
                if (calendarSyncEnabled && !hasCalendarPermissions && !showPermissionDeniedMessage) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "Kalender-Berechtigung wird angefordert...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Calendar selection dropdown (only show when sync is enabled and permissions granted)
        if (calendarSyncEnabled && hasCalendarPermissions && availableCalendars.isNotEmpty()) {
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
                    Text(
                        text = "Kalender auswählen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Wählen Sie den Kalender aus, mit dem Ihre Dualis Veranstaltungen synchronisiert werden sollen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Material 3 ExposedDropdownMenu for calendar selection
                    var expanded by remember { mutableStateOf(false) }
                    val selectedCalendar = availableCalendars.find { it.id == selectedCalendarId }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedCalendar?.displayName ?: "Kalender auswählen",
                            onValueChange = { },
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth(),
                            label = { Text("Kalender") },
                            supportingText = if (selectedCalendar != null) {
                                { Text("${selectedCalendar.accountName} (${selectedCalendar.accountType})") }
                            } else null
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableCalendars.forEach { calendar ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = calendar.displayName,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "${calendar.accountName} (${calendar.accountType})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        scope.launch {
                                            // Remove events from previously selected calendar
                                            if (selectedCalendarId != -1L) {
                                                calendarSyncManager.removeDHBWEventsFromCalendar(selectedCalendarId)
                                            }

                                            calendarSyncPreferencesManager.setSelectedCalendarId(calendar.id)

                                            // Trigger sync with the new calendar
                                            calendarSyncService.syncTimetableIfEnabled()
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Sync Now Button
                    var isSyncing by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                try {
                                    calendarSyncService.syncAllAvailableTimetableData()
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSyncing && selectedCalendarId != -1L,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(if (isSyncing) "Synchronisiere..." else "Jetzt synchronisieren")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
    }
}
