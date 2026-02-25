/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TypographyTest {

    @Test
    fun typography_isNotNull() {
        assertNotNull(Typography)
    }

    @Test
    fun bodyLarge_hasCorrectFontSize() {
        assertEquals(16.sp, Typography.bodyLarge.fontSize)
    }

    @Test
    fun bodyLarge_hasCorrectLineHeight() {
        assertEquals(24.sp, Typography.bodyLarge.lineHeight)
    }

    @Test
    fun bodyLarge_hasCorrectLetterSpacing() {
        assertEquals(0.5.sp, Typography.bodyLarge.letterSpacing)
    }

    @Test
    fun bodyLarge_hasCorrectFontWeight() {
        assertEquals(FontWeight.Normal, Typography.bodyLarge.fontWeight)
    }

    @Test
    fun bodyLarge_hasCorrectFontFamily() {
        assertEquals(FontFamily.Default, Typography.bodyLarge.fontFamily)
    }

    @Test
    fun allTypographyStyles_areNotNull() {
        assertNotNull(Typography.displayLarge)
        assertNotNull(Typography.displayMedium)
        assertNotNull(Typography.displaySmall)
        assertNotNull(Typography.headlineLarge)
        assertNotNull(Typography.headlineMedium)
        assertNotNull(Typography.headlineSmall)
        assertNotNull(Typography.titleLarge)
        assertNotNull(Typography.titleMedium)
        assertNotNull(Typography.titleSmall)
        assertNotNull(Typography.bodyLarge)
        assertNotNull(Typography.bodyMedium)
        assertNotNull(Typography.bodySmall)
        assertNotNull(Typography.labelLarge)
        assertNotNull(Typography.labelMedium)
        assertNotNull(Typography.labelSmall)
    }

    @Test
    fun typographyStyles_haveValidFontSizes() {
        // All font sizes should be positive
        val styles = listOf(
            Typography.displayLarge,
            Typography.displayMedium,
            Typography.displaySmall,
            Typography.headlineLarge,
            Typography.headlineMedium,
            Typography.headlineSmall,
            Typography.titleLarge,
            Typography.titleMedium,
            Typography.titleSmall,
            Typography.bodyLarge,
            Typography.bodyMedium,
            Typography.bodySmall,
            Typography.labelLarge,
            Typography.labelMedium,
            Typography.labelSmall
        )

        styles.forEach { style ->
            assertTrue(
                style.fontSize.value > 0f,
                "Font size should be positive, got ${style.fontSize}"
            )
        }
    }

    @Test
    fun displayStyles_areLargerThanBodyStyles() {
        // Display styles should have larger font sizes than body styles
        assertTrue(Typography.displayLarge.fontSize > Typography.bodyLarge.fontSize)
        assertTrue(Typography.displayMedium.fontSize > Typography.bodyMedium.fontSize)
        assertTrue(Typography.displaySmall.fontSize > Typography.bodySmall.fontSize)
    }

    @Test
    fun headlineStyles_areLargerThanBodyStyles() {
        // Headline styles should have larger font sizes than body styles
        assertTrue(Typography.headlineLarge.fontSize > Typography.bodyLarge.fontSize)
        assertTrue(Typography.headlineMedium.fontSize > Typography.bodyMedium.fontSize)
        assertTrue(Typography.headlineSmall.fontSize > Typography.bodySmall.fontSize)
    }

    @Test
    fun titleStyles_areLargerThanBodyStyles() {
        // Title styles should have larger font sizes than body styles
        assertTrue(Typography.titleLarge.fontSize > Typography.bodyLarge.fontSize)
        assertTrue(Typography.titleMedium.fontSize >= Typography.bodyMedium.fontSize)
    }

    @Test
    fun labelStyles_areSmallestStyles() {
        // Label styles are typically the smallest
        assertTrue(Typography.labelSmall.fontSize <= Typography.bodySmall.fontSize)
    }

    @Test
    fun lineHeight_isProportionalToFontSize() {
        // Line height should generally be larger than font size for readability
        assertTrue(Typography.bodyLarge.lineHeight > Typography.bodyLarge.fontSize)
        assertTrue(Typography.bodyMedium.lineHeight > Typography.bodyMedium.fontSize)
        assertTrue(Typography.bodySmall.lineHeight > Typography.bodySmall.fontSize)
    }
}
