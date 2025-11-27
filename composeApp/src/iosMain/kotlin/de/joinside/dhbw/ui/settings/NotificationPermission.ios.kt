package de.joinside.dhbw.ui.settings

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequest(
    onPermissionResult: (Boolean) -> Unit
): () -> Unit {
    // iOS uses NotificationDispatcher.requestPermission directly elsewhere
    return { onPermissionResult(true) }
}

@Composable
actual fun checkNotificationPermission(): Boolean {
    // Assume permission is managed elsewhere for now
    return true
}

