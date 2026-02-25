/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Enhanced Dark Color Scheme with better Material You compatibility
val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Neutral10,
    primaryContainer = Purple40,
    onPrimaryContainer = Purple80,
    secondary = PurpleGrey80,
    onSecondary = Neutral10,
    secondaryContainer = PurpleGrey40,
    onSecondaryContainer = PurpleGrey80,
    tertiary = Pink80,
    onTertiary = Neutral10,
    tertiaryContainer = Pink40,
    onTertiaryContainer = Pink80,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant60,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant30,
    surfaceContainer = Neutral20,
    surfaceContainerHigh = Neutral20,
    surfaceContainerHighest = Neutral20
)

// Enhanced Light Color Scheme with better Material You compatibility
val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Neutral99,
    primaryContainer = Purple80,
    onPrimaryContainer = Purple40,
    secondary = PurpleGrey40,
    onSecondary = Neutral99,
    secondaryContainer = PurpleGrey80,
    onSecondaryContainer = PurpleGrey40,
    tertiary = Pink40,
    onTertiary = Neutral99,
    tertiaryContainer = Pink80,
    onTertiaryContainer = Pink40,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant90,
    surfaceContainer = Neutral95,
    surfaceContainerHigh = Neutral95,
    surfaceContainerHighest = Neutral90
)

/**
 * Platform-specific color scheme provider.
 * On Android: Uses Material You dynamic colors on Android 12+ (when supported)
 * On other platforms: Uses static color schemes
 */
@Composable
expect fun getColorScheme(darkTheme: Boolean, useMaterialYou: Boolean = true, seedColor: androidx.compose.ui.graphics.Color): ColorScheme

/**
 * Platform-specific system UI configuration.
 * On Android: Configures status bar and navigation bar appearance
 * On other platforms: No-op
 */
@Composable
expect fun SystemAppearance(darkTheme: Boolean, useMaterialYou: Boolean = true, seedColor: androidx.compose.ui.graphics.Color)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DHBWHorbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useMaterialYou: Boolean = true,
    seedColor: androidx.compose.ui.graphics.Color = Purple40,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(darkTheme, useMaterialYou, seedColor)

    SystemAppearance(darkTheme, useMaterialYou, seedColor)

    // 2. Define the MotionScheme
    // Options: MotionScheme.expressive() OR MotionScheme.standard()
    val motionScheme = MotionScheme.expressive()

    MaterialExpressiveTheme (
        colorScheme = colorScheme,
        typography = myTypography(),
        motionScheme = motionScheme,
        shapes = shapes,
        content = content
    )
}
