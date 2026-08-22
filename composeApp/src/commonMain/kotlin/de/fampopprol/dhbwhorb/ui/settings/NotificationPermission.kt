/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable for requesting notification permission.
 * Returns a lambda that triggers the permission request flow.
 *
 * @param onPermissionResult Callback invoked with the result (true if granted, false if denied)
 * @return A function that can be called to request permission
 */
@Composable
expect fun rememberNotificationPermissionRequest(
    onPermissionResult: (Boolean) -> Unit
): () -> Unit

/**
 * Platform-specific composable to check current notification permission status.
 * @return true if permission is granted, false otherwise
 */
@Composable
expect fun checkNotificationPermission(): Boolean

/**
 * Whether the system will run the lecture reminders at the minute they name.
 *
 * `false` only on Android, and only when the user has taken the exact-alarm permission away — the
 * reminders still arrive, the system may just hold them back to save power. The settings screen
 * says so rather than being quietly late.
 */
@Composable
expect fun remindersFireExactly(): Boolean

/**
 * Opens the system screen where exact alarms are allowed, or `null` where there is no such screen.
 *
 * Only Android has one, and only from Android 12. Returning `null` rather than an empty lambda so
 * the settings screen can leave the button out entirely instead of offering one that does nothing.
 */
@Composable
expect fun rememberExactAlarmSettingsOpener(): (() -> Unit)?
