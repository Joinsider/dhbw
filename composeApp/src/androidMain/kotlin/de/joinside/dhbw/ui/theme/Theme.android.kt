/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Android implementation: Returns Material You dynamic colors on Android 12+ (API 31+),
 * falls back to static color schemes on older versions.
 */
@Composable
actual fun getColorScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current

    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
}

/**
 * Android implementation: Configures status bar and navigation bar appearance
 * to match the theme (light/dark).
 */
@Composable
actual fun SystemAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    val colorScheme = getColorScheme(darkTheme)

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect

            // Set window background to match the theme background color
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())

            // Configure system bars for edge-to-edge
            WindowCompat.setDecorFitsSystemWindows(window, false)

            val insetsController = WindowCompat.getInsetsController(window, view)

            // Set status bar appearance
            insetsController.isAppearanceLightStatusBars = !darkTheme

            // Set navigation bar appearance (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}
