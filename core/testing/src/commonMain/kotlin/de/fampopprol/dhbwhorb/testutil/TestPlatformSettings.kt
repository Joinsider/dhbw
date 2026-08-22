/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.testutil

import de.fampopprol.dhbwhorb.data.storage.settings.PlatformSettings

/** In-memory [PlatformSettings] for tests. */
class TestPlatformSettings : PlatformSettings {
    private val values = mutableMapOf<String, String>()

    override fun getStringOrNull(key: String): String? = values[key]

    override fun setString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
