/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ColorSchemeTest {

    @Test
    fun darkColorScheme_hasCorrectPrimaryColors() {
        assertEquals(Purple80, DarkColorScheme.primary)
        assertEquals(Neutral10, DarkColorScheme.onPrimary)
        assertEquals(Purple40, DarkColorScheme.primaryContainer)
        assertEquals(Purple80, DarkColorScheme.onPrimaryContainer)
    }

    @Test
    fun darkColorScheme_hasCorrectSecondaryColors() {
        assertEquals(PurpleGrey80, DarkColorScheme.secondary)
        assertEquals(Neutral10, DarkColorScheme.onSecondary)
        assertEquals(PurpleGrey40, DarkColorScheme.secondaryContainer)
        assertEquals(PurpleGrey80, DarkColorScheme.onSecondaryContainer)
    }

    @Test
    fun darkColorScheme_hasCorrectTertiaryColors() {
        assertEquals(Pink80, DarkColorScheme.tertiary)
        assertEquals(Neutral10, DarkColorScheme.onTertiary)
        assertEquals(Pink40, DarkColorScheme.tertiaryContainer)
        assertEquals(Pink80, DarkColorScheme.onTertiaryContainer)
    }

    @Test
    fun darkColorScheme_hasCorrectSurfaceColors() {
        assertEquals(Neutral10, DarkColorScheme.background)
        assertEquals(Neutral90, DarkColorScheme.onBackground)
        assertEquals(Neutral10, DarkColorScheme.surface)
        assertEquals(Neutral90, DarkColorScheme.onSurface)
    }

    @Test
    fun darkColorScheme_hasCorrectVariantColors() {
        assertEquals(NeutralVariant30, DarkColorScheme.surfaceVariant)
        assertEquals(NeutralVariant60, DarkColorScheme.onSurfaceVariant)
        assertEquals(NeutralVariant50, DarkColorScheme.outline)
        assertEquals(NeutralVariant30, DarkColorScheme.outlineVariant)
    }

    @Test
    fun lightColorScheme_hasCorrectPrimaryColors() {
        assertEquals(Purple40, LightColorScheme.primary)
        assertEquals(Neutral99, LightColorScheme.onPrimary)
        assertEquals(Purple80, LightColorScheme.primaryContainer)
        assertEquals(Purple40, LightColorScheme.onPrimaryContainer)
    }

    @Test
    fun lightColorScheme_hasCorrectSecondaryColors() {
        assertEquals(PurpleGrey40, LightColorScheme.secondary)
        assertEquals(Neutral99, LightColorScheme.onSecondary)
        assertEquals(PurpleGrey80, LightColorScheme.secondaryContainer)
        assertEquals(PurpleGrey40, LightColorScheme.onSecondaryContainer)
    }

    @Test
    fun lightColorScheme_hasCorrectTertiaryColors() {
        assertEquals(Pink40, LightColorScheme.tertiary)
        assertEquals(Neutral99, LightColorScheme.onTertiary)
        assertEquals(Pink80, LightColorScheme.tertiaryContainer)
        assertEquals(Pink40, LightColorScheme.onTertiaryContainer)
    }

    @Test
    fun lightColorScheme_hasCorrectSurfaceColors() {
        assertEquals(Neutral99, LightColorScheme.background)
        assertEquals(Neutral10, LightColorScheme.onBackground)
        assertEquals(Neutral99, LightColorScheme.surface)
        assertEquals(Neutral10, LightColorScheme.onSurface)
    }

    @Test
    fun lightColorScheme_hasCorrectVariantColors() {
        assertEquals(NeutralVariant90, LightColorScheme.surfaceVariant)
        assertEquals(NeutralVariant30, LightColorScheme.onSurfaceVariant)
        assertEquals(NeutralVariant50, LightColorScheme.outline)
        assertEquals(NeutralVariant90, LightColorScheme.outlineVariant)
    }

    @Test
    fun darkAndLightSchemes_haveDifferentBackgrounds() {
        assertNotEquals(DarkColorScheme.background, LightColorScheme.background)
        assertNotEquals(DarkColorScheme.surface, LightColorScheme.surface)
    }

    @Test
    fun darkAndLightSchemes_haveDifferentPrimaryColors() {
        assertNotEquals(DarkColorScheme.primary, LightColorScheme.primary)
        assertNotEquals(DarkColorScheme.onPrimary, LightColorScheme.onPrimary)
    }

    @Test
    fun colorSchemes_provideGoodContrast() {
        // Dark theme should have light text on dark backgrounds
        assertTrue(
            DarkColorScheme.onBackground.luminance() > DarkColorScheme.background.luminance(),
            "Dark theme should have lighter text than background"
        )

        // Light theme should have dark text on light backgrounds
        assertTrue(
            LightColorScheme.background.luminance() > LightColorScheme.onBackground.luminance(),
            "Light theme should have darker text than background"
        )
    }

    @Test
    fun primaryColors_areNotFullyTransparent() {
        assertTrue(DarkColorScheme.primary.alpha > 0f)
        assertTrue(LightColorScheme.primary.alpha > 0f)
    }

    @Test
    fun surfaceColors_areNotFullyTransparent() {
        assertTrue(DarkColorScheme.surface.alpha > 0f)
        assertTrue(LightColorScheme.surface.alpha > 0f)
    }
}

// Extension function for luminance calculation
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
