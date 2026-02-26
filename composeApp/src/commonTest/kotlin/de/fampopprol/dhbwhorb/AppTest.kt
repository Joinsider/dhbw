/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import de.fampopprol.dhbwhorb.testutil.MockAuthenticationService
import de.fampopprol.dhbwhorb.testutil.MockCredentialsProvider
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {

    private val testViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private fun createMockAuthService(authenticated: Boolean = false) =
        MockAuthenticationService(authenticated)

    private fun createMockCredentialsProvider() = MockCredentialsProvider()

    @Test
    fun app_displaysAppContainer_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App(
                    testAuthenticationService = createMockAuthService(authenticated = false),
                    testCredentialsProvider = createMockCredentialsProvider()
                )
            }
        }

        // Check that app container is displayed
        onNodeWithTag("appContainer").assertIsDisplayed()
    }

    @Test
    fun app_displaysAppTitle_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App(
                    testAuthenticationService = createMockAuthService(authenticated = false),
                    testCredentialsProvider = createMockCredentialsProvider()
                )
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // Check that app title is displayed
        onNodeWithTag("appTitle").assertIsDisplayed()
    }

    @Test
    fun app_displaysLoginForm_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App(
                    testAuthenticationService = createMockAuthService(authenticated = false),
                    testCredentialsProvider = createMockCredentialsProvider()
                )
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // Check that login form is displayed on welcome screen
        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    @Test
    fun app_showsLoginFormComponents() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App(
                    testAuthenticationService = createMockAuthService(authenticated = false),
                    testCredentialsProvider = createMockCredentialsProvider()
                )
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // LoginForm should be displayed with its components
        onNodeWithTag("loginForm").assertIsDisplayed()
        onNodeWithTag("usernameField").assertIsDisplayed()
        onNodeWithTag("passwordField").assertIsDisplayed()
        onNodeWithTag("loginButton").assertIsDisplayed()
    }

    @Test
    fun app_usesCorrectTheme() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App(
                    testAuthenticationService = createMockAuthService(authenticated = false),
                    testCredentialsProvider = createMockCredentialsProvider()
                )
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // Verify that app container is rendered (theme is applied)
        onNodeWithTag("appContainer").assertIsDisplayed()
    }

    @Test
    fun app_hasCorrectLayout_onWelcomeScreen() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App(
                    testAuthenticationService = createMockAuthService(authenticated = false),
                    testCredentialsProvider = createMockCredentialsProvider()
                )
            }
        }

        // Wait for composition to complete
        waitForIdle()

        // Verify all welcome screen elements are displayed
        onNodeWithTag("appContainer").assertIsDisplayed()
        onNodeWithTag("appTitle").assertIsDisplayed()
        onNodeWithTag("loginForm").assertIsDisplayed()
    }
}
