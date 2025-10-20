/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {

    @Test
    fun app_displaysAppContainer_initially() = runComposeUiTest {
        setContent {
            App()
        }

        // Check that app container is displayed
        onNodeWithTag("appContainer").assertIsDisplayed()
    }

    @Test
    fun app_displaysAppTitle_initially() = runComposeUiTest {
        setContent {
            App()
        }

        // Check that app title is displayed
        onNodeWithTag("appTitle").assertIsDisplayed()
    }

    @Test
    fun app_displaysLoginButton_initially() = runComposeUiTest {
        setContent {
            App()
        }

        // Check that login button is displayed
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()
    }

    @Test
    fun app_showsLoginForm_whenLoginButtonClicked() = runComposeUiTest {
        setContent {
            App()
        }

        // Click the login button
        onNodeWithTag("loginWithDualisButton").performClick()

        // Wait for animation to complete
        waitForIdle()

        // LoginForm should be displayed (using test tag from LoginForm)
        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    @Test
    fun app_loginFormNotVisible_initially() = runComposeUiTest {
        setContent {
            App()
        }

        // LoginForm should not be visible initially
        onNodeWithTag("loginForm").assertDoesNotExist()
    }

    @Test
    fun app_displaysLoginFormComponents_afterButtonClick() = runComposeUiTest {
        setContent {
            App()
        }

        // Click the login button
        onNodeWithTag("loginWithDualisButton").performClick()

        // Wait for animation to complete
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
            App()
        }

        // Initially button is visible
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()

        // Click the login button
        onNodeWithTag("loginWithDualisButton").performClick()

        // Wait for animation to complete
        waitForIdle()

        // Button should not exist anymore (AnimatedVisibility removes it from tree)
        onNodeWithTag("loginWithDualisButton").assertDoesNotExist()
    }

    @Test
    fun app_togglesBetweenButtonAndForm() = runComposeUiTest {
        setContent {
            App()
        }

        // Initially, button is visible and form is not
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()
        onNodeWithTag("loginForm").assertDoesNotExist()

        // Click the login button
        onNodeWithTag("loginWithDualisButton").performClick()
        waitForIdle()

        // After click, button is hidden and form is shown
        onNodeWithTag("loginWithDualisButton").assertDoesNotExist()
        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    @Test
    fun app_usesCorrectTheme() = runComposeUiTest {
        setContent {
            App()
        }

        // Verify that app container is rendered (theme is applied)
        onNodeWithTag("appContainer").assertIsDisplayed()
    }

    @Test
    fun app_hasCorrectLayout() = runComposeUiTest {
        setContent {
            App()
        }

        // Verify all initial elements are displayed
        onNodeWithTag("appContainer").assertIsDisplayed()
        onNodeWithTag("appTitle").assertIsDisplayed()
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()
    }
}
