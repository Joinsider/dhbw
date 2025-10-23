/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {

    private val testViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Test
    fun app_displaysAppContainer_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Check that app container is displayed
        onNodeWithTag("appContainer").assertIsDisplayed()
    }

    @Test
    fun app_displaysAppTitle_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Check that app title is displayed
        onNodeWithTag("appTitle").assertIsDisplayed()
    }

    @Test
    fun app_displaysLoginButton_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Check that login button is displayed on welcome screen
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()
    }

    @Test
    fun app_showsLoginForm_whenLoginButtonClicked() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Click the login button
        onNodeWithTag("loginWithDualisButton").performClick()

        // Wait for screen transition
        waitForIdle()

        // LoginForm should be displayed (using test tag from LoginForm)
        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    @Test
    fun app_loginFormNotVisible_initially() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // LoginForm should not be visible on welcome screen
        onNodeWithTag("loginForm").assertDoesNotExist()
    }

    @Test
    fun app_displaysLoginFormComponents_afterButtonClick() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Click the login button to navigate to login screen
        onNodeWithTag("loginWithDualisButton").performClick()

        // Wait for screen transition
        waitForIdle()

        // Verify LoginForm components are displayed
        onNodeWithTag("loginForm").assertIsDisplayed()
        onNodeWithTag("usernameField").assertIsDisplayed()
        onNodeWithTag("passwordField").assertIsDisplayed()
        onNodeWithTag("loginButton").assertIsDisplayed()
    }

    @Test
    fun app_loginButtonNotVisible_afterClick() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Initially button is visible on welcome screen
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()

        // Click the login button to navigate to login screen
        onNodeWithTag("loginWithDualisButton").performClick()

        // Wait for screen transition
        waitForIdle()

        // Button should not exist on login screen
        onNodeWithTag("loginWithDualisButton").assertDoesNotExist()
    }

    @Test
    fun app_navigatesToLoginScreen() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Initially on welcome screen
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()
        onNodeWithTag("loginForm").assertDoesNotExist()

        // Click the login button to navigate to login screen
        onNodeWithTag("loginWithDualisButton").performClick()
        waitForIdle()

        // Now on login screen
        onNodeWithTag("loginWithDualisButton").assertDoesNotExist()
        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    @Test
    fun app_usesCorrectTheme() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Verify that app container is rendered (theme is applied)
        onNodeWithTag("appContainer").assertIsDisplayed()
    }

    @Test
    fun app_hasCorrectLayout_onWelcomeScreen() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Verify all welcome screen elements are displayed
        onNodeWithTag("appContainer").assertIsDisplayed()
        onNodeWithTag("appTitle").assertIsDisplayed()
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()
    }

    @Test
    fun app_titleRemains_onLoginScreen() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                App()
            }
        }

        // Navigate to login screen
        onNodeWithTag("loginWithDualisButton").performClick()
        waitForIdle()

        // App title should still be visible on login screen
        onNodeWithTag("appTitle").assertIsDisplayed()
    }
}
