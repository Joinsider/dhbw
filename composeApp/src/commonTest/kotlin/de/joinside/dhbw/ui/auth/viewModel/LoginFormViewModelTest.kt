package de.joinside.dhbw.ui.auth.viewModel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginFormViewModelTest {

    @Test
    fun initialState_isEmpty() {
        val viewModel = LoginFormViewModel()

        assertEquals("", viewModel.uiState.username)
        assertEquals("", viewModel.uiState.password)
        assertNull(viewModel.uiState.usernameError)
        assertNull(viewModel.uiState.passwordError)
        assertFalse(viewModel.uiState.isPasswordVisible)
    }

    @Test
    fun onUsernameChange_updatesUsername() {
        val viewModel = LoginFormViewModel()

        viewModel.onUsernameChange("testuser")

        assertEquals("testuser", viewModel.uiState.username)
    }

    @Test
    fun onUsernameChange_clearsUsernameError() {
        val viewModel = LoginFormViewModel()

        // Trigger validation error
        viewModel.validateFields("Error", "Invalid", "Password error")
        assertNotNull(viewModel.uiState.usernameError)

        // Change username should clear error
        viewModel.onUsernameChange("t")
        assertNull(viewModel.uiState.usernameError)
    }

    @Test
    fun onPasswordChange_updatesPassword() {
        val viewModel = LoginFormViewModel()

        viewModel.onPasswordChange("password123")

        assertEquals("password123", viewModel.uiState.password)
    }

    @Test
    fun onPasswordChange_clearsPasswordError() {
        val viewModel = LoginFormViewModel()

        // Trigger validation error
        viewModel.validateFields("Username error", "Invalid", "Error")
        assertNotNull(viewModel.uiState.passwordError)

        // Change password should clear error
        viewModel.onPasswordChange("p")
        assertNull(viewModel.uiState.passwordError)
    }

    @Test
    fun onTogglePasswordVisibility_togglesState() {
        val viewModel = LoginFormViewModel()

        assertFalse(viewModel.uiState.isPasswordVisible)

        viewModel.onTogglePasswordVisibility()
        assertTrue(viewModel.uiState.isPasswordVisible)

        viewModel.onTogglePasswordVisibility()
        assertFalse(viewModel.uiState.isPasswordVisible)
    }

    @Test
    fun validateFields_emptyUsername_showsError() {
        val viewModel = LoginFormViewModel()

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Invalid format",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertFalse(isValid)
        assertEquals("Username cannot be empty", viewModel.uiState.usernameError)
    }

    @Test
    fun validateFields_emptyPassword_showsError() {
        val viewModel = LoginFormViewModel()
        viewModel.onUsernameChange("test@hb.dhbw-stuttgart.de")

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Invalid format",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertFalse(isValid)
        assertEquals("Password cannot be empty", viewModel.uiState.passwordError)
    }

    @Test
    fun validateFields_bothEmpty_showsBothErrors() {
        val viewModel = LoginFormViewModel()

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Invalid format",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertFalse(isValid)
        assertEquals("Username cannot be empty", viewModel.uiState.usernameError)
        assertEquals("Password cannot be empty", viewModel.uiState.passwordError)
    }

    @Test
    fun validateFields_invalidEmailFormat_showsError() {
        val viewModel = LoginFormViewModel()
        viewModel.onUsernameChange("invalid-email")
        viewModel.onPasswordChange("password123")

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Username must be valid email",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertFalse(isValid)
        assertEquals("Username must be valid email", viewModel.uiState.usernameError)
    }

    @Test
    fun validateFields_validDHBWEmail_passes() {
        val viewModel = LoginFormViewModel()
        viewModel.onUsernameChange("student123@hb.dhbw-stuttgart.de")
        viewModel.onPasswordChange("password123")

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Username must be valid email",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertTrue(isValid)
        assertNull(viewModel.uiState.usernameError)
        assertNull(viewModel.uiState.passwordError)
    }

    @Test
    fun validateFields_emailWithoutDHBWDomain_fails() {
        val viewModel = LoginFormViewModel()
        viewModel.onUsernameChange("test@gmail.com")
        viewModel.onPasswordChange("password123")

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Username must be valid email",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertFalse(isValid)
        assertEquals("Username must be valid email", viewModel.uiState.usernameError)
    }

    @Test
    fun validateFields_alphanumericUsername_passes() {
        val viewModel = LoginFormViewModel()
        viewModel.onUsernameChange("abc123@hb.dhbw-stuttgart.de")
        viewModel.onPasswordChange("password123")

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Username must be valid email",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertTrue(isValid)
    }

    @Test
    fun validateFields_specialCharactersInUsername_fails() {
        val viewModel = LoginFormViewModel()
        viewModel.onUsernameChange("test.user@hb.dhbw-stuttgart.de")
        viewModel.onPasswordChange("password123")

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Username must be valid email",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertFalse(isValid)
        assertEquals("Username must be valid email", viewModel.uiState.usernameError)
    }

    @Test
    fun clearForm_resetsAllState() {
        val viewModel = LoginFormViewModel()

        // Set some state
        viewModel.onUsernameChange("test@hb.dhbw-stuttgart.de")
        viewModel.onPasswordChange("password123")
        viewModel.onTogglePasswordVisibility()
        viewModel.validateFields("", "", "")

        // Clear form
        viewModel.clearForm()

        // Verify everything is reset
        assertEquals("", viewModel.uiState.username)
        assertEquals("", viewModel.uiState.password)
        assertNull(viewModel.uiState.usernameError)
        assertNull(viewModel.uiState.passwordError)
        assertFalse(viewModel.uiState.isPasswordVisible)
    }

    @Test
    fun validateFields_whitespaceOnlyUsername_treatedAsEmpty() {
        val viewModel = LoginFormViewModel()
        viewModel.onUsernameChange("   ")
        viewModel.onPasswordChange("password123")

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Invalid format",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertFalse(isValid)
        assertEquals("Username cannot be empty", viewModel.uiState.usernameError)
    }

    @Test
    fun validateFields_whitespaceOnlyPassword_treatedAsEmpty() {
        val viewModel = LoginFormViewModel()
        viewModel.onUsernameChange("test@hb.dhbw-stuttgart.de")
        viewModel.onPasswordChange("   ")

        val isValid = viewModel.validateFields(
            usernameCannotBeEmpty = "Username cannot be empty",
            usernameInvalidFormat = "Invalid format",
            passwordCannotBeEmpty = "Password cannot be empty"
        )

        assertFalse(isValid)
        assertEquals("Password cannot be empty", viewModel.uiState.passwordError)
    }
}