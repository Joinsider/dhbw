/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.credentials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard decides whether a user stays logged in across an app update and whether they are
 * logged out by a reinstall. Both directions are worth a test: one of them is a security promise,
 * the other is the thing that makes the promise bearable.
 */
class CredentialsInstallGuardTest {

    private val storage = FakeSecureStorage()

    private fun guard(stamp: String?) =
        CredentialsInstallGuard(storage = storage, installStamp = { stamp })

    private fun storeCredentials() {
        storage.setString("username", "someone@hb.dhbw-stuttgart.de")
        storage.setString("password", "secret")
    }

    private fun hasCredentials() = storage.getString("username", "").isNotEmpty()

    @Test
    fun `keeps credentials when the installation is unchanged`() {
        guard("install-1").purgeIfReinstalled()
        storeCredentials()

        guard("install-1").purgeIfReinstalled()

        assertTrue(hasCredentials(), "an update must not log the user out")
    }

    @Test
    fun `clears credentials when the installation is a different one`() {
        guard("install-1").purgeIfReinstalled()
        storeCredentials()

        guard("install-2").purgeIfReinstalled()

        assertTrue(!hasCredentials(), "a reinstall must not inherit the previous account")
    }

    @Test
    fun `adopts an installation that predates the guard instead of clearing it`() {
        // No stamp has ever been written — the state of every existing install on the update that
        // introduces this. Clearing here would log out everyone once, for nothing.
        storeCredentials()

        guard("install-1").purgeIfReinstalled()

        assertTrue(hasCredentials(), "an install from before the guard must be adopted, not wiped")
        assertEquals("install-1", storage.getString(CredentialsInstallGuard.INSTALL_STAMP_KEY, ""))
    }

    @Test
    fun `stamps the new installation after clearing so it only happens once`() {
        guard("install-1").purgeIfReinstalled()
        storeCredentials()

        guard("install-2").purgeIfReinstalled()
        storeCredentials()
        guard("install-2").purgeIfReinstalled()

        assertTrue(hasCredentials(), "the second run on the same install must leave things alone")
    }

    @Test
    fun `does nothing at all where the platform cannot tell installs apart`() {
        storeCredentials()

        guard(null).purgeIfReinstalled()

        assertTrue(hasCredentials())
        assertEquals("", storage.getString(CredentialsInstallGuard.INSTALL_STAMP_KEY, ""))
    }
}
