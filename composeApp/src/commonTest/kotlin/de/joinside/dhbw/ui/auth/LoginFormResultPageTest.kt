package de.joinside.dhbw.ui.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.storage.credentials.CredentialsStorageProvider
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class LoginFormResultPageTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var credentialsProvider: CredentialsStorageProvider
    private lateinit var sessionManager: SessionManager

    @BeforeTest
    fun setup() {
        // Initialize Napier for logging in tests
        Napier.base(DebugAntilog())
        fakeSecureStorage = FakeSecureStorage()
        credentialsProvider = CredentialsStorageProvider(fakeSecureStorage)
        sessionManager = SessionManager(fakeSecureStorage)
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

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { },
                authService = authService
            )
        }

        // Then
        waitForIdle()
        waitForIdle() // Extra wait for async LaunchedEffect

        onNodeWithTag("loginFormResultPage").assertIsDisplayed()
        onNodeWithTag("warningIcon").assertIsDisplayed()
        onNodeWithTag("noCredentialsText").assertIsDisplayed()
        onNodeWithText("No Credentials Stored").assertIsDisplayed()
        onNodeWithTag("loginPromptText").assertIsDisplayed()
    }

    @Test
    fun loginFormResultPage_withStoredCredentials_showsSuccessState() = runComposeUiTest {
        // Given - credentials and auth data are stored
        credentialsProvider.storeCredentials("test@dhbw.de", "testpassword")
        sessionManager.storeCredentials("test@dhbw.de", "testpassword")
        sessionManager.storeAuthData(
            de.joinside.dhbw.data.dualis.remote.models.AuthData(
                sessionId = "test-session-id",
                authToken = "test-auth-token"
            )
        )
        val authService = createMockAuthService(successful = true)

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { },
                authService = authService
            )
        }

        // Wait for async LaunchedEffect
        waitForIdle()
        waitForIdle()

        // Then
        onNodeWithTag("loginFormResultPage").assertIsDisplayed()
        onNodeWithTag("successIcon").assertIsDisplayed()
        onNodeWithTag("credentialsStoredText").assertIsDisplayed()
        onNodeWithText("Authentication Successful").assertIsDisplayed()
        onNodeWithTag("usernameDisplayText").assertIsDisplayed()
        onNodeWithText("Username: test@dhbw.de").assertIsDisplayed()
        onNodeWithTag("passwordDisplayText").assertIsDisplayed()
        onNodeWithText("Password: ********").assertIsDisplayed()
        onNodeWithTag("logoutButton").assertIsDisplayed()
    }

    @Test
    fun loginFormResultPage_withSuccessfulDualisLogin_showsSuccessStatus() = runComposeUiTest {
        // Given - credentials are stored and Dualis login is successful (demo mode)
        credentialsProvider.storeCredentials("demo@dhbw.de", "demopassword")
        sessionManager.storeCredentials("demo@dhbw.de", "demopassword")
        sessionManager.setDemoMode(true)
        val authService = AuthenticationService(sessionManager)

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { },
                authService = authService
            )
        }

        // Wait for async LaunchedEffect
        waitForIdle()
        waitForIdle()

        // Then
        onNodeWithTag("dualisLoginStatusText").assertIsDisplayed()
        onNodeWithText("Dualis Login: Successful").assertIsDisplayed()
        authService.close()
    }

    @Test
    fun loginFormResultPage_logoutButton_callsOnLogoutAndClearsCredentials() = runComposeUiTest {
        // Given - credentials and auth data are stored
        credentialsProvider.storeCredentials("test@dhbw.de", "testpassword")
        sessionManager.storeCredentials("test@dhbw.de", "testpassword")
        sessionManager.storeAuthData(
            de.joinside.dhbw.data.dualis.remote.models.AuthData(
                sessionId = "test-session-id",
                authToken = "test-auth-token"
            )
        )
        val authService = createMockAuthService(successful = true)

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { },
                authService = authService
            )
        }

        // Wait for async LaunchedEffect
        waitForIdle()
        waitForIdle()

        onNodeWithTag("logoutButton").performClick()

        // Then
        waitForIdle()
        assertFalse(credentialsProvider.hasStoredCredentials(), "Credentials should be cleared")
    }

    @Test
    fun loginFormResultPage_displaysCorrectUsernameFromCredentials() = runComposeUiTest {
        // Given - specific username stored
        val testUsername = "student@dhbw.de"
        credentialsProvider.storeCredentials(testUsername, "password123")
        sessionManager.storeCredentials(testUsername, "password123")
        sessionManager.storeAuthData(
            de.joinside.dhbw.data.dualis.remote.models.AuthData(
                sessionId = "test-session-id",
                authToken = "test-auth-token"
            )
        )
        val authService = createMockAuthService(successful = true)

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { },
                authService = authService
            )
        }

        // Wait for async LaunchedEffect
        waitForIdle()
        waitForIdle()

        // Then
        onNodeWithText("Username: $testUsername").assertIsDisplayed()
    }

    @Test
    fun loginFormResultPage_passwordAlwaysMasked() = runComposeUiTest {
        // Given
        credentialsProvider.storeCredentials("test@dhbw.de", "supersecretpassword")
        sessionManager.storeCredentials("test@dhbw.de", "supersecretpassword")
        sessionManager.storeAuthData(
            de.joinside.dhbw.data.dualis.remote.models.AuthData(
                sessionId = "test-session-id",
                authToken = "test-auth-token"
            )
        )
        val authService = createMockAuthService(successful = true)

        // When
        setContent {
            LoginFormResultPage(
                credentialsProvider = credentialsProvider,
                onLogout = { },
                authService = authService
            )
        }

        // Wait for async LaunchedEffect
        waitForIdle()
        waitForIdle()

        // Then
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
                    content = ByteReadChannel(
                        """
                        <html>
                        <body>
                        <h1>Login successful</h1>
                        </body>
                        </html>
                    """.trimIndent()
                    ), status = HttpStatusCode.OK, headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                    })
            } else {
                respond(
                    content = ByteReadChannel(
                        """
                        <html>
                        <body>
                        <h1>LOGINCHECK failed</h1>
                        </body>
                        </html>
                    """.trimIndent()
                    ), status = HttpStatusCode.OK, headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                    })
            }
        }

        val mockClient = HttpClient(mockEngine) {
            expectSuccess = false
            install(HttpCookies)
        }
        return AuthenticationService(sessionManager, client = mockClient)
    }
}
