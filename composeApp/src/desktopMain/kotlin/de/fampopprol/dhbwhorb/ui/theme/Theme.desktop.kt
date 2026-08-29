/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

/**
 * Desktop has no system accent colour to read, so "Material You" here means the scheme generated
 * from the seed colour the user picked; switching it off gives the app's own static palette.
 *
 * The flag used to be ignored entirely, which made the setting visible in the UI and inert.
 */
@Composable
actual fun getColorScheme(darkTheme: Boolean, useMaterialYou: Boolean, seedColor: Color): ColorScheme {
    if (!useMaterialYou) {
        return if (darkTheme) DarkColorScheme else LightColorScheme
    }
    return dynamicColorScheme(seedColor, darkTheme)
}

/**
 * JVM/Desktop implementation: No system UI configuration needed.
 */
@Composable
actual fun SystemAppearance(darkTheme: Boolean, useMaterialYou: Boolean, seedColor: Color) {
    // No-op on desktop platforms
}