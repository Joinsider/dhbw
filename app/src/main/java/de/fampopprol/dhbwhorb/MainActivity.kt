/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import de.fampopprol.dhbwhorb.data.cache.TimetableCacheManager
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.screen.*
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme
import de.fampopprol.dhbwhorb.widget.WidgetUpdateManager
import de.fampopprol.dhbwhorb.ui.components.CalendarViewMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Debug the cache contents first
        val cacheManager = TimetableCacheManager(this)
        cacheManager.debugCacheContents()

        // Manually refresh widgets on app start to ensure they show current data
        Log.d("MainActivity", "Manually refreshing widgets on app start")
        WidgetUpdateManager.updateAllWidgets(this)

        setContent {
            var currentTimetableScreenViewMode by remember { mutableStateOf(CalendarViewMode.WEEKLY) }

            val onTimetableScreenViewModeChanged: (CalendarViewMode) -> Unit = { newMode ->
                currentTimetableScreenViewMode = newMode
            }

            App(
                currentTimetableScreenViewMode = currentTimetableScreenViewMode,
                onTimetableScreenViewModeChanged = onTimetableScreenViewModeChanged
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    DHBWHorbTheme {
        LoginScreen(
            dualisService = DualisService(),
            credentialManager = CredentialManager(LocalContext.current),
            onLoginSuccess = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TimetableScreenPreview() {
    DHBWHorbTheme {
        TimetableScreen(
            dualisService = DualisService(),
            credentialManager = CredentialManager(LocalContext.current),
            timetableCacheManager = TimetableCacheManager(LocalContext.current),
            onLogout = {},
            currentViewMode = CalendarViewMode.WEEKLY, // Dummy value for preview
            onViewModeChanged = {} // Dummy value for preview
        )
    }
}