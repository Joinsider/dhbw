/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.domain.repository.AuthRepository
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository

/** Log in with the credentials the user just typed. */
class LoginWithCredentials(private val authRepository: AuthRepository) {
    suspend operator fun invoke(username: String, password: String): Outcome<Session> =
        authRepository.login(username, password)
}

/**
 * Bring back the session from a previous app run.
 *
 * Returns [AppError.NoCredentials] when there is nothing stored — the caller shows the login
 * screen for that, which is not the same as a failed login attempt.
 */
class RestoreSession(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Outcome<Session> {
        sessionRepository.currentSession()?.let { return Outcome.Ok(it) }
        if (!sessionRepository.canAuthenticate()) return Outcome.Err(AppError.NoCredentials)
        return authRepository.reAuthenticate()
    }
}

/** End the session and wipe everything derived from it. */
class Logout(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): Outcome<Unit> = authRepository.logout()
}
