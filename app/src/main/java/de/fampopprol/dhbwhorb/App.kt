/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import de.fampopprol.dhbwhorb.data.notification.RequestNotificationPermission
import de.fampopprol.dhbwhorb.ui.screen.LoginScreen
import de.fampopprol.dhbwhorb.ui.screen.MainScreen
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme
import de.fampopprol.dhbwhorb.ui.components.CalendarViewMode
import kotlinx.coroutines.launch

@Composable
fun App(
    currentTimetableScreenViewMode: CalendarViewMode,
    onTimetableScreenViewModeChanged: (CalendarViewMode) -> Unit
) {
    DHBWHorbTheme {
        val context = LocalContext.current
        val credentialManager = remember { CredentialManager(context) }
        var isLoggedIn by remember { mutableStateOf(credentialManager.isLoggedInBlocking()) }
        var shouldRequestNotificationPermission by remember { mutableStateOf(false) }
        val dualisService = remember { DualisService() }
        val timetableCacheManager = remember { TimetableCacheManager(context) }
        val gradesCacheManager = remember { GradesCacheManager(context) }
        val notificationScheduler = remember { NotificationScheduler(context) }
        val scope = rememberCoroutineScope()

        // Request notification permission when user is logged in
        if (isLoggedIn && shouldRequestNotificationPermission) {
            RequestNotificationPermission { permissionGranted ->
                Log.d("MainActivity", "Notification permission result: $permissionGranted")
                shouldRequestNotificationPermission = false
                if (permissionGranted) {
                    // Permission granted, start notifications
                    notificationScheduler.schedulePeriodicNotifications()
                } else {
                    // Permission denied, notifications will be silently skipped
                    Log.w("MainActivity", "Notification permission denied - notifications will be disabled")
                }
            }
        }

        // Auto-login if credentials are stored
        LaunchedEffect(Unit) {
            if (credentialManager.hasStoredCredentialsBlocking() && !isLoggedIn) {
                val username = credentialManager.getUsernameBlocking()
                val password = credentialManager.getPassword()
                if (username != null && password != null) {
                    dualisService.login(username, password) { result ->
                        if (result != null) {
                            isLoggedIn = true
                            Log.d("MainActivity", "Auto-login successful")
                            // Request notification permission after successful auto-login
                            shouldRequestNotificationPermission = true
                        } else {
                            Log.e("MainActivity", "Auto-login failed")
                            scope.launch {
                                credentialManager.logout() // Clear invalid credentials
                                timetableCacheManager.clearCache() // Clear cached data on logout
                                gradesCacheManager.clearCache() // Clear grades cache on logout
                            }
                        }
                    }
                }
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0.dp) // Remove default content padding
        ) { innerPadding ->
            if (!isLoggedIn) {
                LoginScreen(
                    dualisService = dualisService,
                    credentialManager = credentialManager,
                    onLoginSuccess = {
                        isLoggedIn = true
                        // Request notification permission after successful manual login
                        shouldRequestNotificationPermission = true
                    },
                    modifier = Modifier
                        .padding(innerPadding)
                        .windowInsetsPadding(WindowInsets.systemBars)
                )
            } else {
                MainScreen(
                    dualisService = dualisService,
                    credentialManager = credentialManager,
                    timetableCacheManager = timetableCacheManager,
                    gradesCacheManager = gradesCacheManager,
                    notificationScheduler = notificationScheduler,
                    onLogout = {
                        isLoggedIn = false
                        shouldRequestNotificationPermission = false
                        // Stop notifications when user logs out
                        notificationScheduler.cancelPeriodicNotifications()
                        scope.launch {
                            timetableCacheManager.clearCache() // Clear cached data on logout
                            gradesCacheManager.clearCache() // Clear grades cache on logout
                            Log.d("MainActivity", "Cleared cache during logout")
                        }
                    },
                    currentTimetableScreenViewMode = currentTimetableScreenViewMode,
                    onTimetableScreenViewModeChanged = onTimetableScreenViewModeChanged,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}