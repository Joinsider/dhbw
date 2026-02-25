/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Common theme tests that work on all platforms.
 * These tests verify color scheme and typography values without requiring UI rendering.
 */
class ThemeTest {

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
    fun lightColorScheme_hasValidSurfaceColor() {
        assertNotNull(LightColorScheme.surface)
        assertEquals(Neutral99, LightColorScheme.surface)
    }

    @Test
    fun darkColorScheme_hasValidSurfaceColor() {
        assertNotNull(DarkColorScheme.surface)
        assertEquals(Neutral10, DarkColorScheme.surface)
    }

    @Test
    fun lightAndDarkColorSchemes_haveDifferentPrimaryColors() {
        assertNotEquals(LightColorScheme.primary, DarkColorScheme.primary)
    }

    @Test
    fun lightAndDarkColorSchemes_haveDifferentBackgroundColors() {
        assertNotEquals(LightColorScheme.background, DarkColorScheme.background)
    }

    @Test
    fun typography_hasValidBodyLarge() {
        assertNotNull(Typography.bodyLarge)
    }

    @Test
    fun typography_hasValidHeadlineLarge() {
        assertNotNull(Typography.headlineLarge)
    }

    @Test
    fun typography_hasValidTitleLarge() {
        assertNotNull(Typography.titleLarge)
    }

    @Test
    fun colorScheme_hasPrimaryContainer() {
        assertNotNull(LightColorScheme.primaryContainer)
        assertNotNull(DarkColorScheme.primaryContainer)
    }

    @Test
    fun colorScheme_hasSecondaryColors() {
        assertNotNull(LightColorScheme.secondary)
        assertNotNull(DarkColorScheme.secondary)
    }

    @Test
    fun colorScheme_hasTertiaryColors() {
        assertNotNull(LightColorScheme.tertiary)
        assertNotNull(DarkColorScheme.tertiary)
    }

    @Test
    fun colorScheme_hasErrorColors() {
        assertNotNull(LightColorScheme.error)
        assertNotNull(DarkColorScheme.error)
    }

    @Test
    fun colorScheme_hasOnPrimaryColors() {
        assertNotNull(LightColorScheme.onPrimary)
        assertNotNull(DarkColorScheme.onPrimary)
    }

    @Test
    fun colorScheme_hasOnBackgroundColors() {
        assertNotNull(LightColorScheme.onBackground)
        assertNotNull(DarkColorScheme.onBackground)
    }
}