/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM/Desktop-specific theme tests.
 *
 * Desktop derives its colour scheme from the seed colour via MaterialKolor
 * (see `Theme.desktop.kt`), it does not use the static [LightColorScheme]/[DarkColorScheme].
 * These tests therefore assert the seed-derived behaviour, not fixed colour values.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeJvmTest {

    @Test
    fun dhbwHorbTheme_appliesMaterialTheme() = runComposeUiTest {
        setContent {
            DHBWHorbTheme {
                assertNotNull(MaterialTheme.colorScheme)
                assertNotNull(MaterialTheme.typography)
            }
        }
    }

    @Test
    fun getColorScheme_lightMode_derivesSchemeFromSeed() = runComposeUiTest {
        var defaultSeedPrimary: androidx.compose.ui.graphics.Color? = null
        var customSeedPrimary: androidx.compose.ui.graphics.Color? = null

        setContent {
            DHBWHorbTheme(darkTheme = false, seedColor = Purple40) {
                defaultSeedPrimary = MaterialTheme.colorScheme.primary
            }
        }
        waitForIdle()

        setContent {
            DHBWHorbTheme(darkTheme = false, seedColor = androidx.compose.ui.graphics.Color(0xFF1B6B2F)) {
                customSeedPrimary = MaterialTheme.colorScheme.primary
            }
        }
        waitForIdle()

        assertNotNull(defaultSeedPrimary)
        assertNotNull(customSeedPrimary)
        assertNotEquals(defaultSeedPrimary, customSeedPrimary, "Seed colour must drive the scheme")
    }

    @Test
    fun getColorScheme_darkMode_isDarkerThanLightMode() = runComposeUiTest {
        var lightBackground: androidx.compose.ui.graphics.Color? = null
        var darkBackground: androidx.compose.ui.graphics.Color? = null

        setContent {
            DHBWHorbTheme(darkTheme = false) { lightBackground = MaterialTheme.colorScheme.background }
        }
        waitForIdle()

        setContent {
            DHBWHorbTheme(darkTheme = true) { darkBackground = MaterialTheme.colorScheme.background }
        }
        waitForIdle()

        assertNotNull(lightBackground)
        assertNotNull(darkBackground)
        assertTrue(
            darkBackground!!.luminance() < lightBackground!!.luminance(),
            "Dark theme background must be darker than the light one"
        )
    }

    @Test
    fun dhbwHorbTheme_appliesCustomTypography() = runComposeUiTest {
        setContent {
            DHBWHorbTheme {
                val typography = MaterialTheme.typography
                // The theme applies myTypography(), not the static Typography value.
                assertEquals(myTypography(), typography)
                assertNotNull(typography.bodyLarge)
                assertNotNull(typography.headlineLarge)
                assertNotNull(typography.titleLarge)
            }
        }
    }

    @Test
    fun systemAppearance_isNoOpOnDesktop() = runComposeUiTest {
        // SystemAppearance should be a no-op on desktop
        // This test verifies it doesn't cause any issues
        setContent {
            DHBWHorbTheme(darkTheme = false) {
                assertNotNull(MaterialTheme.colorScheme)
            }
        }

        setContent {
            DHBWHorbTheme(darkTheme = true) {
                assertNotNull(MaterialTheme.colorScheme)
            }
        }
    }

    @Test
    fun dhbwHorbTheme_supportsThemeToggling() = runComposeUiTest {
        var isDark = false
        var lightPrimary: androidx.compose.ui.graphics.Color? = null
        var darkPrimary: androidx.compose.ui.graphics.Color? = null

        setContent {
            DHBWHorbTheme(darkTheme = isDark) {
                lightPrimary = MaterialTheme.colorScheme.primary
            }
        }

        waitForIdle()

        isDark = true
        setContent {
            DHBWHorbTheme(darkTheme = isDark) {
                darkPrimary = MaterialTheme.colorScheme.primary
            }
        }

        waitForIdle()

        assertNotNull(lightPrimary)
        assertNotNull(darkPrimary)
        assertNotEquals(lightPrimary, darkPrimary, "Toggling the theme must change the scheme")
    }

    @Test
    fun dhbwHorbTheme_canNestContent() = runComposeUiTest {
        var contentRendered = false

        setContent {
            DHBWHorbTheme {
                contentRendered = true
            }
        }

        waitForIdle()
        assertTrue(contentRendered)
    }

    @Test
    fun getColorScheme_materialYouDisabled_usesTheStaticPalette() = runComposeUiTest {
        var lightPrimary: androidx.compose.ui.graphics.Color? = null
        var darkPrimary: androidx.compose.ui.graphics.Color? = null

        setContent {
            DHBWHorbTheme(darkTheme = false, useMaterialYou = false) {
                lightPrimary = MaterialTheme.colorScheme.primary
            }
        }
        waitForIdle()

        setContent {
            DHBWHorbTheme(darkTheme = true, useMaterialYou = false) {
                darkPrimary = MaterialTheme.colorScheme.primary
            }
        }
        waitForIdle()

        assertEquals(LightColorScheme.primary, lightPrimary)
        assertEquals(DarkColorScheme.primary, darkPrimary)
    }

    @Test
    fun dhbwHorbTheme_providesAllRequiredColors() = runComposeUiTest {
        setContent {
            DHBWHorbTheme {
                val colorScheme = MaterialTheme.colorScheme
                // Verify all essential colors are defined
                assertNotNull(colorScheme.primary)
                assertNotNull(colorScheme.onPrimary)
                assertNotNull(colorScheme.primaryContainer)
                assertNotNull(colorScheme.secondary)
                assertNotNull(colorScheme.tertiary)
                assertNotNull(colorScheme.background)
                assertNotNull(colorScheme.surface)
                assertNotNull(colorScheme.error)
            }
        }
    }
}

