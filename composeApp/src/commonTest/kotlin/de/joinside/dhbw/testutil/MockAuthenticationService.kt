/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.testutil

import de.joinside.dhbw.data.dualis.remote.models.AuthData
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.dualis.remote.services.LoginResult
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
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

    override suspend fun login(username: String, password: String): LoginResult {
        // Simulate successful login for test purposes
        val authData = AuthData(
            sessionId = "test-session",
            authToken = "test-token",
            userFullName = null
        )
        sessionManager.storeAuthData(authData)
        sessionManager.storeCredentials(username, password)
        return LoginResult.Success(authData)
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


