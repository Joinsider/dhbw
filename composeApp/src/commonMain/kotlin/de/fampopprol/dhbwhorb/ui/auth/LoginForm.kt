package de.fampopprol.dhbwhorb.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.LoginResult
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.cancel
import de.fampopprol.dhbwhorb.resources.enter_password
import de.fampopprol.dhbwhorb.resources.enter_username
import de.fampopprol.dhbwhorb.resources.login
import de.fampopprol.dhbwhorb.resources.login_successful
import de.fampopprol.dhbwhorb.resources.password
import de.fampopprol.dhbwhorb.resources.password_cannot_be_empty
import de.fampopprol.dhbwhorb.resources.username
import de.fampopprol.dhbwhorb.resources.username_cannot_be_empty
import de.fampopprol.dhbwhorb.resources.username_must_be_valid_email
import de.fampopprol.dhbwhorb.ui.auth.viewModel.LoginFormViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun LoginForm(
    authenticationService: AuthenticationService? = null,
    credentialsProvider: CredentialsStorageProvider? = null,
    onLoginSuccess: () -> Unit = {},
    viewModel: LoginFormViewModel = viewModel { LoginFormViewModel() }
) {
    val uiState = viewModel.uiState
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isUsernameFocused by remember { mutableStateOf(false) }
    var isPasswordFocused by remember { mutableStateOf(false) }

    val usernameCannotBeEmpty = stringResource(Res.string.username_cannot_be_empty)
    val usernameInvalidFormat = stringResource(Res.string.username_must_be_valid_email)
    val passwordCannotBeEmpty = stringResource(Res.string.password_cannot_be_empty)
    val loginSuccessfulText = stringResource(Res.string.login_successful)
    val usernameText = stringResource(Res.string.username)

    val hapticFeedback = LocalHapticFeedback.current

    // Extract login logic into a function that can be reused
    val performLogin: () -> Unit = {
        // Clear focus to ensure UI updates properly
        focusManager.clearFocus()

        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        if (viewModel.validateFields(
                usernameCannotBeEmpty = usernameCannotBeEmpty,
                usernameInvalidFormat = usernameInvalidFormat,
                passwordCannotBeEmpty = passwordCannotBeEmpty
            )
        ) {
            // Use AuthenticationService if available, otherwise fall back to old behavior
            if (authenticationService != null) {
                isLoading = true
                loginError = null

                coroutineScope.launch {
                    val result = authenticationService.login(
                        username = uiState.username, password = uiState.password
                    )

                    isLoading = false

                    when (result) {
                        is LoginResult.Success -> {
                            println("$loginSuccessfulText! $usernameText: ${uiState.username}")
                            onLoginSuccess()
                        }

                        is LoginResult.Failure -> {
                            loginError = result.message
                        }
                    }
                }
            } else {
                // Fallback: Store credentials only (for backward compatibility)
                credentialsProvider?.storeCredentials(
                    username = uiState.username, password = uiState.password
                )
                println("$loginSuccessfulText! $usernameText: ${uiState.username}")
                onLoginSuccess()
            }
        }
    }

    Column(
        modifier = Modifier.testTag("loginForm").padding(16.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("usernameField")
                .focusRequester(usernameFocusRequester)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Tab) {
                        passwordFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                }
                .onFocusChanged { focusState ->
                    isUsernameFocused = focusState.isFocused
                },
            value = uiState.username,
            onValueChange = { viewModel.onUsernameChange(it) },
            label = { Text(stringResource(Res.string.username)) },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.enter_username)) },
            isError = uiState.usernameError != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() }
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = stringResource(Res.string.username)
                )
            },
            trailingIcon = {
                if (isUsernameFocused && uiState.username.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onUsernameChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.cancel)
                        )
                    }
                }
            },
            supportingText = {
                uiState.usernameError?.let {
                    Text(
                        text = it, color = MaterialTheme.colorScheme.error
                    )
                }
            })

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("passwordField")
                .focusRequester(passwordFocusRequester)
                .onKeyEvent { keyEvent ->
                    when (keyEvent.type) {
                        KeyEventType.KeyDown if keyEvent.key == Key.Tab -> {
                            // Shift+Tab to go back to username field
                            if (keyEvent.isShiftPressed) {
                                usernameFocusRequester.requestFocus()
                            }
                            true
                        }
                        KeyEventType.KeyDown if keyEvent.key == Key.Enter -> {
                            // Enter to trigger login
                            performLogin()
                            true
                        }
                        else -> false
                    }
                }
                .onFocusChanged { focusState ->
                    isPasswordFocused = focusState.isFocused
                },
            value = uiState.password,
            onValueChange = { viewModel.onPasswordChange(it) },
            label = { Text(stringResource(Res.string.password)) },
            placeholder = { Text(stringResource(Res.string.enter_password)) },
            isError = uiState.passwordError != null,
            singleLine = true,
            visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { performLogin() }
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = stringResource(Res.string.password)
                )
            },
            trailingIcon = {
                if (isPasswordFocused && uiState.password.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onTogglePasswordVisibility() }) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (uiState.isPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            },
            supportingText = {
                uiState.passwordError?.let { errorText ->
                    Text(
                        text = errorText, color = MaterialTheme.colorScheme.error
                    )
                }
            })

        Spacer(modifier = Modifier.height(8.dp))

        // Show login error if any
        loginError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("loginErrorText")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = performLogin,
            modifier = Modifier.testTag("loginButton")
                .padding(8.dp)
                .height(48.dp)
                .width(150.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isLoading) {
                LoadingIndicator(
                    modifier = Modifier.width(24.dp).height(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(Res.string.login))
            }
        }
    }
}

