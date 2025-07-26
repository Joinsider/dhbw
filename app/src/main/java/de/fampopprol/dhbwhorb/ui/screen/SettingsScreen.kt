/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.notification.NotificationPreferencesManager
import de.fampopprol.dhbwhorb.data.notification.NotificationScheduler
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.data.theme.ThemePreferencesManager
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.ClassReminderSettingsSection
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.DemoModeTestSection
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.GeneralSettingsSection
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.LogoutSection
import de.fampopprol.dhbwhorb.ui.screen.settingsScreen.NotificationSettingsSection

@Composable
fun NotificationSettingsScreen(
    dualisService: DualisService,
    notificationScheduler: NotificationScheduler,
    notificationPreferencesManager: NotificationPreferencesManager,
    credentialManager: CredentialManager,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themePreferencesManager = remember { ThemePreferencesManager(context) }

    val notificationsEnabled by notificationPreferencesManager.notificationsEnabled.collectAsState(initial = true)
    val isDemoMode = dualisService.isDemoMode()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // General Settings Section
        GeneralSettingsSection(
            themePreferencesManager = themePreferencesManager
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Notification Settings Section
        NotificationSettingsSection(
            notificationPreferencesManager = notificationPreferencesManager,
            notificationScheduler = notificationScheduler
        )

        // Class Reminder Settings Section
        ClassReminderSettingsSection(
            notificationPreferencesManager = notificationPreferencesManager,
            notificationScheduler = notificationScheduler,
            notificationsEnabled = notificationsEnabled
        )

        // Demo Mode Test Section (only show in demo mode)
        if (isDemoMode) {
            DemoModeTestSection()
        }

        // Logout Section
        LogoutSection(
            credentialManager = credentialManager,
            onLogout = onLogout
        )
    }
}
