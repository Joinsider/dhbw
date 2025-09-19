package de.fampopprol.dhbwhorb.ui.screen.settingsScreen.calendarSync

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.calendar.CalendarExportService
import de.fampopprol.dhbwhorb.data.calendar.CalendarSyncPreferencesManager
import de.fampopprol.dhbwhorb.data.calendar.CalendarSyncScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSyncSettingsSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { CalendarSyncPreferencesManager(context) }
    val service = remember { CalendarExportService(context) }

    var sectionEnabled by remember { mutableStateOf(prefs.isSectionEnabled()) }
    var syncActive by remember { mutableStateOf(prefs.isSyncActive()) }

    // Load calendars when permissions available and section enabled
    var calendars by remember { mutableStateOf(emptyList<CalendarExportService.CalendarInfo>()) }
    var calendarExpanded by remember { mutableStateOf(false) }

    var selectedCalendarId by remember { mutableStateOf(service.getChosenCalendarId()) }
    var selectedCalendarLabel by remember { mutableStateOf("") }

    var showStopConfirm by remember { mutableStateOf(false) }

    // Function to refresh calendars and validate selection
    fun refreshCalendarsAndValidateSelection() {
        if (service.hasReadPermission()) {
            val newCalendars = service.listDeviceCalendars()
            calendars = newCalendars

            // Check if current selection still exists
            val currentId = selectedCalendarId
            if (currentId != null && currentId != -1L) {
                val stillExists = newCalendars.any { it.id == currentId }
                if (stillExists) {
                    // Update label for existing selection (calendar might have been renamed)
                    selectedCalendarLabel = newCalendars.firstOrNull { it.id == currentId }?.name.orEmpty()
                } else {
                    // Selected calendar no longer exists - deactivate syncing without deletion
                    selectedCalendarId = null
                    selectedCalendarLabel = ""
                    service.setChosenCalendarId(-1) // Clear stored selection

                    if (syncActive) {
                        // Stop syncing but don't delete events
                        CalendarSyncScheduler.stop(context)
                        syncActive = false
                        prefs.setSyncActive(false)
                        Toast.makeText(
                            context,
                            context.getString(R.string.calendar_sync_stopped_calendar_removed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } else {
                // No calendar was previously selected, just ensure label is empty
                selectedCalendarLabel = ""
            }
        } else {
            calendars = emptyList()
            selectedCalendarId = null
            selectedCalendarLabel = ""
        }
    }

    // Permissions launcher for calendar
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
                result[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            refreshCalendarsAndValidateSelection()
            if (calendars.isEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.calendar_no_calendars_found),
                    Toast.LENGTH_LONG
                ).show()
                sectionEnabled = false
                prefs.setSectionEnabled(false)
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.calendar_permissions_required),
                Toast.LENGTH_LONG
            ).show()
            sectionEnabled = false
            prefs.setSectionEnabled(false)
        }
    }

    // Track permission state for proper reloading
    val hasPermissions = service.hasReadPermission() && service.hasWritePermission()

    // Always fetch calendars on app restart/composition (even if sync is active)
    LaunchedEffect(Unit) {
        if (hasPermissions) {
            refreshCalendarsAndValidateSelection()
        }
    }

    // Always refresh calendars when permissions change
    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            refreshCalendarsAndValidateSelection()
        } else {
            calendars = emptyList()
            selectedCalendarId = null
            selectedCalendarLabel = ""
        }
    }

    // Always refresh calendars when section toggle changes (enabled/disabled)
    LaunchedEffect(sectionEnabled) {
        if (hasPermissions) {
            refreshCalendarsAndValidateSelection()
        }
    }

    // Always refresh calendars when sync state changes (active/inactive)
    LaunchedEffect(syncActive) {
        if (hasPermissions) {
            refreshCalendarsAndValidateSelection()
        }
    }

    // Refresh calendars when dropdown is expanded (to catch real-time changes)
    LaunchedEffect(calendarExpanded) {
        if (calendarExpanded && hasPermissions) {
            refreshCalendarsAndValidateSelection()
        }
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.calendar_sync_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.calendar_sync_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = sectionEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            // Check permissions
                            if (!service.hasReadPermission() || !service.hasWritePermission()) {
                                permissionsLauncher.launch(service.requiredPermissions())
                                // Tentatively enable; if denied in callback, we'll revert
                                sectionEnabled = true
                                prefs.setSectionEnabled(true)
                            } else {
                                // Load calendars and validate selection
                                refreshCalendarsAndValidateSelection()
                                if (calendars.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.calendar_no_calendars_found),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    sectionEnabled = false
                                    prefs.setSectionEnabled(false)
                                } else {
                                    sectionEnabled = true
                                    prefs.setSectionEnabled(true)
                                }
                            }
                        } else {
                            // If turning off while sync active, show confirm dialog; otherwise just disable
                            if (syncActive) {
                                showStopConfirm = true
                            } else {
                                sectionEnabled = false
                                prefs.setSectionEnabled(false)
                            }
                        }
                    }
                )
            }

            if (sectionEnabled) {
                // Dropdown for calendars
                ExposedDropdownMenuBox(
                    expanded = calendarExpanded,
                    onExpandedChange = { calendarExpanded = !calendarExpanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedCalendarLabel,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.calendar_select_calendar)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calendarExpanded) },
                        enabled = !syncActive // Disable when syncing is active
                    )
                    ExposedDropdownMenu(
                        expanded = calendarExpanded,
                        onDismissRequest = { calendarExpanded = false }
                    ) {
                        // Add key to force recomposition when calendars list changes
                        if (calendars.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_no_calendars_found)) },
                                onClick = { calendarExpanded = false },
                                enabled = false
                            )
                        } else {
                            calendars.forEachIndexed { index, cal ->
                                DropdownMenuItem(
                                    text = { Text(cal.name) },
                                    onClick = {
                                        selectedCalendarId = cal.id
                                        selectedCalendarLabel = cal.name
                                        service.setChosenCalendarId(cal.id)
                                        calendarExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Start/Stop Sync button
                val buttonLabel =
                    if (syncActive) R.string.calendar_stop_sync else R.string.calendar_start_sync
                val buttonColors = if (syncActive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Button(
                    onClick = {
                        val chosen = selectedCalendarId
                        if (!syncActive) {
                            if (chosen == null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.calendar_select_calendar_first),
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            // Start periodic sync
                            CalendarSyncScheduler.start(context)
                            syncActive = true
                            prefs.setSyncActive(true)
                            // Optional immediate sync
                            service.syncWithLocalTimetable()
                            Toast.makeText(
                                context,
                                context.getString(R.string.calendar_sync_started),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Stop requested
                            showStopConfirm = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = buttonColors
                ) {
                    Text(text = stringResource(buttonLabel))
                }
            }

            if (showStopConfirm) {
                AlertDialog(
                    onDismissRequest = { showStopConfirm = false },
                    title = { Text(stringResource(R.string.calendar_stop_sync_title)) },
                    text = { Text(stringResource(R.string.calendar_stop_sync_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            // Delete existing events and stop
                            val result = service.deleteAllExportedEventsDetailed()
                            CalendarSyncScheduler.stop(context)
                            syncActive = false
                            sectionEnabled = false
                            prefs.setSyncActive(false)
                            prefs.setSectionEnabled(false)
                            showStopConfirm = false
                            if (result.deleted > 0) {
                                Toast.makeText(context, context.getString(R.string.calendar_deleted_events_count, result.deleted), Toast.LENGTH_LONG).show()
                            } else {
                                val msg = when (result.reason) {
                                    CalendarExportService.DeleteReason.NO_PERMISSIONS -> R.string.calendar_delete_none_permissions
                                    CalendarExportService.DeleteReason.NO_CALENDAR_SELECTED -> R.string.calendar_delete_none_no_calendar
                                    CalendarExportService.DeleteReason.NO_MATCHING_EVENTS -> R.string.calendar_delete_none_no_matching
                                    else -> R.string.calendar_delete_none_no_matching
                                }
                                Toast.makeText(context, context.getString(msg), Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Text(stringResource(R.string.calendar_stop_and_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            // Keep events, just stop
                            CalendarSyncScheduler.stop(context)
                            syncActive = false
                            sectionEnabled = false
                            prefs.setSyncActive(false)
                            prefs.setSectionEnabled(false)
                            showStopConfirm = false
                            Toast.makeText(context, context.getString(R.string.calendar_sync_stopped), Toast.LENGTH_SHORT).show()
                        }) {
                            Text(stringResource(R.string.calendar_stop_keep))
                        }
                    }
                )
            }

            if (sectionEnabled) {
                Text(
                    text = stringResource(R.string.calendar_sync_note),
                    Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
