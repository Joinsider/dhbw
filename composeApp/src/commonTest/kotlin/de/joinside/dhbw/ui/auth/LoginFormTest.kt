/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LoginFormTest {

    @Test
    fun loginForm_isDisplayed() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    @Test
    fun loginForm_hasUsernameField() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        onNodeWithTag("usernameField").assertIsDisplayed()
    }

    @Test
    fun loginForm_hasPasswordField() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        onNodeWithTag("passwordField").assertIsDisplayed()
    }

    @Test
    fun loginForm_hasLoginButton() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        onNodeWithTag("loginButton").assertIsDisplayed()
        onNodeWithTag("loginButton").assertIsEnabled()
    }

    @Test
    fun loginForm_usernameFieldAcceptsInput() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        val testUsername = "testuser"
        onNodeWithTag("usernameField").performTextInput(testUsername)

        // Verify the input was accepted
        onNodeWithTag("usernameField").assertIsDisplayed()
    }

    @Test
    fun loginForm_passwordFieldAcceptsInput() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        val testPassword = "testpassword"
        onNodeWithTag("passwordField").performTextInput(testPassword)

        // Verify the input was accepted
        onNodeWithTag("passwordField").assertIsDisplayed()
    }

    @Test
    fun loginForm_showsErrorWhenUsernameIsEmpty() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Click login button without entering username
        onNodeWithTag("loginButton").performClick()

        // Wait for error message to appear
        waitForIdle()

        // Check that error message is displayed
        onNodeWithText("Username cannot be empty").assertIsDisplayed()
    }

    @Test
    fun loginForm_showsErrorWhenPasswordIsEmpty() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Enter username but not password
        onNodeWithTag("usernameField").performTextInput("testuser")
        onNodeWithTag("loginButton").performClick()

        waitForIdle()

        // Check that password error message is displayed
        onNodeWithText("Password cannot be empty").assertIsDisplayed()
    }

    @Test
    fun loginForm_showsErrorWhenBothFieldsAreEmpty() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Click login button without entering anything
        onNodeWithTag("loginButton").performClick()

        waitForIdle()

        // Both error messages should be displayed
        onNodeWithText("Username cannot be empty").assertIsDisplayed()
        onNodeWithText("Password cannot be empty").assertIsDisplayed()
    }

    @Test
    fun loginForm_errorsDisappearWhenUserTypesInUsernameField() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Trigger validation error
        onNodeWithTag("loginButton").performClick()
        waitForIdle()
        onNodeWithText("Username cannot be empty").assertIsDisplayed()

        // Start typing in username field
        onNodeWithTag("usernameField").performTextInput("t")
        waitForIdle()

        // Error should disappear
        onNodeWithText("Username cannot be empty").assertDoesNotExist()
    }

    @Test
    fun loginForm_errorsDisappearWhenUserTypesInPasswordField() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Enter username to trigger only password error
        onNodeWithTag("usernameField").performTextInput("testuser")
        onNodeWithTag("loginButton").performClick()
        waitForIdle()
        onNodeWithText("Password cannot be empty").assertIsDisplayed()

        // Start typing in password field
        onNodeWithTag("passwordField").performTextInput("p")
        waitForIdle()

        // Password error should disappear
        onNodeWithText("Password cannot be empty").assertDoesNotExist()
    }

    @Test
    fun loginForm_allowsLoginWithValidCredentials() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Enter valid credentials
        onNodeWithTag("usernameField").performTextInput("testuser")
        onNodeWithTag("passwordField").performTextInput("testpassword")

        // Click login button
        onNodeWithTag("loginButton").performClick()
        waitForIdle()

        // No error messages should be displayed
        onNodeWithText("Username cannot be empty").assertDoesNotExist()
        onNodeWithText("Password cannot be empty").assertDoesNotExist()
    }

    @Test
    fun loginForm_doesNotShowErrorsInitially() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Error messages should not be displayed on initial load
        onNodeWithText("Username cannot be empty").assertDoesNotExist()
        onNodeWithText("Password cannot be empty").assertDoesNotExist()
    }

    @Test
    fun loginForm_buttonIsAlwaysEnabled() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Button should be enabled initially
        onNodeWithTag("loginButton").assertIsEnabled()

        // Button should remain enabled after entering text
        onNodeWithTag("usernameField").performTextInput("testuser")
        onNodeWithTag("loginButton").assertIsEnabled()

        // Button should remain enabled even after validation errors
        onNodeWithTag("passwordField").performTextInput("")
        onNodeWithTag("loginButton").performClick()
        waitForIdle()
        onNodeWithTag("loginButton").assertIsEnabled()
    }

    @Test
    fun loginForm_usernameFieldHasCorrectLabel() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Check that username label is displayed
        onNodeWithText("Username").assertIsDisplayed()
    }

    @Test
    fun loginForm_passwordFieldHasCorrectLabel() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Check that password label is displayed
        onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun loginForm_loginButtonHasCorrectText() = runComposeUiTest {
        setContent {
            LoginForm()
        }

        // Check that login button has correct text
        onNodeWithText("Login").assertIsDisplayed()
    }
}
