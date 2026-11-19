/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import io.github.aakira.napier.Napier
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import org.koin.compose.koinInject

/**
 * Android-specific composable that handles notification permission requests.
 * Returns a lambda that can be called to request permission.
 */
@Composable
actual fun rememberNotificationPermissionRequest(
    onPermissionResult: (Boolean) -> Unit
): () -> Unit {
    // For Android 13+ (API 33+), we need to request POST_NOTIFICATIONS permission
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                onPermissionResult(granted)
            }
        )
        // A `val` assigned here so the lambda below can't be parsed as a trailing-lambda
        // argument to the rememberLauncherForActivityResult call above it.
        val requestPermission: () -> Unit = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        requestPermission
    } else {
        // Before Android 13, notifications don't require runtime permission
        val requestPermission: () -> Unit = { onPermissionResult(true) }
        requestPermission
    }
}

/**
 * Check if notification permission is granted.
 * This is a composable that observes permission state changes.
 */
@Composable
actual fun checkNotificationPermission(): Boolean {
    var hasPermission by remember { mutableStateOf(false) }
    val dispatcher: NotificationDispatcher = koinInject()

    LaunchedEffect(dispatcher) {
        hasPermission = dispatcher.hasPermission()
    }

    return hasPermission
}

/**
 * Whether exact alarms are allowed, read again every time the app comes back to the foreground.
 *
 * The permission is granted on a system screen the button below opens, so the user leaves the app
 * to change it. Read once, as this used to be, the warning stayed on screen after they had allowed
 * exact alarms and come back — until the settings screen was built again from scratch.
 */
@Composable
actual fun remindersFireExactly(): Boolean {
    val context = LocalContext.current
    var exact by remember(context) { mutableStateOf(canScheduleExactAlarms(context)) }
    LifecycleResumeEffect(context) {
        exact = canScheduleExactAlarms(context)
        onPauseOrDispose { }
    }
    return exact
}

private fun canScheduleExactAlarms(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        true
    } else {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

@Composable
actual fun rememberExactAlarmSettingsOpener(): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return remember(context) {
        {
            // Android offers no way to ask for this in a dialog: the permission is granted on a
            // settings screen, and all an app may do is take the user there.
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { Napier.w("No exact-alarm settings screen on this device", it, tag = "Settings") }
            Unit
        }
    }
}
