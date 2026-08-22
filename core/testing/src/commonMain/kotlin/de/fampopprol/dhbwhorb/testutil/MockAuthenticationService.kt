/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.testutil

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.domain.model.Session
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel

/**
 * Mock authentication service for testing.
 * Always returns not authenticated unless explicitly set.
 */
class MockAuthenticationService(
    isAuthenticatedState: Boolean = false
) : AuthenticationService(
    sessionManager = SessionManager(TestSecureStorage()),
    client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }) {
        expectSuccess = false
    }
) {

    init {
        if (!isAuthenticatedState) {
            sessionManager.logout()
        }
    }

    override fun isAuthenticated(): Boolean {
        return sessionManager.isAuthenticated()
    }

    override suspend fun login(username: String, password: String): Outcome<Session> {
        // Every login succeeds: these tests are about what the UI does with a session, not about
        // the Dualis handshake, which AuthenticationServiceTest covers against a mock engine.
        sessionManager.storeAuthData(
            AuthData(sessionId = "test-session", authToken = "test-token", userFullName = null)
        )
        sessionManager.storeCredentials(username, password)
        return Outcome.Ok(Session(userFullName = null, isDemo = false))
    }

    override fun logout() {
        sessionManager.logout()
    }

    fun setAuthenticatedState(authenticated: Boolean) {
        if (authenticated) {
            val authData = AuthData(
                sessionId = "test-session",
                authToken = "test-token",
                userFullName = null
            )
            sessionManager.storeAuthData(authData)
        } else {
            sessionManager.logout()
        }
    }
}


