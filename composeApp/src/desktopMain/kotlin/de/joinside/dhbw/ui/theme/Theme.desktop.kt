/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * JVM/Desktop implementation: Uses static light/dark color schemes.
 * Material You is not available on desktop platforms.
 */
@Composable
actual fun getColorScheme(darkTheme: Boolean, useMaterialYou: Boolean): ColorScheme {
    return if (darkTheme) DarkColorScheme else LightColorScheme
}

/**
 * JVM/Desktop implementation: No system UI configuration needed.
 */
@Composable
actual fun SystemAppearance(darkTheme: Boolean, useMaterialYou: Boolean) {
    // No-op on desktop platforms
}

