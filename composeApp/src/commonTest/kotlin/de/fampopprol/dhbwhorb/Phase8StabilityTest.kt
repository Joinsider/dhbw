package de.fampopprol.dhbwhorb

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import de.fampopprol.dhbwhorb.testutil.MockAuthenticationService
import de.fampopprol.dhbwhorb.testutil.MockCredentialsProvider
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import io.ktor.client.HttpClient
import kotlin.test.Test

/**
 * Phase 8 Stability Tests:
 * 1. Verify that the app shows a LoadingIndicator when isInitialized is false (early setContent).
 * 2. Verify that the app transitions to the main UI once isInitialized is true.
 */
@OptIn(ExperimentalTestApi::class)
class Phase8StabilityTest {

    private val testViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun createMockAuthService(authenticated: Boolean = false) =
        MockAuthenticationService(authenticated)

    private fun createMockCredentialsProvider() = MockCredentialsProvider()

    private fun createMockHttpClient() = HttpClient { }

    @Test
    fun app_displaysLoadingIndicator_whenNotInitialized() = runComposeUiTest {
        val authService = createMockAuthService(authenticated = false)
        val secureStorage = FakeSecureStorage()
        val sessionManager = SessionManager(secureStorage)
        val httpClient = createMockHttpClient()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App(
                    isInitialized = false,
                    testAuthenticationService = authService,
                    testCredentialsProvider = createMockCredentialsProvider(),
                    testSecureStorage = secureStorage,
                    sessionManager = sessionManager,
                    sharedHttpClient = httpClient
                )
            }
        }

        // Wait for composition
        waitForIdle()

        // Verify that only LoadingIndicator (skeleton) is shown
        // Since LoadingIndicator doesn't have a test tag by default, we'll check for absence of other elements
        onNodeWithTag("appTitle").assertDoesNotExist()
        onNodeWithTag("loginForm").assertDoesNotExist()
    }

    @Test
    fun app_displaysMainUI_whenInitialized() = runComposeUiTest {
        val authService = createMockAuthService(authenticated = false)
        val secureStorage = FakeSecureStorage()
        val sessionManager = SessionManager(secureStorage)
        val httpClient = createMockHttpClient()

        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App(
                    isInitialized = true,
                    testAuthenticationService = authService,
                    testCredentialsProvider = createMockCredentialsProvider(),
                    testSecureStorage = secureStorage,
                    sessionManager = sessionManager,
                    sharedHttpClient = httpClient
                )
            }
        }

        // Wait for composition
        waitForIdle()

        // Verify that main UI (Welcome screen) is shown
        onNodeWithTag("appTitle").assertIsDisplayed()
        onNodeWithTag("loginForm").assertIsDisplayed()
    }
}
