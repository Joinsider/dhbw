/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.presentation.app.AppIntent
import de.fampopprol.dhbwhorb.presentation.app.AppStore
import de.fampopprol.dhbwhorb.presentation.auth.AuthEffect
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.settings.SettingsIntent
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.ui.navigation.DhbwNavHost
import de.fampopprol.dhbwhorb.ui.pages.LoginPage
import de.fampopprol.dhbwhorb.ui.store.HandleEffects
import de.fampopprol.dhbwhorb.ui.store.collectState
import de.fampopprol.dhbwhorb.ui.theme.DHBWHorbTheme
import de.fampopprol.dhbwhorb.ui.theme.LocalThemePrefs
import de.fampopprol.dhbwhorb.ui.theme.ThemePreferences as UIThemePreferences
import org.koin.compose.koinInject

/**
 * Root composable: the theme, and the choice between the login screen and the app.
 *
 * Navigation inside the app belongs to [DhbwNavHost] — this used to be a `when` over an enum with
 * a callback per screen per page, which had no back stack and no way to express a destination's
 * arguments.
 *
 * @param navController injected so tests can drive navigation and assert where it landed.
 */
@Composable
fun App(navController: NavHostController = rememberNavController()) {
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

    CompositionLocalProvider(
        LocalThemePrefs provides UIThemePreferences(
            darkMode = darkTheme,
            useMaterialYou = settings.materialYouEnabled
        )
    ) {
        DHBWHorbTheme(
            darkTheme = darkTheme,
            useMaterialYou = settings.materialYouEnabled,
            seedColor = Color(settings.seedColor.toInt())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("appContainer")
            ) {
                when {
                    // The stored session has not been checked yet. Rendering either the login or
                    // the timetable here would be a guess, and it guessed wrong for anyone whose
                    // session had expired.
                    appState.isRestoring -> Box(
                        modifier = Modifier.fillMaxSize().testTag("appRestoring"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }

                    !appState.isLoggedIn -> Column(
                        modifier = Modifier.fillMaxSize().safeContentPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // No success callback: the auth store emits AuthEffect.LoggedIn and the
                        // app store decides what that means.
                        LoginPage()
                    }

                    else -> DhbwNavHost(
                        navController = navController,
                        onLogout = { appStore.dispatch(AppIntent.LogoutRequested) },
                        modifier = Modifier.fillMaxSize().padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
