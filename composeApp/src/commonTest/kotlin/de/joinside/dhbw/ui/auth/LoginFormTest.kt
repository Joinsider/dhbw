package de.joinside.dhbw.ui.auth

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test


class LoginFormTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun formFieldsExist() = runComposeUiTest {
        onNodeWithTag("emailInput").assertExists()
        onNodeWithTag("passwordInput").assertExists()
        onNodeWithTag("loginButton").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun formFieldsIncludeText() = runComposeUiTest {
        onNodeWithText("Email").assertExists()
        onNodeWithText("Password").assertExists()
        onNodeWithText("Login").assertExists()
    }
}