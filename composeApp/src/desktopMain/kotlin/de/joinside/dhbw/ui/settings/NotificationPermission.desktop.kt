package de.joinside.dhbw.ui.settings

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequest(
    onPermissionResult: (Boolean) -> Unit
): () -> Unit {
    return { onPermissionResult(true) }
}

@Composable
actual fun checkNotificationPermission(): Boolean = true
