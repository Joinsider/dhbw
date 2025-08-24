/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.settingsScreen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.permissions.PermissionManager
import de.fampopprol.dhbwhorb.data.permissions.PermissionType

@Composable
fun PermissionSettingsSection(
    modifier: Modifier = Modifier,
    onPermissionsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }

    var missingPermissions by remember { mutableStateOf(permissionManager.getMissingPermissions()) }

    // Permission launcher for notification permission
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        missingPermissions = permissionManager.getMissingPermissions()
        onPermissionsChanged()
    }

    LaunchedEffect(Unit) {
        missingPermissions = permissionManager.getMissingPermissions()
    }

    if (missingPermissions.isNotEmpty()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Permission cards
            missingPermissions.forEach { permission ->
                PermissionCard(
                    permission = permission,
                    onGrantClick = {
                        when (permission.type) {
                            PermissionType.NOTIFICATIONS -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    permissionManager.openNotificationSettings()
                                }
                            }
                            PermissionType.EXACT_ALARMS -> {
                                permissionManager.openExactAlarmSettings()
                            }
                            PermissionType.BATTERY_OPTIMIZATION -> {
                                permissionManager.openBatteryOptimizationSettings()
                            }
                        }
                    },
                    onRefreshClick = {
                        missingPermissions = permissionManager.getMissingPermissions()
                        onPermissionsChanged()
                    }
                )
            }

            if (missingPermissions.any { it.isRequired }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Important Notice",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = "Notifications for grade and timetable changes will not work until you grant the required permissions above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: de.fampopprol.dhbwhorb.data.permissions.PermissionInfo,
    onGrantClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (permission.isRequired)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
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
                    imageVector = when (permission.type) {
                        PermissionType.NOTIFICATIONS -> Icons.Default.Notifications
                        PermissionType.EXACT_ALARMS -> Icons.Default.Schedule
                        PermissionType.BATTERY_OPTIMIZATION -> Icons.Default.BatteryFull
                    },
                    contentDescription = null,
                    tint = if (permission.isRequired)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = permission.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (permission.isRequired)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (permission.isRequired) {
                            Text(
                                text = "Required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = permission.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (permission.isRequired)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh")
                }

                Button(
                    onClick = onGrantClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Grant")
                }
            }
        }
    }
}
