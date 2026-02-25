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

