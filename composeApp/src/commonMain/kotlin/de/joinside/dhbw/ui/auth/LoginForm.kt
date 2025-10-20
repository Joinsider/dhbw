package de.joinside.dhbw.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.enter_password
import de.joinside.dhbw.resources.enter_username
import de.joinside.dhbw.resources.login
import de.joinside.dhbw.resources.login_successful
import de.joinside.dhbw.resources.password
import de.joinside.dhbw.resources.password_cannot_be_empty
import de.joinside.dhbw.resources.username
import de.joinside.dhbw.resources.username_cannot_be_empty
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginForm() {
    val usernameFieldValue = remember { mutableStateOf(TextFieldValue("")) }
    val passwordFieldState = remember { mutableStateOf(TextFieldValue("")) }

    val usernameError = remember { mutableStateOf<String?>(null) }
    val passwordError = remember { mutableStateOf<String?>(null) }

    val usernameCannotBeEmpty = stringResource(Res.string.username_cannot_be_empty)
    val passwordCannotBeEmpty = stringResource(Res.string.password_cannot_be_empty)
    val loginSuccessfulText = stringResource(Res.string.login_successful)
    val usernameText = stringResource(Res.string.username)

    val validateFields = {
        var isValid = true

        if (usernameFieldValue.value.text.isBlank()) {
            usernameError.value = usernameCannotBeEmpty
            isValid = false
        } else {
            usernameError.value = null
        }

        if (passwordFieldState.value.text.isBlank()) {
            passwordError.value = passwordCannotBeEmpty
            isValid = false
        } else {
            passwordError.value = null
        }

        isValid
    }

    Column(
        modifier = Modifier
            .testTag("loginForm")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        TextField(
            modifier = Modifier.testTag("usernameField"),
            value = usernameFieldValue.value,
            onValueChange = {
                usernameFieldValue.value = it
                // Clear error when user starts typing
                if (usernameError.value != null) {
                    usernameError.value = null
                }
            },
            label = { Text(stringResource(Res.string.username)) },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.enter_username)) },
            isError = usernameError.value != null,
            supportingText = {
                usernameError.value?.let {
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
            value = passwordFieldState.value,
            onValueChange = {
                passwordFieldState.value = it
                // Clear error when user starts typing
                if (passwordError.value != null) {
                    passwordError.value = null
                }
            },
            label = { Text(stringResource(Res.string.password)) },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.enter_password)) },
            isError = passwordError.value != null,
            supportingText = {
                passwordError.value?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (validateFields()) {
                    // TODO: Add login logic here
                    println("$loginSuccessfulText! $usernameText: ${usernameFieldValue.value.text}")
                }
            },
            modifier = Modifier
                .testTag("loginButton")
                .fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(stringResource(Res.string.login))
        }
    }
}