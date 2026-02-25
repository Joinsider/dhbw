/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher

/**
 * Android-specific composable that handles notification permission requests.
 * Returns a lambda that can be called to request permission.
 */
@Composable
actual fun rememberNotificationPermissionRequest(
    onPermissionResult: (Boolean) -> Unit
): () -> Unit {
    // For Android 13+ (API 33+), we need to request POST_NOTIFICATIONS permission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                onPermissionResult(granted)
            }
        )

        return {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    } else {
        // Before Android 13, notifications don't require runtime permission
        return {
            onPermissionResult(true)
        }
    }
}

/**
 * Check if notification permission is granted.
 * This is a composable that observes permission state changes.
 */
@Composable
actual fun checkNotificationPermission(): Boolean {
    var hasPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val dispatcher = NotificationDispatcher()
        hasPermission = dispatcher.hasPermission()
    }

    return hasPermission
}

