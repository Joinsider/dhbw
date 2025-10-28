package de.joinside.dhbw.data.dualis.remote.services

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationServiceTest {

    @BeforeTest
    fun setup() {
        // Initialize Napier for logging in tests
        Napier.base(DebugAntilog())
    }

    @AfterTest
    fun teardown() {
        // Clean up Napier
        Napier.takeLogarithm()
    }

    @Test
    fun login_withDemoCredentials_returnsTrue() = runTest {
        // Given
        val service = AuthenticationService()
        val username = "demo@dhbw.de"
        val password = "demopassword"

        // When
        val result = service.login(username, password)

        // Then
        assertTrue(result, "Demo credentials should always return true")
        service.close()
    }

    @Test
    fun login_withValidCredentials_returnsTrue() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""
                    <html>
                    <body>
                    <h1>Login successful</h1>
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
        val username = "valid@dhbw.de"
        val password = "validpassword"

        // When
        val result = service.login(username, password)

        // Then
        assertTrue(result, "Valid credentials should return true")
        service.close()
    }

    @Test
    fun login_withInvalidCredentials_returnsFalse() = runTest {
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
        assertFalse(result, "Invalid credentials should return false")
        service.close()
    }

    @Test
    fun login_withHttpError_returnsFalse() = runTest {
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
        assertFalse(result, "HTTP error should return false")
        service.close()
    }

    @Test
    fun login_withNetworkException_returnsFalse() = runTest {
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
        assertFalse(result, "Network exception should return false")
        service.close()
    }

    @Test
    fun login_withEmptyCredentials_returnsFalse() = runTest {
        // Given
        val service = AuthenticationService()
        val username = ""
        val password = ""

        // When
        val result = service.login(username, password)

        // Then
        assertFalse(result, "Empty credentials should return false")
        service.close()
    }

    @Test
    fun login_withRedirectResponse_returnsFalse() = runTest {
        // Given
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.Found,
                headers = headers {
                    append(HttpHeaders.Location, "/error")
                }
            )
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "user@dhbw.de"
        val password = "password"

        // When
        val result = service.login(username, password)

        // Then
        assertFalse(result, "Redirect should return false")
        service.close()
    }

    // Helper function to create AuthenticationService with mock engine
    private fun createAuthenticationServiceWithMockEngine(mockEngine: MockEngine): AuthenticationService {
        val mockClient = HttpClient(mockEngine) {
            expectSuccess = false
        }
        return AuthenticationService(mockClient)
    }
}

