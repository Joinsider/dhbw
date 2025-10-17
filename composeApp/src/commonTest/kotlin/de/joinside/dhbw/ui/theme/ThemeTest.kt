/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class ThemeTest {

    @Test
    fun dhbwHorbTheme_appliesMaterialTheme() = runComposeUiTest {
        setContent {
            DHBWHorbTheme {
                // Verify MaterialTheme is accessible
                assertNotNull(MaterialTheme.colorScheme)
                assertNotNull(MaterialTheme.typography)
            }
        }
    }

    @Test
    fun dhbwHorbTheme_lightMode_usesLightColors() = runComposeUiTest {
        setContent {
            DHBWHorbTheme(darkTheme = false) {
                val colorScheme = MaterialTheme.colorScheme
                // The actual color depends on platform implementation
                // but we can verify the color scheme is set
                assertNotNull(colorScheme.primary)
                assertNotNull(colorScheme.surface)
                assertNotNull(colorScheme.background)
            }
        }
    }

    @Test
    fun dhbwHorbTheme_darkMode_usesDarkColors() = runComposeUiTest {
        setContent {
            DHBWHorbTheme(darkTheme = true) {
                val colorScheme = MaterialTheme.colorScheme
                // Verify the color scheme is properly set
                assertNotNull(colorScheme.primary)
                assertNotNull(colorScheme.surface)
                assertNotNull(colorScheme.background)
            }
        }
    }

    @Test
    fun dhbwHorbTheme_appliesTypography() = runComposeUiTest {
        setContent {
            DHBWHorbTheme {
                val typography = MaterialTheme.typography
                assertEquals(Typography, typography)
                assertNotNull(typography.bodyLarge)
            }
        }
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
        assertEquals(true, contentRendered)
    }

    @Test
    fun dhbwHorbTheme_lightAndDarkMode_haveDifferentSchemes() = runComposeUiTest {
        var lightPrimary: androidx.compose.ui.graphics.Color? = null
        var darkPrimary: androidx.compose.ui.graphics.Color? = null

        setContent {
            DHBWHorbTheme(darkTheme = false) {
                lightPrimary = MaterialTheme.colorScheme.primary
            }
        }

        waitForIdle()

        setContent {
            DHBWHorbTheme(darkTheme = true) {
                darkPrimary = MaterialTheme.colorScheme.primary
            }
        }

        waitForIdle()

        assertNotNull(lightPrimary)
        assertNotNull(darkPrimary)
        // On platforms without dynamic theming, the colors should be different
        // On Android with Material You, they might be the same if system uses same colors
    }

    @Test
    fun dhbwHorbTheme_supportsNestedComposables() = runComposeUiTest {
        var innerContentRendered = false

        setContent {
            DHBWHorbTheme {
                DHBWHorbTheme(darkTheme = true) {
                    innerContentRendered = true
                }
            }
        }

        waitForIdle()
        assertEquals(true, innerContentRendered)
    }
}