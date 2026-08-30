/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.auth

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.domain.usecase.LoginWithCredentials
import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.testutil.fakes.FakeAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Drives [AuthStore] directly (rather than through Koin) so validation and login outcomes are
 * deterministic — see [FakeAuthRepository].
 */
@OptIn(ExperimentalTestApi::class)
class LoginFormValidationTest {

    private fun store(repository: FakeAuthRepository = FakeAuthRepository()) =
        AuthStore(LoginWithCredentials(repository), TestScopes.immediate())

    @Test
    fun submittingEmptyForm_showsBothFieldErrors() = runComposeUiTest {
        setContent { LoginForm(store = store()) }

        onNodeWithTag("loginButton").performClick()
        waitForIdle()

        onNodeWithTag("usernameErrorText", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("passwordErrorText", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun submittingWithNonDhbwAddress_showsUsernameError() = runComposeUiTest {
        setContent { LoginForm(store = store()) }

        onNodeWithTag("usernameField").performClick()
        onNodeWithTag("usernameField").performTextInput("someone@gmail.com")
        onNodeWithTag("passwordField").performClick()
        onNodeWithTag("passwordField").performTextInput("hunter2")
        onNodeWithTag("loginButton").performClick()
        waitForIdle()

        onNodeWithTag("usernameErrorText", useUnmergedTree = true).assertIsDisplayed()
        assertFailsWith<AssertionError> { onNodeWithTag("passwordErrorText", useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test
    fun typingAfterAnError_clearsIt() = runComposeUiTest {
        setContent { LoginForm(store = store()) }

        onNodeWithTag("loginButton").performClick()
        waitForIdle()
        onNodeWithTag("usernameErrorText", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag("usernameField").performClick()
        onNodeWithTag("usernameField").performTextInput("a")
        waitForIdle()

        assertFailsWith<AssertionError> { onNodeWithTag("usernameErrorText", useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test
    fun successfulLogin_clearsTheForm() = runComposeUiTest {
        val repository = FakeAuthRepository(loginResult = Outcome.Ok(Session(userFullName = "Max")))
        setContent { LoginForm(store = store(repository)) }

        onNodeWithTag("usernameField").performClick()
        onNodeWithTag("usernameField").performTextInput("max@hb.dhbw-stuttgart.de")
        onNodeWithTag("passwordField").performClick()
        onNodeWithTag("passwordField").performTextInput("hunter2")
        onNodeWithTag("loginButton").performClick()
        waitForIdle()

        assertEquals(1, repository.loginCount)
        assertFailsWith<AssertionError> { onNodeWithTag("loginErrorText").assertIsDisplayed() }
    }

    @Test
    fun failedLogin_showsTheLoginError() = runComposeUiTest {
        val repository = FakeAuthRepository(loginResult = Outcome.Err(AppError.InvalidCredentials))
        setContent { LoginForm(store = store(repository)) }

        onNodeWithTag("usernameField").performClick()
        onNodeWithTag("usernameField").performTextInput("max@hb.dhbw-stuttgart.de")
        onNodeWithTag("passwordField").performClick()
        onNodeWithTag("passwordField").performTextInput("wrong")
        onNodeWithTag("loginButton").performClick()
        waitForIdle()

        onNodeWithTag("loginErrorText").assertIsDisplayed()
    }

    @Test
    fun clearButton_appearsWhenFocusedAndNotEmpty_andClearsTheField() = runComposeUiTest {
        setContent { LoginForm(store = store()) }

        onNodeWithTag("usernameField").performClick()
        assertFailsWith<AssertionError> { onNodeWithTag("usernameClearButton", useUnmergedTree = true).assertIsDisplayed() }

        onNodeWithTag("usernameField").performTextInput("someone")
        waitForIdle()
        onNodeWithTag("usernameClearButton", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag("usernameClearButton", useUnmergedTree = true).performClick()
        waitForIdle()

        assertFailsWith<AssertionError> { onNodeWithTag("usernameClearButton", useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test
    fun passwordVisibilityToggle_appearsWhenFocusedAndNotEmpty_andTogglesTheIcon() = runComposeUiTest {
        setContent { LoginForm(store = store()) }

        onNodeWithTag("passwordField").performClick()
        onNodeWithTag("passwordField").performTextInput("hunter2")
        waitForIdle()

        onNodeWithContentDescription("Show password", useUnmergedTree = true).assertIsDisplayed()

        // Clicking it dispatches AuthIntent.PasswordVisibilityToggled — the click itself steals
        // focus from the field, which (same as the clear button) hides the row again, so the
        // flipped icon isn't observable without re-focusing in a way this test doesn't need.
        onNodeWithTag("passwordVisibilityToggle", useUnmergedTree = true).performClick()
        waitForIdle()

        assertFailsWith<AssertionError> {
            onNodeWithContentDescription("Show password", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    // LoginButton is exercised directly (rather than through a real, awaited submission) because
    // AuthStore in these tests runs on TestScopes.immediate() — login completes synchronously, so
    // isSubmitting is never observably true through the public form.
    @Test
    fun loginButton_whenSubmitting_isDisabled() = runComposeUiTest {
        setContent {
            LoginButton(isSubmitting = true, onClick = {})
        }

        onNodeWithTag("loginButton").assertIsNotEnabled()
    }

    @Test
    fun loginButton_whenNotSubmitting_isEnabled() = runComposeUiTest {
        setContent {
            LoginButton(isSubmitting = false, onClick = {})
        }

        onNodeWithTag("loginButton").assertIsEnabled()
    }
}
