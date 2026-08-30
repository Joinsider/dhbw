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
 * macOS native implementation: Uses MaterialKolor to generate dynamic schemes.
 *
 * `useMaterialYou` is unused here — macOS has no system dynamic-color source to toggle between,
 * so it always uses the seed-derived scheme — but the parameter is required to satisfy the
 * `expect fun` signature shared with the Android actual, which does use it.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
actual fun getColorScheme(darkTheme: Boolean, useMaterialYou: Boolean, seedColor: Color): ColorScheme {
    return dynamicColorScheme(seedColor, darkTheme)
}

/**
 * macOS native implementation: No system UI configuration needed.
 *
 * Parameters are unused here but required to satisfy the `expect fun` signature shared with the
 * Android actual, which does use all three.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
actual fun SystemAppearance(darkTheme: Boolean, useMaterialYou: Boolean, seedColor: Color) {
    // No-op on macOS native platforms
}