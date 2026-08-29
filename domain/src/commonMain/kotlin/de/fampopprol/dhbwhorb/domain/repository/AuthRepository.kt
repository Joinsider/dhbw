/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Session

/**
 * Everything that changes who is logged in.
 *
 * Reading the current session is [SessionRepository]'s job; this one only performs transitions.
 */
interface AuthRepository {

    /**
     * Log in and store the credentials for later re-authentication.
     *
     * Fails with [de.fampopprol.dhbwhorb.core.error.AppError.InvalidCredentials] when Dualis
     * rejects the pair — that is a different situation from being offline, and the UI has to say
     * so differently.
     */
    suspend fun login(username: String, password: String): Outcome<Session>

    /**
     * Log in again with the stored credentials.
     *
     * Concurrent callers share one attempt: three services used to run their own re-login when
     * several requests came back unauthorised at the same time.
     */
    suspend fun reAuthenticate(): Outcome<Session>

    /** End the session and drop everything derived from it. */
    suspend fun logout(): Outcome<Unit>
}
