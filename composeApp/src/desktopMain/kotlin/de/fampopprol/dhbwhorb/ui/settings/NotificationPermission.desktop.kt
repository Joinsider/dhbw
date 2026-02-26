package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequest(
    onPermissionResult: (Boolean) -> Unit
): () -> Unit {
    return { onPermissionResult(true) }
}

@Composable
actual fun checkNotificationPermission(): Boolean = true
