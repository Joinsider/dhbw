/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services

import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import io.github.aakira.napier.Napier

/**
 * Ends the session and removes everything derived from it.
 *
 * All three steps belong together: leaving cached lectures or grades behind after a logout would
 * show the previous user's data to the next one. Previously this lived in the root composable,
 * which is why the UI layer needed a database handle at all.
 */
class LogoutUseCase(
    private val sessionManager: SessionManager,
    private val credentialsProvider: CredentialsStorageProvider,
    private val database: AppDatabase
) {
    suspend operator fun invoke() {
        sessionManager.logout()
        credentialsProvider.clearCredentials()
        try {
            database.clearAllData()
        } catch (e: Exception) {
            // The session is already gone; a failed cache wipe must not leave the user logged in.
            Napier.e("Could not clear cached data on logout: ${e.message}", e, tag = TAG)
        }
    }

    private companion object {
        const val TAG = "LogoutUseCase"
    }
}
