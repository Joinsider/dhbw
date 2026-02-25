/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

/**
 * JVM/Desktop implementation: Uses MaterialKolor to generate dynamic schemes.
 */
@Composable
actual fun getColorScheme(darkTheme: Boolean, useMaterialYou: Boolean, seedColor: Color): ColorScheme {
    return dynamicColorScheme(seedColor, darkTheme)
}

/**
 * JVM/Desktop implementation: No system UI configuration needed.
 */
@Composable
actual fun SystemAppearance(darkTheme: Boolean, useMaterialYou: Boolean, seedColor: Color) {
    // No-op on desktop platforms
}