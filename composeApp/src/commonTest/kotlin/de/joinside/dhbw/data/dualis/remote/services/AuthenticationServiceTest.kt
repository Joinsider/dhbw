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
        val service = AuthenticationService(sessionManager)
        val username = "demo@dhbw.de"
        val password = "demopassword"

        // When
        val result = service.login(username, password)

        // Then
        assertTrue(result is LoginResult.Success, "Demo credentials should return Success")
        service.close()
    }

    @Test
    fun login_withInvalidCredentials_returnsFailure() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
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
        val mockEngine = MockEngine { request ->
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
        val mockEngine = MockEngine { request ->
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
        val mockEngine = MockEngine { request ->
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
        val service = AuthenticationService(sessionManager)
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
        val service = AuthenticationService(sessionManager)
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

