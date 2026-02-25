/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.data.dualis.remote.services

import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.storage.credentials.FakeSecureStorage
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
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for AuthenticationService functionality.
 * These tests verify login, logout, and authentication state management.
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
    fun login_withDemoCredentials_returnsSuccess() = runTest {
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
        assertTrue(result is LoginResult.Success, "Demo credentials should return Success")
        service.close()
    }

    @Test
    fun login_withInvalidCredentials_returnsFailure() = runTest {
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
        assertTrue(result is LoginResult.Failure, "Invalid credentials should return Failure")
        service.close()
    }

    @Test
    fun login_withHttpError_returnsFailure() = runTest {
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
        assertTrue(result is LoginResult.Failure, "HTTP error should return Failure")
        service.close()
    }

    @Test
    fun login_withNetworkException_returnsFailure() = runTest {
        // Given
        val mockEngine = MockEngine {
            throw Exception("Network error")
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "user@dhbw.de"
        val password = "password"

        // When
        val result = service.login(username, password)

        // Then
        assertTrue(result is LoginResult.Failure, "Network exception should return Failure")
        service.close()
    }

    @Test
    fun login_withNoRedirectHeader_returnsFailure() = runTest {
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
        assertTrue(result is LoginResult.Failure, "No redirect header should return Failure")
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
