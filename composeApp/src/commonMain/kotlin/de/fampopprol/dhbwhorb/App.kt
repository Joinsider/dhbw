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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.presentation.app.AppIntent
import de.fampopprol.dhbwhorb.presentation.app.AppScreen
import de.fampopprol.dhbwhorb.presentation.app.AppStore
import de.fampopprol.dhbwhorb.presentation.auth.AuthEffect
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.settings.SettingsIntent
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.ui.pages.DocumentsPage
import de.fampopprol.dhbwhorb.ui.pages.GradesPage
import de.fampopprol.dhbwhorb.ui.pages.LoginPage
import de.fampopprol.dhbwhorb.ui.pages.SettingsPage
import de.fampopprol.dhbwhorb.ui.pages.TimetablePage
import de.fampopprol.dhbwhorb.ui.store.HandleEffects
import de.fampopprol.dhbwhorb.ui.store.collectState
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme
import de.fampopprol.dhbwhorb.ui.theme.LocalThemePrefs
import de.fampopprol.dhbwhorb.ui.theme.ThemePreferences as UIThemePreferences
import org.koin.compose.koinInject

/**
 * Root composable.
 *
 * Holds no state of its own any more: the session, the current screen and the settings all live in
 * stores. What used to be half a dozen `remember { mutableStateOf(...) }` here — each one a chance
 * for the composition and a `LaunchedEffect` to disagree — is now one [AppStore] state.
 */
@Composable
fun App() {
    val appStore: AppStore = koinInject()
    val authStore: AuthStore = koinInject()
    val settingsStore: SettingsStore = koinInject()

    val appState by appStore.collectState()
    val settings by settingsStore.collectState()

    // Both stores outlive the composition, so this only has to run once per process.
    LaunchedEffect(Unit) {
        appStore.dispatch(AppIntent.Started)
        settingsStore.dispatch(SettingsIntent.Load)
    }

    authStore.HandleEffects { effect ->
        when (effect) {
            AuthEffect.LoggedIn -> appStore.dispatch(AppIntent.LoggedIn)
        }
    }

    val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val seedColor = Color(settings.seedColor.toInt())

    val navigate: (AppScreen) -> Unit = { appStore.dispatch(AppIntent.Navigated(it)) }

    CompositionLocalProvider(
        LocalThemePrefs provides UIThemePreferences(
            darkMode = darkTheme,
            useMaterialYou = settings.materialYouEnabled
        )
    ) {
        DHBWHorbTheme(
            darkTheme = darkTheme,
            useMaterialYou = settings.materialYouEnabled,
            seedColor = seedColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("appContainer")
            ) {
                val pageModifier = Modifier.fillMaxSize().padding(top = 16.dp)

                when (appState.screen) {
                    AppScreen.LOGIN -> Column(
                        modifier = Modifier.fillMaxSize().safeContentPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // No onLoginSuccess callback: the login store emits AuthEffect.LoggedIn
                        // and the app store decides what that means for routing.
                        LoginPage()
                    }

                    AppScreen.TIMETABLE -> TimetablePage(
                        onNavigateToGrades = { navigate(AppScreen.GRADES) },
                        onNavigateToDocuments = { navigate(AppScreen.DOCUMENTS) },
                        onNavigateToSettings = { navigate(AppScreen.SETTINGS) },
                        isLoggedIn = appState.isLoggedIn,
                        modifier = pageModifier
                    )

                    AppScreen.GRADES -> GradesPage(
                        onNavigateToTimetable = { navigate(AppScreen.TIMETABLE) },
                        onNavigateToDocuments = { navigate(AppScreen.DOCUMENTS) },
                        onNavigateToSettings = { navigate(AppScreen.SETTINGS) },
                        isLoggedIn = appState.isLoggedIn,
                        modifier = pageModifier
                    )

                    AppScreen.DOCUMENTS -> DocumentsPage(
                        onNavigateToTimetable = { navigate(AppScreen.TIMETABLE) },
                        onNavigateToGrades = { navigate(AppScreen.GRADES) },
                        onNavigateToSettings = { navigate(AppScreen.SETTINGS) },
                        isLoggedIn = appState.isLoggedIn,
                        modifier = pageModifier
                    )

                    AppScreen.SETTINGS -> SettingsPage(
                        onNavigateToTimetable = { navigate(AppScreen.TIMETABLE) },
                        onNavigateToGrades = { navigate(AppScreen.GRADES) },
                        onNavigateToDocuments = { navigate(AppScreen.DOCUMENTS) },
                        onLogout = { appStore.dispatch(AppIntent.LogoutRequested) },
                        isLoggedIn = appState.isLoggedIn,
                        currentThemeMode = settings.themeMode,
                        onThemeModeChange = { settingsStore.dispatch(SettingsIntent.ThemeModeChanged(it)) },
                        materialYouEnabled = settings.materialYouEnabled,
                        onMaterialYouChange = { settingsStore.dispatch(SettingsIntent.MaterialYouChanged(it)) },
                        currentSeedColor = seedColor,
                        onSeedColorChange = {
                            settingsStore.dispatch(SettingsIntent.SeedColorChanged(it.toArgb().toLong()))
                        },
                        notificationsEnabled = settings.notificationsEnabled,
                        onNotificationsEnabledChange = {
                            settingsStore.dispatch(SettingsIntent.NotificationsChanged(it))
                        },
                        lectureAlertsEnabled = settings.lectureAlertsEnabled,
                        onLectureAlertsEnabledChange = {
                            settingsStore.dispatch(SettingsIntent.LectureAlertsChanged(it))
                        },
                        modifier = pageModifier
                    )
                }
            }
        }
    }
}
