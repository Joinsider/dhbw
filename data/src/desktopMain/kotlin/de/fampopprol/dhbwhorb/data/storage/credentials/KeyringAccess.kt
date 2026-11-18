/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.credentials

import com.github.javakeyring.Keyring

/**
 * The three keyring operations [DesktopSecureStorage] needs.
 *
 * java-keyring's [Keyring] has a private constructor, so it cannot be replaced in a test. This
 * interface is the seam: production wires [JavaKeyringAccess], tests wire a fake. The methods
 * throw `PasswordAccessException` exactly as java-keyring does, because the storage's catch blocks
 * depend on that.
 */
internal interface KeyringAccess {
    fun getPassword(service: String, account: String): String?
    fun setPassword(service: String, account: String, password: String)
    fun deletePassword(service: String, account: String)
}

/** Delegates every call to a real java-keyring [Keyring]. */
internal class JavaKeyringAccess(private val keyring: Keyring) : KeyringAccess {
    override fun getPassword(service: String, account: String): String? =
        keyring.getPassword(service, account)

    override fun setPassword(service: String, account: String, password: String) =
        keyring.setPassword(service, account, password)

    override fun deletePassword(service: String, account: String) =
        keyring.deletePassword(service, account)
}
