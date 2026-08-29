/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.credentials

import io.github.aakira.napier.Napier

private const val TAG = "CredentialsInstallGuard"

/**
 * Identifies the current installation, or `null` where reinstalling cannot be told from updating.
 *
 * The value has no meaning beyond "changed or not". It must survive an app update and must differ
 * after a delete-and-reinstall — which is exactly the property the guard below needs.
 */
expect fun currentInstallStamp(): String?

/**
 * Removes credentials that outlived the installation that stored them.
 *
 * Deleting an app is meant to take its account with it — that is what a user deleting an app
 * expects, and on both platforms it is the documented behaviour of app data. The Keychain is the
 * exception: it deliberately survives uninstall so that a reinstalled app can pick its account back
 * up, which means iOS reinstalls came back already logged in. Android has the same hole through a
 * different door (see the backup rules in the manifest).
 *
 * The mechanism is one stored value: the identity of the installation that wrote the credentials,
 * kept *with* the credentials. It changes when the app is reinstalled and does not change when the
 * app is updated, so a mismatch means exactly one thing.
 *
 * Three cases, and the third is the reason it is a stored stamp rather than a stored flag:
 *
 * * **Stamp matches** — same installation, keep everything. The ordinary case.
 * * **Stamp differs** — the credentials predate this installation. Clear them.
 * * **No stamp yet** — an installation from before this guard existed, or one that has never
 *   stored anything. Both are indistinguishable from here, and clearing would log out every
 *   existing user on the update that introduces this. So: write the stamp, keep the credentials.
 *   From the next reinstall on, the guard has what it needs.
 *
 * [currentInstallStamp] returning `null` (Android, Desktop, macOS) disables the guard, and that is
 * not a gap: there the credential store lives inside the app's own data and is already gone with
 * it. Guessing would be worse than doing nothing — a false positive here logs a user out for no
 * reason they can see.
 */
class CredentialsInstallGuard(
    private val storage: SecureStorageInterface,
    private val installStamp: () -> String? = ::currentInstallStamp,
) {
    companion object {
        /**
         * Lives in the credential store, not in settings: it has to be wiped by
         * [SecureStorageInterface.clear] together with what it describes.
         */
        const val INSTALL_STAMP_KEY = "_install_stamp"
    }

    fun purgeIfReinstalled() {
        val current = installStamp() ?: return
        val stored = storage.getString(INSTALL_STAMP_KEY, "")
        if (stored == current) return

        if (stored.isEmpty()) {
            Napier.d("First run with the install guard — adopting this installation", tag = TAG)
        } else {
            Napier.i("Credentials belong to a previous installation — clearing them", tag = TAG)
            storage.clear()
        }
        storage.setString(INSTALL_STAMP_KEY, current)
    }
}
