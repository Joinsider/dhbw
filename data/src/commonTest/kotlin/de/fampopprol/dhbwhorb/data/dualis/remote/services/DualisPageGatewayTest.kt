/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.domain.model.Session
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DualisPageGatewayTest {

    private val httpClient = HttpClient { }
    private val sessionManager = SessionManager(FakeSecureStorage())

    /** Overrides only [login], and mimics what a real one does to session state on success. */
    private class FakeAuthenticationService(
        sessionManager: SessionManager,
        client: HttpClient,
        private val sessionManagerRef: SessionManager,
        private val results: List<Outcome<Session>>,
    ) : AuthenticationService(sessionManager, client) {
        var loginCount = 0
            private set

        override suspend fun login(username: String, password: String): Outcome<Session> {
            val result = results.getOrElse(loginCount) { results.last() }
            loginCount++
            if (result is Outcome.Ok) {
                sessionManagerRef.storeAuthData(AuthData(sessionId = "s-$loginCount", authToken = "t-$loginCount"))
            }
            return result
        }
    }

    private fun gateway(
        authResults: List<Outcome<Session>> = listOf(Outcome.Ok(Session(userFullName = "Max"))),
    ): Pair<DualisPageGateway, FakeAuthenticationService> {
        val authService = FakeAuthenticationService(sessionManager, httpClient, sessionManager, authResults)
        val reAuthenticator = ReAuthenticator(sessionManager, authService)
        return DualisPageGateway(
            apiClient = DualisApiClient(httpClient),
            sessionManager = sessionManager,
            reAuthenticator = reAuthenticator,
        ) to authService
    }

    @Test
    fun notAuthenticated_noStoredCredentials_returnsNoCredentials() = runTest {
        val (gateway, _) = gateway()
        // isAuthenticated() is false: nothing was ever stored, and no credentials means
        // ReAuthenticator can't even attempt a login.

        val result = gateway.fetchPage(source = "test", isValid = { true }, buildUrl = { "" })

        assertEquals(Outcome.Err(AppError.NoCredentials), result)
    }

    @Test
    fun demoModeWithoutASession_credentialsStored_reauthenticatesAndRetries() = runTest {
        sessionManager.setDemoMode(true)
        sessionManager.storeCredentials("max@hb.dhbw-stuttgart.de", "hunter2")
        val (_, authService) = gateway()
        val client = HttpClient(MockEngine { respond(ByteReadChannel("<html>ok</html>"), HttpStatusCode.OK) })
        val realGateway = DualisPageGateway(
            apiClient = DualisApiClient(client),
            sessionManager = sessionManager,
            reAuthenticator = ReAuthenticator(sessionManager, authService),
        )

        // Demo mode makes isAuthenticated() true with no real AuthData — the gateway's own "no
        // usable session id" branch, not the "not authenticated at all" one above.
        val result = realGateway.fetchPage(source = "test", isValid = { true }, buildUrl = { "" })

        assertIs<Outcome.Ok<String>>(result)
        assertEquals(1, authService.loginCount, "one re-authentication should have supplied the missing session")
    }

    @Test
    fun sessionExpiredMidFetch_reauthenticatesAndRetriesSuccessfully() = runTest {
        sessionManager.storeAuthData(AuthData(sessionId = "expired", authToken = "expired"))
        sessionManager.storeCredentials("max@hb.dhbw-stuttgart.de", "hunter2")
        val (_, authService) = gateway()

        var requestCount = 0
        val client = HttpClient(MockEngine { request ->
            requestCount++
            if (requestCount == 1) {
                respond(ByteReadChannel(""), HttpStatusCode.Unauthorized)
            } else {
                respond(ByteReadChannel("<html>ok</html>"), HttpStatusCode.OK)
            }
        })
        val realGateway = DualisPageGateway(
            apiClient = DualisApiClient(client),
            sessionManager = sessionManager,
            reAuthenticator = ReAuthenticator(sessionManager, authService),
        )

        val result = realGateway.fetchPage(source = "test", isValid = { true }, buildUrl = { "" })

        val page = assertIs<Outcome.Ok<String>>(result)
        assertEquals("<html>ok</html>", page.value)
        assertEquals(1, authService.loginCount)
    }

    @Test
    fun unexpectedPage_onLastAttempt_notAnErrorPage_returnsParseError() = runTest {
        sessionManager.storeAuthData(AuthData(sessionId = "s", authToken = "t"))
        sessionManager.storeCredentials("max@hb.dhbw-stuttgart.de", "hunter2")
        val (_, authService) = gateway()

        // Both attempts see a page isValid() rejects, and neither is Dualis' error page. The
        // first attempt re-authenticates and retries (not the last attempt yet); the second
        // (now the last) must give up with a Parse error rather than loop forever.
        val client = HttpClient(MockEngine { respond(ByteReadChannel("<html>some redesign</html>"), HttpStatusCode.OK) })
        val realGateway = DualisPageGateway(
            apiClient = DualisApiClient(client),
            sessionManager = sessionManager,
            reAuthenticator = ReAuthenticator(sessionManager, authService),
        )

        val result = realGateway.fetchPage(source = "widgets", isValid = { false }, buildUrl = { "" })

        val error = assertIs<Outcome.Err>(result).error
        assertIs<AppError.Parse>(error)
        assertEquals(1, authService.loginCount, "the first attempt should have re-authenticated once before giving up")
    }

    @Test
    fun notAuthenticated_reauthenticationFails_propagatesTheError() = runTest {
        sessionManager.storeCredentials("max@hb.dhbw-stuttgart.de", "wrong")
        val (gateway, _) = gatewayWithFailingReauth()

        val result = gateway.fetchPage(source = "test", isValid = { true }, buildUrl = { "" })

        assertEquals(Outcome.Err(AppError.InvalidCredentials), result)
    }

    private fun gatewayWithFailingReauth(): Pair<DualisPageGateway, FakeAuthenticationService> {
        val authService = FakeAuthenticationService(
            sessionManager, httpClient, sessionManager,
            listOf(Outcome.Err(AppError.InvalidCredentials)),
        )
        return DualisPageGateway(
            apiClient = DualisApiClient(httpClient),
            sessionManager = sessionManager,
            reAuthenticator = ReAuthenticator(sessionManager, authService),
        ) to authService
    }
}
