/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.preferences

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeModeTest {

    @Test
    fun fromString_roundTripsEveryKnownValue() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromString(mode.name))
        }
    }

    @Test
    fun fromString_fallsBackToSystemForAnUnknownValue() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("NOT_A_REAL_MODE"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString(""))
    }
}
