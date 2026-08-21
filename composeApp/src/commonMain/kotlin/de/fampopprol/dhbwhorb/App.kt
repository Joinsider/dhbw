/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.usecase.LoginWithCredentials
import de.fampopprol.dhbwhorb.domain.usecase.Logout
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.ui.pages.DocumentsPage
import de.fampopprol.dhbwhorb.ui.pages.GradesPage
import de.fampopprol.dhbwhorb.ui.pages.LoginPage
import de.fampopprol.dhbwhorb.ui.pages.SettingsPage
import de.fampopprol.dhbwhorb.ui.pages.TimetablePage
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme
import de.fampopprol.dhbwhorb.ui.theme.LocalThemePrefs
import de.fampopprol.dhbwhorb.ui.theme.ThemePreferences as UIThemePreferences
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class AppScreen {
    LOGIN,
    TIMETABLE,
    GRADES,
    DOCUMENTS,
    SETTINGS
}

/**
 * Root composable.
 *
 * Takes no dependencies: everything is resolved from Koin, which the platform entry point started
 * before any composition. There is no initialising state to render around any more — the graph is
 * ready by the time this runs.
 */
@Composable
fun App() {
    val themePreferences: ThemePreferences = koinInject()
    val notificationPreferences: NotificationPreferencesInteractor = koinInject()
    val sessionRepository: SessionRepository = koinInject()
    val loginWithCredentials: LoginWithCredentials = koinInject()
    val logout: Logout = koinInject()

    var themeMode by remember { mutableStateOf(themePreferences.getThemeMode()) }
    var materialYouEnabled by remember { mutableStateOf(themePreferences.getMaterialYouEnabled()) }
    var seedColorLong by remember { mutableStateOf(themePreferences.getCustomColor()) }
    val seedColor = remember(seedColorLong) { Color(seedColorLong.toInt()) }

    val notificationsEnabled by notificationPreferences.notificationsEnabled.collectAsState()
    val lectureAlertsEnabled by notificationPreferences.lectureAlertsEnabled.collectAsState()

    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    var isLoggedIn by remember { mutableStateOf(sessionRepository.isLoggedIn()) }
    var currentScreen by remember {
        mutableStateOf(if (isLoggedIn) AppScreen.TIMETABLE else AppScreen.LOGIN)
    }

    // A stored session can be present but stale; re-checking once on start keeps the first screen
    // honest without blocking the UI on a network round-trip.
    LaunchedEffect(Unit) {
        val authenticated = sessionRepository.isLoggedIn()
        if (authenticated != isLoggedIn) {
            isLoggedIn = authenticated
            currentScreen = if (authenticated) AppScreen.TIMETABLE else AppScreen.LOGIN
        }
    }

    val scope = rememberCoroutineScope()
    val handleLogout: () -> Unit = {
        scope.launch { logout() }
        isLoggedIn = false
        currentScreen = AppScreen.LOGIN
    }

    CompositionLocalProvider(
        LocalThemePrefs provides UIThemePreferences(
            darkMode = darkTheme,
            useMaterialYou = materialYouEnabled
        )
    ) {
        DHBWHorbTheme(
            darkTheme = darkTheme,
            useMaterialYou = materialYouEnabled,
            seedColor = seedColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("appContainer")
            ) {
                val pageModifier = Modifier.fillMaxSize().padding(top = 16.dp)

                when (currentScreen) {
                    AppScreen.LOGIN -> Column(
                        modifier = Modifier.fillMaxSize().safeContentPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LoginPage(
                            onLoginSuccess = {
                                isLoggedIn = true
                                currentScreen = AppScreen.TIMETABLE
                            },
                            login = loginWithCredentials
                        )
                    }

                    AppScreen.TIMETABLE -> TimetablePage(
                        onNavigateToGrades = { currentScreen = AppScreen.GRADES },
                        onNavigateToDocuments = { currentScreen = AppScreen.DOCUMENTS },
                        onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                        isLoggedIn = isLoggedIn,
                        modifier = pageModifier
                    )

                    AppScreen.GRADES -> GradesPage(
                        onNavigateToTimetable = { currentScreen = AppScreen.TIMETABLE },
                        onNavigateToDocuments = { currentScreen = AppScreen.DOCUMENTS },
                        onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                        isLoggedIn = isLoggedIn,
                        modifier = pageModifier
                    )

                    AppScreen.DOCUMENTS -> DocumentsPage(
                        onNavigateToTimetable = { currentScreen = AppScreen.TIMETABLE },
                        onNavigateToGrades = { currentScreen = AppScreen.GRADES },
                        onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                        isLoggedIn = isLoggedIn,
                        modifier = pageModifier
                    )

                    AppScreen.SETTINGS -> SettingsPage(
                        onNavigateToTimetable = { currentScreen = AppScreen.TIMETABLE },
                        onNavigateToGrades = { currentScreen = AppScreen.GRADES },
                        onNavigateToDocuments = { currentScreen = AppScreen.DOCUMENTS },
                        onLogout = handleLogout,
                        isLoggedIn = isLoggedIn,
                        currentThemeMode = themeMode,
                        onThemeModeChange = { newMode ->
                            themeMode = newMode
                            themePreferences.setThemeMode(newMode)
                        },
                        materialYouEnabled = materialYouEnabled,
                        onMaterialYouChange = { enabled ->
                            materialYouEnabled = enabled
                            themePreferences.setMaterialYouEnabled(enabled)
                        },
                        currentSeedColor = seedColor,
                        onSeedColorChange = { newColor ->
                            seedColorLong = newColor.toArgb().toLong()
                            themePreferences.setCustomColor(seedColorLong)
                        },
                        notificationsEnabled = notificationsEnabled,
                        onNotificationsEnabledChange = notificationPreferences::setNotificationsEnabled,
                        lectureAlertsEnabled = lectureAlertsEnabled,
                        onLectureAlertsEnabledChange = notificationPreferences::setLectureAlertsEnabled,
                        modifier = pageModifier
                    )
                }
            }
        }
    }
}
