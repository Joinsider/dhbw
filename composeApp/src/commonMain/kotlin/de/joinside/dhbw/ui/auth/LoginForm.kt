package de.joinside.dhbw.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.dualis.remote.services.LoginResult
import de.joinside.dhbw.data.storage.credentials.CredentialsStorageProvider
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.enter_password
import de.joinside.dhbw.resources.enter_username
import de.joinside.dhbw.resources.login
import de.joinside.dhbw.resources.login_successful
import de.joinside.dhbw.resources.password
import de.joinside.dhbw.resources.password_cannot_be_empty
import de.joinside.dhbw.resources.username
import de.joinside.dhbw.resources.username_cannot_be_empty
import de.joinside.dhbw.resources.username_must_be_valid_email
import de.joinside.dhbw.ui.auth.viewModel.LoginFormViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
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

    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    val usernameCannotBeEmpty = stringResource(Res.string.username_cannot_be_empty)
    val usernameInvalidFormat = stringResource(Res.string.username_must_be_valid_email)
    val passwordCannotBeEmpty = stringResource(Res.string.password_cannot_be_empty)
    val loginSuccessfulText = stringResource(Res.string.login_successful)
    val usernameText = stringResource(Res.string.username)

    Column(
        modifier = Modifier.testTag("loginForm").padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        TextField(
            modifier = Modifier.testTag("usernameField"),
            value = uiState.username,
            onValueChange = { viewModel.onUsernameChange(it) },
            label = { Text(stringResource(Res.string.username)) },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.enter_username)) },
            isError = uiState.usernameError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            supportingText = {
                uiState.usernameError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            modifier = Modifier.testTag("passwordField"),
            value = uiState.password,
            onValueChange = { viewModel.onPasswordChange(it) },
            label = { Text(stringResource(Res.string.password)) },
            placeholder = { Text(stringResource(Res.string.enter_password)) },
            isError = uiState.passwordError != null,
            singleLine = true,
            visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = {
                uiState.passwordError?.let { errorText ->
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

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
            onClick = {
                if (viewModel.validateFields(
                        usernameCannotBeEmpty = usernameCannotBeEmpty,
                        usernameInvalidFormat = usernameInvalidFormat,
                        passwordCannotBeEmpty = passwordCannotBeEmpty
                    )) {

                    // Use AuthenticationService if available, otherwise fall back to old behavior
                    if (authenticationService != null) {
                        isLoading = true
                        loginError = null

                        coroutineScope.launch {
                            val result = authenticationService.login(
                                username = uiState.username,
                                password = uiState.password
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
                            username = uiState.username,
                            password = uiState.password
                        )
                        println("$loginSuccessfulText! $usernameText: ${uiState.username}")
                        onLoginSuccess()
                    }
                }
            },
            modifier = Modifier.testTag("loginButton").padding(16.dp).width(300.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.width(24.dp).height(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(Res.string.login))
            }
        }
    }
}

