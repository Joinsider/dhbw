/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.testutil

import de.joinside.dhbw.data.storage.credentials.SecureStorageInterface

/**
 * In-memory implementation of SecureStorageInterface for testing.
 * Shared across all test mocks.
 */
class TestSecureStorage : SecureStorageInterface {
    private val storage = mutableMapOf<String, String>()

    override fun setString(key: String, value: String) {
        storage[key] = value
    }

    override fun getString(key: String, defaultValue: String): String {
        return storage[key] ?: defaultValue
    }

    override fun remove(key: String) {
        storage.remove(key)
    }

    override fun clear() {
        storage.clear()
    }
}

