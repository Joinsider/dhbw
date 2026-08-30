/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.settings

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Backed by the real `java.util.prefs` node for this package — a unique key per test and cleanup
 * afterwards keep it from leaking into a developer's or CI machine's actual preference store.
 */
class DesktopPlatformSettingsTest {

    private val settings = DesktopPlatformSettings()
    private val key = "test_key_${System.nanoTime()}"

    @AfterTest
    fun cleanup() {
        settings.remove(key)
    }

    @Test
    fun getStringOrNull_withNothingStored_returnsNull() {
        assertNull(settings.getStringOrNull(key))
    }

    @Test
    fun setString_thenRead_returnsWhatWasStored() {
        settings.setString(key, "dark")

        assertEquals("dark", settings.getStringOrNull(key))
    }

    @Test
    fun setString_overwritesThePreviousValue() {
        settings.setString(key, "dark")
        settings.setString(key, "light")

        assertEquals("light", settings.getStringOrNull(key))
    }

    @Test
    fun remove_clearsTheStoredValue() {
        settings.setString(key, "dark")

        settings.remove(key)

        assertNull(settings.getStringOrNull(key))
    }
}
