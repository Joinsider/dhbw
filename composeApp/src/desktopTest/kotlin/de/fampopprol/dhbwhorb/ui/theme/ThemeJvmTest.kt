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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM/Desktop-specific theme tests.
 * Tests desktop-specific theming behavior (no dynamic colors).
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
    fun getColorScheme_lightMode_usesStaticLightColors() = runComposeUiTest {
        setContent {
            DHBWHorbTheme(darkTheme = false) {
                val colorScheme = MaterialTheme.colorScheme
                // Desktop always uses static colors (no Material You)
                assertEquals(LightColorScheme.primary, colorScheme.primary)
                assertEquals(LightColorScheme.background, colorScheme.background)
                assertEquals(LightColorScheme.surface, colorScheme.surface)
                assertEquals(LightColorScheme.onPrimary, colorScheme.onPrimary)
            }
        }
    }

    @Test
    fun getColorScheme_darkMode_usesStaticDarkColors() = runComposeUiTest {
        setContent {
            DHBWHorbTheme(darkTheme = true) {
                val colorScheme = MaterialTheme.colorScheme
                // Desktop always uses static colors (no Material You)
                assertEquals(DarkColorScheme.primary, colorScheme.primary)
                assertEquals(DarkColorScheme.background, colorScheme.background)
                assertEquals(DarkColorScheme.surface, colorScheme.surface)
                assertEquals(DarkColorScheme.onPrimary, colorScheme.onPrimary)
            }
        }
    }

    @Test
    fun dhbwHorbTheme_appliesCustomTypography() = runComposeUiTest {
        setContent {
            DHBWHorbTheme {
                val typography = MaterialTheme.typography
                assertEquals(Typography, typography)
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
        assertEquals(LightColorScheme.primary, lightPrimary)
        assertEquals(DarkColorScheme.primary, darkPrimary)
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

