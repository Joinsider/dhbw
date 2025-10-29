/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * macOS native implementation: Uses static light/dark color schemes.
 * Material You is not available on macOS native platforms.
 */
@Composable
actual fun getColorScheme(darkTheme: Boolean): ColorScheme {
    return if (darkTheme) DarkColorScheme else LightColorScheme
}

/**
 * macOS native implementation: No system UI configuration needed.
 */
@Composable
actual fun SystemAppearance(darkTheme: Boolean) {
    // No-op on macOS native platforms
}

