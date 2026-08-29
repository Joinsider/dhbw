/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.session

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.domain.model.Session
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three Dualis services all react to an unusable session by re-authenticating, and the app
 * loads three screens at once on start. The previous `isReAuthenticating` boolean let the first
 * caller through and told the rest "already in progress", so their requests failed even though a
 * fresh session arrived moments later. These tests pin down that this no longer happens.
 */
class ReAuthenticatorTest {

    private class CountingAuthService(
        sessionManager: SessionManager,
        private val gate: CompletableDeferred<Unit>? = null,
        private val result: () -> Outcome<Session>
    ) : AuthenticationService(
        sessionManager = sessionManager,
        client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }) {
            expectSuccess = false
        }
    ) {
        var loginCount = 0
            private set

        override suspend fun login(username: String, password: String): Outcome<Session> {
            loginCount++
            gate?.await()
            return result()
        }
    }

    private fun sessionManagerWithCredentials(): SessionManager =
        SessionManager(FakeSecureStorage()).apply { storeCredentials("user@hb.dhbw-stuttgart.de", "pw") }

    @Test
    fun concurrentCallers_shareASingleLogin() = runTest {
        val sessionManager = sessionManagerWithCredentials()
        val gate = CompletableDeferred<Unit>()
        val authService = CountingAuthService(sessionManager, gate) {
            Outcome.Ok(Session(userFullName = "Test User"))
        }
        val reAuthenticator = ReAuthenticator(sessionManager, authService)

        // All three start before any of them can finish, which is the situation the old flag
        // handled by rejecting two of them.
        val calls = List(3) { async { reAuthenticator.reAuthenticate() } }
        // Let all three reach the gate before any of them can finish; otherwise the first one
        // completes and the others simply start a new attempt, which is not the race being tested.
        runCurrent()
        gate.complete(Unit)
        val results = calls.map { it.await() }

        assertEquals(1, authService.loginCount, "Dualis must see exactly one login attempt")
        assertTrue(results.all { it is Outcome.Ok }, "every caller receives the shared session")
    }

    @Test
    fun afterCompletion_theNextCallerStartsAFreshLogin() = runTest {
        val sessionManager = sessionManagerWithCredentials()
        val authService = CountingAuthService(sessionManager) {
            Outcome.Ok(Session(userFullName = "Test User"))
        }
        val reAuthenticator = ReAuthenticator(sessionManager, authService)

        reAuthenticator.reAuthenticate()
        reAuthenticator.reAuthenticate()

        assertEquals(2, authService.loginCount, "a finished attempt must not be reused")
    }

    @Test
    fun aFailedLogin_isReportedToEveryWaitingCaller() = runTest {
        val sessionManager = sessionManagerWithCredentials()
        val gate = CompletableDeferred<Unit>()
        val authService = CountingAuthService(sessionManager, gate) {
            Outcome.Err(AppError.InvalidCredentials)
        }
        val reAuthenticator = ReAuthenticator(sessionManager, authService)

        val calls = List(3) { async { reAuthenticator.reAuthenticate() } }
        runCurrent()
        gate.complete(Unit)
        val results = calls.map { it.await() }

        assertEquals(1, authService.loginCount)
        assertTrue(
            results.all { it == Outcome.Err(AppError.InvalidCredentials) },
            "followers must learn why the login failed, not just that it did"
        )
    }

    @Test
    fun withoutStoredCredentials_itReportsNoCredentials() = runTest {
        val sessionManager = SessionManager(FakeSecureStorage())
        val authService = CountingAuthService(sessionManager) {
            Outcome.Ok(Session(userFullName = null))
        }
        val reAuthenticator = ReAuthenticator(sessionManager, authService)

        assertEquals(Outcome.Err(AppError.NoCredentials), reAuthenticator.reAuthenticate())
        assertEquals(0, authService.loginCount, "there is nothing to log in with")
    }
}
