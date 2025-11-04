/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.util

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun getPlatform_returnsValidPlatformType() {
        val platform = getPlatform()
        assertNotNull(platform, "Platform should not be null")

        // Ensure the platform is one of the valid types
        val validPlatforms = setOf(
            PlatformType.ANDROID,
            PlatformType.IOS,
            PlatformType.DESKTOP,
            PlatformType.MACOS
        )
        assertTrue(
            platform in validPlatforms,
            "Platform should be one of the valid types: $validPlatforms, but was $platform"
        )
    }

    @Test
    fun isMobilePlatform_returnsCorrectValue() {
        val platform = getPlatform()
        val isMobile = isMobilePlatform()

        when (platform) {
            PlatformType.ANDROID, PlatformType.IOS -> {
                assertTrue(isMobile, "Platform $platform should be considered mobile")
            }
            PlatformType.DESKTOP, PlatformType.MACOS -> {
                assertTrue(!isMobile, "Platform $platform should not be considered mobile")
            }
        }
    }

    @Test
    fun platformType_hasAllEnumValues() {
        val values = PlatformType.entries
        assertTrue(values.contains(PlatformType.ANDROID), "ANDROID should be a valid platform type")
        assertTrue(values.contains(PlatformType.IOS), "IOS should be a valid platform type")
        assertTrue(values.contains(PlatformType.DESKTOP), "DESKTOP should be a valid platform type")
        assertTrue(values.contains(PlatformType.MACOS), "MACOS should be a valid platform type")
    }
}

