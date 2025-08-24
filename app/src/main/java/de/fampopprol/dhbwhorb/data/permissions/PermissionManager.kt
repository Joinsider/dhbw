/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    /**
     * Check if notification permission is granted
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // On older Android versions, notification permission was granted by default
            true
        }
    }

    /**
     * Check if exact alarm permission is granted (Android 12+)
     */
    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Check if battery optimization is disabled for the app
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Open notification settings for the app
     */
    fun openNotificationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Open exact alarm settings (Android 12+)
     */
    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Open battery optimization settings - tries multiple approaches for better compatibility
     */
    fun openBatteryOptimizationSettings() {
        try {
            // First try to open the direct request dialog for this app
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // If that fails, open the general battery optimization settings
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // As a last resort, open app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Get all missing permissions and their descriptions
     */
    fun getMissingPermissions(): List<PermissionInfo> {
        val missing = mutableListOf<PermissionInfo>()

        if (!hasNotificationPermission()) {
            missing.add(
                PermissionInfo(
                    type = PermissionType.NOTIFICATIONS,
                    title = "Notification Permission",
                    description = "Required to show notifications about timetable changes and new grades",
                    isRequired = true
                )
            )
        }

        if (!hasExactAlarmPermission()) {
            missing.add(
                PermissionInfo(
                    type = PermissionType.EXACT_ALARMS,
                    title = "Exact Alarm Permission",
                    description = "Required for precise class reminder notifications",
                    isRequired = false
                )
            )
        }

        if (!isBatteryOptimizationDisabled()) {
            missing.add(
                PermissionInfo(
                    type = PermissionType.BATTERY_OPTIMIZATION,
                    title = "Battery Optimization",
                    description = "Disable battery optimization to ensure reliable background notifications",
                    isRequired = false
                )
            )
        }

        return missing
    }
}

data class PermissionInfo(
    val type: PermissionType,
    val title: String,
    val description: String,
    val isRequired: Boolean
)

enum class PermissionType {
    NOTIFICATIONS,
    EXACT_ALARMS,
    BATTERY_OPTIMIZATION
}
