/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.testutil

import de.joinside.dhbw.data.storage.credentials.CredentialsStorageProvider

/**
 * Mock credentials storage provider for testing.
 * Stores credentials in memory only.
 */
class MockCredentialsProvider : CredentialsStorageProvider(TestSecureStorage()) {

    private var storedUsername: String = ""
    private var storedPassword: String = ""

    override fun storeCredentials(username: String, password: String) {
        storedUsername = username
        storedPassword = password
    }

    override fun getUsername(): String {
        return storedUsername
    }

    override fun getPassword(): String {
        return storedPassword
    }

    override fun clearCredentials() {
        storedUsername = ""
        storedPassword = ""
    }
}


