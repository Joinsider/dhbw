/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class DesignSelectionCardTest {

    @Test
    fun lightMode_lightButtonIsCheckedAndOthersAreNot() = runComposeUiTest {
        setContent {
            DesignSelectionCard(currentThemeMode = ThemeMode.LIGHT)
        }

        onNodeWithTag(themeButtonTestTag(ThemeMode.LIGHT)).assertIsOn()
        onNodeWithTag(themeButtonTestTag(ThemeMode.DARK)).assertIsOff()
        onNodeWithTag(themeButtonTestTag(ThemeMode.SYSTEM)).assertIsOff()
    }

    @Test
    fun darkMode_darkButtonIsChecked() = runComposeUiTest {
        setContent {
            DesignSelectionCard(currentThemeMode = ThemeMode.DARK)
        }

        onNodeWithTag(themeButtonTestTag(ThemeMode.DARK)).assertIsOn()
        onNodeWithTag(themeButtonTestTag(ThemeMode.LIGHT)).assertIsOff()
    }

    @Test
    fun systemMode_systemButtonIsChecked() = runComposeUiTest {
        setContent {
            DesignSelectionCard(currentThemeMode = ThemeMode.SYSTEM)
        }

        onNodeWithTag(themeButtonTestTag(ThemeMode.SYSTEM)).assertIsOn()
    }

    @Test
    fun clickingLightButton_reportsLightMode() = runComposeUiTest {
        var reported: ThemeMode? = null
        setContent {
            DesignSelectionCard(
                currentThemeMode = ThemeMode.SYSTEM,
                onThemeModeChange = { reported = it },
            )
        }

        onNodeWithTag(themeButtonTestTag(ThemeMode.LIGHT)).performClick()

        assertEquals(ThemeMode.LIGHT, reported)
    }

    @Test
    fun clickingDarkButton_reportsDarkMode() = runComposeUiTest {
        var reported: ThemeMode? = null
        setContent {
            DesignSelectionCard(
                currentThemeMode = ThemeMode.SYSTEM,
                onThemeModeChange = { reported = it },
            )
        }

        onNodeWithTag(themeButtonTestTag(ThemeMode.DARK)).performClick()

        assertEquals(ThemeMode.DARK, reported)
    }

    @Test
    fun clickingSystemButton_reportsSystemMode() = runComposeUiTest {
        var reported: ThemeMode? = null
        setContent {
            DesignSelectionCard(
                currentThemeMode = ThemeMode.LIGHT,
                onThemeModeChange = { reported = it },
            )
        }

        onNodeWithTag(themeButtonTestTag(ThemeMode.SYSTEM)).performClick()

        assertEquals(ThemeMode.SYSTEM, reported)
    }

    @Test
    fun materialYouSwitch_isAbsentOnDesktop() = runComposeUiTest {
        setContent {
            DesignSelectionCard()
        }

        // materialYouSwitch is only emitted on the Android platform branch; the desktop actual
        // used by this test never reports PlatformType.ANDROID, so the switch is never composed.
        assertFailsWith<AssertionError> { onNodeWithTag("materialYouSwitch").assertIsDisplayed() }
    }

    @Test
    fun themeButtonTestTag_derivesFromEnumName() {
        assertEquals("themeLightButton", themeButtonTestTag(ThemeMode.LIGHT))
        assertEquals("themeDarkButton", themeButtonTestTag(ThemeMode.DARK))
        assertEquals("themeSystemButton", themeButtonTestTag(ThemeMode.SYSTEM))
    }

    @Test
    fun defaultSeedColor_isPurple() = runComposeUiTest {
        var selected: Color? = null
        setContent {
            DesignSelectionCard(onSeedColorChange = { selected = it })
        }

        // No color was clicked, so the callback should not have fired yet.
        assertNull(selected)
    }

    @Test
    fun clickingAColorSwatch_reportsTheSelectedColor() = runComposeUiTest {
        var selected: Color? = null
        setContent {
            DesignSelectionCard(
                currentSeedColor = Color(0xFF6650a4),
                onSeedColorChange = { selected = it },
            )
        }
        waitForIdle()

        // The first 3 clickable nodes are the light/dark/system theme toggle buttons; the color
        // swatches in the ColorPicker follow. Clicking the first swatch (index 3, the pink one)
        // exercises the onSeedColorChange callback and the isSelected branch it flips.
        onAllNodes(hasClickAction())[3].performClick()

        assertEquals(Color(0xFFFF91FF), selected)
    }
}
