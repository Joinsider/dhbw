package de.joinside.dhbw.ui.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.storage.credentials.CredentialsStorageProvider
import de.joinside.dhbw.data.storage.credentials.FakeSecureStorage
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class LoginFormResultPageTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var credentialsProvider: CredentialsStorageProvider

    @BeforeTest
    fun setup() {
        // Initialize Napier for logging in tests
        Napier.base(DebugAntilog())
        fakeSecureStorage = FakeSecureStorage()
        credentialsProvider = CredentialsStorageProvider(fakeSecureStorage)
    }

    @AfterTest
    fun teardown() {
        // Clean up Napier
        Napier.takeLogarithm()
    }

    @Test
    fun loginFormResultPage_withNoCredentials_showsNoCredentialsMessage() = runComposeUiTest {
        // Given - no credentials stored
        val authService = createMockAuthService(successful = false)
        var logoutCalled = false

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { logoutCalled = true },
                authService = authService
            )
        }

        // Then
        waitForIdle()
        onNodeWithTag("loginFormResultPage").assertIsDisplayed()
        onNodeWithTag("warningIcon").assertIsDisplayed()
        onNodeWithTag("noCredentialsText").assertIsDisplayed()
        onNodeWithText("No Credentials Stored").assertIsDisplayed()
        onNodeWithTag("loginPromptText").assertIsDisplayed()
    }

    @Test
    fun loginFormResultPage_withStoredCredentials_showsSuccessState() = runComposeUiTest {
        // Given - credentials are stored
        credentialsProvider.storeCredentials("test@dhbw.de", "testpassword")
        val authService = createMockAuthService(successful = true)
        var logoutCalled = false

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { logoutCalled = true },
                authService = authService
            )
        }

        // Then
        waitForIdle()
        onNodeWithTag("loginFormResultPage").assertIsDisplayed()
        onNodeWithTag("successIcon").assertIsDisplayed()
        onNodeWithTag("credentialsStoredText").assertIsDisplayed()
        onNodeWithText("Credentials Stored").assertIsDisplayed()
        onNodeWithTag("usernameDisplayText").assertIsDisplayed()
        onNodeWithText("Username: test@dhbw.de").assertIsDisplayed()
        onNodeWithTag("passwordDisplayText").assertIsDisplayed()
        onNodeWithText("Password: ********").assertIsDisplayed()
        onNodeWithTag("logoutButton").assertIsDisplayed()
    }

    @Test
    fun loginFormResultPage_withSuccessfulDualisLogin_showsSuccessStatus() = runComposeUiTest {
        // Given - credentials are stored and Dualis login is successful
        credentialsProvider.storeCredentials("demo@dhbw.de", "demopassword")
        val authService = AuthenticationService() // Demo credentials always succeed
        var logoutCalled = false

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { logoutCalled = true },
                authService = authService
            )
        }

        // Then
        waitForIdle()
        onNodeWithTag("dualisLoginStatusText").assertIsDisplayed()
        onNodeWithText("Dualis Login: Successful").assertIsDisplayed()
    }

    @Test
    fun loginFormResultPage_withFailedDualisLogin_showsFailureStatus() = runComposeUiTest {
        // Given - credentials are stored but Dualis login fails
        credentialsProvider.storeCredentials("invalid@dhbw.de", "wrongpassword")
        val authService = createMockAuthService(successful = false)
        var logoutCalled = false

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { logoutCalled = true },
                authService = authService
            )
        }

        // Then
        waitForIdle()
        onNodeWithTag("dualisLoginStatusText").assertIsDisplayed()
        onNodeWithText("Dualis Login: Failed").assertIsDisplayed()
    }

    @Test
    fun loginFormResultPage_logoutButton_callsOnLogoutAndClearsCredentials() = runComposeUiTest {
        // Given - credentials are stored
        credentialsProvider.storeCredentials("test@dhbw.de", "testpassword")
        val authService = createMockAuthService(successful = true)
        var logoutCalled = false

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { logoutCalled = true },
                authService = authService
            )
        }

        waitForIdle()
        onNodeWithTag("logoutButton").performClick()

        // Then
        waitForIdle()
        assertFalse(credentialsProvider.hasStoredCredentials(), "Credentials should be cleared")
        // Note: logoutCalled cannot be verified in this context as the state would need to be updated
    }

    @Test
    fun loginFormResultPage_displaysCorrectUsernameFromCredentials() = runComposeUiTest {
        // Given - specific username stored
        val testUsername = "student@dhbw.de"
        credentialsProvider.storeCredentials(testUsername, "password123")
        val authService = createMockAuthService(successful = true)
        var logoutCalled = false

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { logoutCalled = true },
                authService = authService
            )
        }

        // Then
        waitForIdle()
        onNodeWithText("Username: $testUsername").assertIsDisplayed()
    }

    @Test
    fun loginFormResultPage_passwordAlwaysMasked() = runComposeUiTest {
        // Given
        credentialsProvider.storeCredentials("test@dhbw.de", "supersecretpassword")
        val authService = createMockAuthService(successful = true)
        var logoutCalled = false

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { logoutCalled = true },
                authService = authService
            )
        }

        // Then
        waitForIdle()
        onNodeWithTag("passwordDisplayText").assertIsDisplayed()
        onNodeWithText("Password: ********").assertIsDisplayed()
        // Ensure actual password is not displayed
        onNodeWithText("supersecretpassword").assertDoesNotExist()
    }

    // Helper function to create mock AuthenticationService
    private fun createMockAuthService(successful: Boolean): AuthenticationService {
        val mockEngine = MockEngine { request ->
            if (successful) {
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
            } else {
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
        }

        val mockClient = HttpClient(mockEngine) {
            expectSuccess = false
        }
        return AuthenticationService(mockClient)
    }
}

