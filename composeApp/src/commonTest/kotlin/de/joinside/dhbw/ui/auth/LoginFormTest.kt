package de.joinside.dhbw.ui.auth

import de.joinside.dhbw.i18n.ProvideStrings
import de.joinside.dhbw.ui.theme.DHBWHorbTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

class LoginFormTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun formFieldsExist() = runComposeUiTest {
        setContent {
            ProvideStrings {
                DHBWHorbTheme {
                    LoginForm()
                }
            }
        }

        onNodeWithTag("usernameField").assertExists()
        onNodeWithTag("passwordField").assertExists()
        onNodeWithTag("loginButton").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun formFieldsIncludeText() = runComposeUiTest {
        setContent {
            ProvideStrings {
                DHBWHorbTheme {
                    LoginForm()
                }
            }
        }

        onNodeWithText("Username").assertExists()
        onNodeWithText("Password").assertExists()
        onNodeWithText("Login").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun emptySubmitShowsValidationErrors() = runComposeUiTest {
        setContent {
            ProvideStrings {
                DHBWHorbTheme {
                    LoginForm()
                }
            }
        }

        // Click the login button with empty fields
        onNodeWithTag("loginButton").performClick()

        // Expect validation error texts to appear
        onNodeWithText("Username cannot be empty").assertExists()
        onNodeWithText("Password cannot be empty").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun typingClearsValidationErrors() = runComposeUiTest {
        setContent {
            ProvideStrings {
                DHBWHorbTheme {
                    LoginForm()
                }
            }
        }

        // Trigger validation errors first
        onNodeWithTag("loginButton").performClick()

        // Type into username field and ensure username error disappears
        onNodeWithTag("usernameField").performTextInput("john")
        onNodeWithText("Username cannot be empty").assertDoesNotExist()
        // Password error should still be present
        onNodeWithText("Password cannot be empty").assertExists()

        // Type into password field and ensure password error disappears
        onNodeWithTag("passwordField").performTextInput("secret")
        onNodeWithText("Password cannot be empty").assertDoesNotExist()
    }
}