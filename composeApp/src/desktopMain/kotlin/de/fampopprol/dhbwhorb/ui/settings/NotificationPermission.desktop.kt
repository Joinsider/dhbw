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

/** Moot: the desktop build has no scheduler to be exact with. */
@Composable
actual fun remindersFireExactly(): Boolean = true

/** No such screen: the desktop build schedules nothing. */
@Composable
actual fun rememberExactAlarmSettingsOpener(): (() -> Unit)? = null
