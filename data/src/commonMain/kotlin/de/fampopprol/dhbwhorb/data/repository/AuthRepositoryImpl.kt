/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.domain.repository.AuthRepository
import io.github.aakira.napier.Napier

/**
 * Login, re-login and logout on top of the Dualis services.
 */
class AuthRepositoryImpl(
    private val authenticationService: AuthenticationService,
    private val reAuthenticator: ReAuthenticator,
    private val credentialsProvider: CredentialsStorageProvider,
    private val database: AppDatabase
) : AuthRepository {

    private companion object {
        const val TAG = "AuthRepositoryImpl"
    }

    override suspend fun login(username: String, password: String): Outcome<Session> =
        authenticationService.login(username, password)

    override suspend fun reAuthenticate(): Outcome<Session> = reAuthenticator.reAuthenticate()

    /**
     * Ends the session and removes everything derived from it.
     *
     * Cached lectures and grades have to go with it: leaving them behind would show the previous
     * user's data to the next one.
     */
    override suspend fun logout(): Outcome<Unit> {
        authenticationService.logout()
        credentialsProvider.clearCredentials()

        return try {
            database.clearAllData()
            Outcome.Ok(Unit)
        } catch (e: Exception) {
            // The session is already gone, so the user is logged out either way — but the caller
            // is told, because stale cached data on disk is worth surfacing.
            Napier.e("Could not clear cached data on logout: ${e.message}", e, tag = TAG)
            Outcome.Err(AppError.Storage("clearing cached data on logout: ${e.message}"))
        }
    }
}
