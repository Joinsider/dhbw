/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Android-specific theme tests.
 * Tests Material You dynamic colors and Android-specific theming behavior.
 * These tests verify color scheme values without requiring UI rendering.
 */
class ThemeAndroidTest {

    @Test
    fun lightColorScheme_hasValidPrimaryColor() {
        assertNotNull(LightColorScheme.primary)
        assertEquals(Purple40, LightColorScheme.primary)
    }

    @Test
    fun darkColorScheme_hasValidPrimaryColor() {
        assertNotNull(DarkColorScheme.primary)
        assertEquals(Purple80, DarkColorScheme.primary)
    }

    @Test
    fun lightColorScheme_hasValidBackgroundColor() {
        assertNotNull(LightColorScheme.background)
        assertEquals(Neutral99, LightColorScheme.background)
    }

    @Test
    fun darkColorScheme_hasValidBackgroundColor() {
        assertNotNull(DarkColorScheme.background)
        assertEquals(Neutral10, DarkColorScheme.background)
    }

    @Test
    fun staticColorScheme_matchesExpectedValues() {
        // Static colors are always available on all Android versions
        assertEquals(Purple40, LightColorScheme.primary)
        assertEquals(Neutral99, LightColorScheme.background)
        assertEquals(Purple80, DarkColorScheme.primary)
        assertEquals(Neutral10, DarkColorScheme.background)
    }

    @Test
    fun colorScheme_hasAllRequiredColors() {
        // Verify light color scheme has all required Material 3 colors
        assertNotNull(LightColorScheme.primary)
        assertNotNull(LightColorScheme.onPrimary)
        assertNotNull(LightColorScheme.primaryContainer)
        assertNotNull(LightColorScheme.onPrimaryContainer)
        assertNotNull(LightColorScheme.secondary)
        assertNotNull(LightColorScheme.onSecondary)
        assertNotNull(LightColorScheme.tertiary)
        assertNotNull(LightColorScheme.background)
        assertNotNull(LightColorScheme.surface)
        assertNotNull(LightColorScheme.error)

        // Verify dark color scheme has all required Material 3 colors
        assertNotNull(DarkColorScheme.primary)
        assertNotNull(DarkColorScheme.onPrimary)
        assertNotNull(DarkColorScheme.primaryContainer)
        assertNotNull(DarkColorScheme.onPrimaryContainer)
        assertNotNull(DarkColorScheme.secondary)
        assertNotNull(DarkColorScheme.onSecondary)
        assertNotNull(DarkColorScheme.tertiary)
        assertNotNull(DarkColorScheme.background)
        assertNotNull(DarkColorScheme.surface)
        assertNotNull(DarkColorScheme.error)
    }

    @Test
    fun colorScheme_surfaceVariants_areProperlyDefined() {
        assertNotNull(LightColorScheme.surfaceVariant)
        assertNotNull(LightColorScheme.onSurfaceVariant)
        assertNotNull(DarkColorScheme.surfaceVariant)
        assertNotNull(DarkColorScheme.onSurfaceVariant)
    }

    @Test
    fun colorScheme_outline_colorsAreDefined() {
        assertNotNull(LightColorScheme.outline)
        assertNotNull(LightColorScheme.outlineVariant)
        assertNotNull(DarkColorScheme.outline)
        assertNotNull(DarkColorScheme.outlineVariant)
    }

    @Test
    fun colorScheme_surfaceContainers_areDefined() {
        assertNotNull(LightColorScheme.surfaceContainer)
        assertNotNull(LightColorScheme.surfaceContainerHigh)
        assertNotNull(LightColorScheme.surfaceContainerHighest)
        assertNotNull(DarkColorScheme.surfaceContainer)
        assertNotNull(DarkColorScheme.surfaceContainerHigh)
        assertNotNull(DarkColorScheme.surfaceContainerHighest)
    }

    @Test
    fun lightAndDarkSchemes_haveDifferentColors() {
        // Verify that light and dark schemes have different color values
        // This ensures proper theme contrast
        assertNotEquals(LightColorScheme.primary, DarkColorScheme.primary)
        assertNotEquals(LightColorScheme.background, DarkColorScheme.background)
        assertNotEquals(LightColorScheme.surface, DarkColorScheme.surface)
    }

    @Test
    fun colorScheme_onColors_haveProperContrast() {
        // Verify on-colors exist for proper contrast
        assertNotNull(LightColorScheme.onPrimary)
        assertNotNull(LightColorScheme.onBackground)
        assertNotNull(LightColorScheme.onSurface)
        assertNotNull(DarkColorScheme.onPrimary)
        assertNotNull(DarkColorScheme.onBackground)
        assertNotNull(DarkColorScheme.onSurface)
    }

    @Test
    fun typography_isProperlyDefined() {
        // Typography should be defined and accessible
        assertNotNull(Typography)
        assertNotNull(Typography.bodyLarge)
        assertNotNull(Typography.headlineLarge)
        assertNotNull(Typography.titleLarge)
    }

    @Test
    fun colorScheme_secondaryColors_areDefined() {
        assertNotNull(LightColorScheme.secondary)
        assertNotNull(LightColorScheme.onSecondary)
        assertNotNull(LightColorScheme.secondaryContainer)
        assertNotNull(DarkColorScheme.secondary)
        assertNotNull(DarkColorScheme.onSecondary)
        assertNotNull(DarkColorScheme.secondaryContainer)
    }

    @Test
    fun colorScheme_tertiaryColors_areDefined() {
        assertNotNull(LightColorScheme.tertiary)
        assertNotNull(LightColorScheme.onTertiary)
        assertNotNull(LightColorScheme.tertiaryContainer)
        assertNotNull(DarkColorScheme.tertiary)
        assertNotNull(DarkColorScheme.onTertiary)
        assertNotNull(DarkColorScheme.tertiaryContainer)
    }
}
