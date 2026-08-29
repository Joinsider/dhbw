/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository

/** Reads the stored session out of [SessionManager]. */
class SessionRepositoryImpl(
    private val sessionManager: SessionManager
) : SessionRepository {

    override fun currentSession(): Session? {
        if (sessionManager.isDemoMode()) {
            return Session(userFullName = null, isDemo = true)
        }
        val authData = sessionManager.getAuthData() ?: return null
        return Session(userFullName = authData.userFullName, isDemo = false)
    }

    override fun isLoggedIn(): Boolean = sessionManager.isAuthenticated()

    override fun canAuthenticate(): Boolean =
        sessionManager.isAuthenticated() || sessionManager.hasStoredCredentials()

    override fun isDemoMode(): Boolean = sessionManager.isDemoMode()
}
