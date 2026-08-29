/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.session

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.domain.model.Session
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Logs in again with the stored credentials, at most once at a time.
 *
 * The timetable, grade and document services each answer an unusable session by re-authenticating.
 * When several of their requests come back at once — which is the normal case, the app loads three
 * screens on start — the previous `isReAuthenticating: Boolean` guard let one of them proceed and
 * told the others "already in progress", so their requests failed even though a fresh session
 * arrived a moment later.
 *
 * Here the losers of the race await the winner's result instead of being turned away: one login
 * request reaches Dualis, and every caller gets its outcome.
 */
class ReAuthenticator(
    private val sessionManager: SessionManager,
    private val authenticationService: AuthenticationService
) {
    private companion object {
        const val TAG = "ReAuthenticator"
    }

    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<Outcome<Session>>? = null

    /**
     * Establish a session again.
     *
     * Returns [AppError.NoCredentials] when nothing is stored to log in with — the caller has to
     * send the user to the login screen, which is not the same as a failed attempt.
     */
    suspend fun reAuthenticate(): Outcome<Session> {
        val (deferred, isLeader) = mutex.withLock {
            val running = inFlight
            if (running != null) {
                Napier.d("Joining the re-authentication already in flight", tag = TAG)
                running to false
            } else {
                CompletableDeferred<Outcome<Session>>().also { inFlight = it } to true
            }
        }

        if (!isLeader) return deferred.await()

        val result = try {
            performLogin()
        } catch (e: Exception) {
            // The leader must never leave followers waiting on a deferred nobody completes, so
            // even a cancellation has to be published before it propagates.
            val failure = Outcome.Err(AppError.Unexpected("Re-authentication failed: ${e.message}"))
            deferred.complete(failure)
            mutex.withLock { inFlight = null }
            throw e
        }

        deferred.complete(result)
        mutex.withLock { inFlight = null }
        return result
    }

    private suspend fun performLogin(): Outcome<Session> {
        val credentials = sessionManager.getStoredCredentials()
        if (credentials == null) {
            Napier.w("No stored credentials for re-authentication", tag = TAG)
            return Outcome.Err(AppError.NoCredentials)
        }

        Napier.d("Re-authenticating", tag = TAG)
        // The stale token has to go first: a request racing this one would otherwise send it and
        // get another rejection back.
        sessionManager.clearAuthData()

        val (username, password) = credentials
        return authenticationService.login(username, password).also { outcome ->
            when (outcome) {
                is Outcome.Ok -> Napier.d("Re-authentication successful", tag = TAG)
                is Outcome.Err -> Napier.e("Re-authentication failed: ${outcome.error}", tag = TAG)
            }
        }
    }
}
