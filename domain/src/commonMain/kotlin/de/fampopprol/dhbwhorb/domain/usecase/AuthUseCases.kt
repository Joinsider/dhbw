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
import de.fampopprol.dhbwhorb.domain.session.SessionDataCleaner
import io.github.aakira.napier.Napier

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

/**
 * End the session and wipe everything derived from it.
 *
 * "Everything" is wider than the repository can reach: [AuthRepository.logout] clears the session,
 * the credentials and the cached tables, and [cleaner] clears what the app handed to the system —
 * scheduled reminders, the widget, cached document files. Both run on every logout; the next
 * person to use this device must not find any of it.
 *
 * @param cleaner absent in the tests and on platforms that hand nothing to the system.
 */
class Logout(
    private val authRepository: AuthRepository,
    private val cleaner: SessionDataCleaner? = null
) {
    suspend operator fun invoke(): Outcome<Unit> {
        val result = authRepository.logout()

        // After the repository, so a cleaner that reads the database — the widget refresh does —
        // sees it already empty.
        val cleanerResult = cleaner?.let { clearWithCleaner(it) } ?: Outcome.Ok(Unit)

        // The session is gone either way. Report the repository's failure first: a cache that is
        // still on disk matters more than a widget that is still drawing.
        return if (result is Outcome.Err) result else cleanerResult
    }

    private suspend fun clearWithCleaner(cleaner: SessionDataCleaner): Outcome<Unit> = try {
        cleaner.clearSessionData()
        Outcome.Ok(Unit)
    } catch (e: Exception) {
        Napier.e("Could not clear session data on logout: ${e.message}", e, tag = "Logout")
        Outcome.Err(AppError.Storage("clearing session data on logout: ${e.message}"))
    }
}
