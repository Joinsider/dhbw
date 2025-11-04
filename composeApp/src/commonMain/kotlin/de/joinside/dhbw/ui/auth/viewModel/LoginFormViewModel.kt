package de.joinside.dhbw.ui.auth.viewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// Represents the state of the login form
data class LoginFormUiState(
    val username: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val isPasswordVisible: Boolean = false
)

class LoginFormViewModel : ViewModel() {

    // Expose the state to the UI, making it observable
    var uiState by mutableStateOf(LoginFormUiState())
        private set // Only the ViewModel can change the state

    // Event handler for when the username changes
    fun onUsernameChange(newValue: String) {
        uiState = uiState.copy(
            username = newValue,
            // Clear the error as soon as the user starts typing
            usernameError = null
        )
    }

    // Event handler for when the password changes
    fun onPasswordChange(newValue: String) {
        uiState = uiState.copy(
            password = newValue,
            // Clear the error as soon as the user starts typing
            passwordError = null
        )
    }

    // Event handler for toggling password visibility
    fun onTogglePasswordVisibility() {
        uiState = uiState.copy(isPasswordVisible = !uiState.isPasswordVisible)
    }

    // Validate the form fields
    fun validateFields(
        usernameCannotBeEmpty: String,
        usernameInvalidFormat: String,
        passwordCannotBeEmpty: String
    ): Boolean {
        var isValid = true

        val pattern = Regex("[a-zA-Z0-9]+@hb.dhbw-stuttgart.de")

        // Validate username
        when {
            uiState.username.isBlank() -> {
                uiState = uiState.copy(usernameError = usernameCannotBeEmpty)
                isValid = false
            }
            !pattern.matches(uiState.username.lowercase()) -> {
                uiState = uiState.copy(usernameError = usernameInvalidFormat)
                isValid = false
            }
            else -> {
                uiState = uiState.copy(usernameError = null)
            }
        }

        // Validate password
        if (uiState.password.isBlank()) {
            uiState = uiState.copy(passwordError = passwordCannotBeEmpty)
            isValid = false
        } else {
            uiState = uiState.copy(passwordError = null)
        }

        return isValid
    }

    // Clear all form data
    fun clearForm() {
        uiState = LoginFormUiState()
    }
}

