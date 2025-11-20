/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.aakira.napier.Napier

/**
 * Android implementation: Returns Material You dynamic colors on Android 12+ (API 31+)
 * when enabled, falls back to static color schemes when disabled or on older versions.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
actual fun getColorScheme(darkTheme: Boolean, useMaterialYou: Boolean): ColorScheme {
    val context = LocalContext.current
    val apiLevel = Build.VERSION.SDK_INT
    val isS = apiLevel >= Build.VERSION_CODES.S

    Napier.d("getColorScheme - darkTheme: $darkTheme, useMaterialYou: $useMaterialYou, API: $apiLevel, isS: $isS", tag = "Theme")

    return when {
        isS && useMaterialYou -> {
            Napier.d("Using Material You dynamic colors", tag = "Theme")
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> {
            Napier.d("Using static DarkColorScheme", tag = "Theme")
            DarkColorScheme
        }
        else -> {
            Napier.d("Using static LightColorScheme", tag = "Theme")
            LightColorScheme
        }
    }
}

/**
 * Android implementation: Configures status bar and navigation bar appearance
 * to match the theme (light/dark).
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
actual fun SystemAppearance(darkTheme: Boolean, useMaterialYou: Boolean) {
    val view = LocalView.current
    val colorScheme = getColorScheme(darkTheme, useMaterialYou)

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
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
