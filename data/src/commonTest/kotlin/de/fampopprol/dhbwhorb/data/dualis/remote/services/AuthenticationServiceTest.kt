/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Login against a mock engine.
 *
 * Every failing case asserts *which* [AppError] comes back, not just that something failed:
 * telling "wrong password" from "no connection" from "Dualis answered with something new" is the
 * whole point of the classified error channel.
 */
class AuthenticationServiceTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var sessionManager: SessionManager

    @BeforeTest
    fun setup() {
        // Initialize Napier for logging in tests
        Napier.base(DebugAntilog())

        // Initialize fake storage and session manager
        fakeSecureStorage = FakeSecureStorage()
        sessionManager = SessionManager(fakeSecureStorage)
    }

    @AfterTest
    fun teardown() {
        // Clean up Napier
        Napier.takeLogarithm()
    }

    @Test
    fun login_withDemoCredentials_succeedsWithoutTouchingTheNetwork() = runTest {
        // Given
        val mockClient = HttpClient(MockEngine {
            // This should never be called for demo credentials
            respond(ByteReadChannel("Should not reach here"), HttpStatusCode.InternalServerError)
        }) {
            expectSuccess = false
        }
        val service = AuthenticationService(sessionManager, mockClient)
        val username = "demo@hb.dhbw-stuttgart.de"
        val password = "demo123"

        // When
        val result = service.login(username, password)

        // Then
        val session = assertIs<Outcome.Ok<*>>(result, "Demo credentials must log in").value
        assertTrue((session as de.fampopprol.dhbwhorb.domain.model.Session).isDemo)
        service.close()
    }

    @Test
    fun login_withInvalidCredentials_reportsInvalidCredentials() = runTest {
        // Given
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""
                    <html>
                    <body>
                    <h1>LOGINCHECK failed</h1>
                    </body>
                    </html>
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headers {
                    append(HttpHeaders.ContentType, "text/html")
                }
            )
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "invalid@dhbw.de"
        val password = "wrongpassword"

        // When
        val result = service.login(username, password)

        // Then
        assertEquals(Outcome.Err(AppError.InvalidCredentials), result)
        service.close()
    }

    @Test
    fun login_withServerError_reportsTheStatusCode() = runTest {
        // Given
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("Internal Server Error"),
                status = HttpStatusCode.InternalServerError,
                headers = headers {
                    append(HttpHeaders.ContentType, "text/html")
                }
            )
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "user@dhbw.de"
        val password = "password"

        // When
        val result = service.login(username, password)

        // Then
        assertEquals(Outcome.Err(AppError.Http(500)), result)
        service.close()
    }

    @Test
    fun login_withoutAConnection_reportsOffline() = runTest {
        // Given
        val mockEngine = MockEngine {
            throw IOException("Network is unreachable")
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "user@dhbw.de"
        val password = "password"

        // When
        val result = service.login(username, password)

        // Then
        assertEquals(
            Outcome.Err(AppError.Offline),
            result,
            "A transport failure is 'you are offline', not an unknown error"
        )
        service.close()
    }

    @Test
    fun login_withoutARedirectHeader_reportsAParseFailure() = runTest {
        // Given
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""
                    <html>
                    <body>
                    <h1>Login response without redirect</h1>
                    </body>
                    </html>
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headers {
                    append(HttpHeaders.ContentType, "text/html")
                }
            )
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "user@dhbw.de"
        val password = "password"

        // When
        val result = service.login(username, password)

        // Then
        // A 200 that is neither the login form nor a redirect means Dualis changed its handshake.
        assertIs<AppError.Parse>(assertIs<Outcome.Err>(result).error)
        service.close()
    }

    @Test
    fun isAuthenticated_returnsTrueWhenDemoMode() = runTest {
        // Given
        val mockClient = HttpClient(MockEngine {
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        }) {
            expectSuccess = false
        }
        val service = AuthenticationService(sessionManager, mockClient)
        sessionManager.setDemoMode(true)

        // When
        val isAuth = service.isAuthenticated()

        // Then
        assertTrue(isAuth, "Should be authenticated in demo mode")
        service.close()
    }

    @Test
    fun logout_clearsSessionData() = runTest {
        // Given
        val mockClient = HttpClient(MockEngine {
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        }) {
            expectSuccess = false
        }
        val service = AuthenticationService(sessionManager, mockClient)
        sessionManager.storeCredentials("test@dhbw.de", "password")
        sessionManager.setDemoMode(true)

        // When
        service.logout()

        // Then
        assertTrue(!service.isAuthenticated(), "Should not be authenticated after logout")
        service.close()
    }

    // Helper function to create AuthenticationService with mock engine
    private fun createAuthenticationServiceWithMockEngine(mockEngine: MockEngine): AuthenticationService {
        val mockClient = HttpClient(mockEngine) {
            expectSuccess = false
            install(HttpCookies)
        }
        return AuthenticationService(sessionManager, client = mockClient)
    }
}
