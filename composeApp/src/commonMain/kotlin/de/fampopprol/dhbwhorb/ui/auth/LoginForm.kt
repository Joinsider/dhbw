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
import de.fampopprol.dhbwhorb.presentation.auth.AuthIntent
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.auth.PasswordError
import de.fampopprol.dhbwhorb.presentation.auth.UsernameError
import de.fampopprol.dhbwhorb.ui.error.toUserMessage
import de.fampopprol.dhbwhorb.ui.store.collectState
import org.koin.compose.koinInject
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun LoginForm(
    store: AuthStore = koinInject()
) {
    val state by store.collectState()
    val focusManager = LocalFocusManager.current
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    var isUsernameFocused by remember { mutableStateOf(false) }
    var isPasswordFocused by remember { mutableStateOf(false) }

    val hapticFeedback = LocalHapticFeedback.current

    // Validation, the request and the result all happen in the store; this only asks for it.
    val performLogin: () -> Unit = {
        focusManager.clearFocus()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        store.dispatch(AuthIntent.Submitted)
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
            value = state.username,
            onValueChange = { store.dispatch(AuthIntent.UsernameChanged(it)) },
            label = { Text(stringResource(Res.string.username)) },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.enter_username)) },
            isError = state.usernameError != null,
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
                if (isUsernameFocused && state.username.isNotEmpty()) {
                    IconButton(
                        onClick = { store.dispatch(AuthIntent.UsernameChanged("")) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.cancel)
                        )
                    }
                }
            },
            supportingText = {
                state.usernameError?.let {
                    Text(
                        text = it.toMessage(), color = MaterialTheme.colorScheme.error
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
            value = state.password,
            onValueChange = { store.dispatch(AuthIntent.PasswordChanged(it)) },
            label = { Text(stringResource(Res.string.password)) },
            placeholder = { Text(stringResource(Res.string.enter_password)) },
            isError = state.passwordError != null,
            singleLine = true,
            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                if (isPasswordFocused && state.password.isNotEmpty()) {
                    IconButton(
                        onClick = { store.dispatch(AuthIntent.PasswordVisibilityToggled) }) {
                        Icon(
                            imageVector = if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.isPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                }
            },
            supportingText = {
                state.passwordError?.let { error ->
                    Text(
                        text = error.toMessage(), color = MaterialTheme.colorScheme.error
                    )
                }
            })

        Spacer(modifier = Modifier.height(8.dp))

        // Show login error if any
        state.loginError?.let { error ->
            Text(
                text = error.toUserMessage(),
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
            enabled = !state.isSubmitting,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (state.isSubmitting) {
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

/** The field errors are enums in the store; the words belong here. */
@Composable
private fun UsernameError.toMessage(): String = when (this) {
    UsernameError.Empty -> stringResource(Res.string.username_cannot_be_empty)
    UsernameError.NotADhbwAddress -> stringResource(Res.string.username_must_be_valid_email)
}

@Composable
private fun PasswordError.toMessage(): String = when (this) {
    PasswordError.Empty -> stringResource(Res.string.password_cannot_be_empty)
}
