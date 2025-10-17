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
import de.joinside.dhbw.i18n.strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginForm() {
    val strings = strings()

    val usernameFieldValue = remember { mutableStateOf(TextFieldValue("")) }
    val passwordFieldState = remember { mutableStateOf(TextFieldValue("")) }

    val usernameError = remember { mutableStateOf<String?>(null) }
    val passwordError = remember { mutableStateOf<String?>(null) }

    val validateFields = {
        var isValid = true

        if (usernameFieldValue.value.text.isBlank()) {
            usernameError.value = strings.usernameCannotBeEmpty
            isValid = false
        } else {
            usernameError.value = null
        }

        if (passwordFieldState.value.text.isBlank()) {
            passwordError.value = strings.passwordCannotBeEmpty
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
            label = { Text(strings.username) },
            singleLine = true,
            placeholder = { Text(strings.enterUsername) },
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
            label = { Text(strings.password) },
            singleLine = true,
            placeholder = { Text(strings.enterPassword) },
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
                    println("${strings.loginSuccessful}! ${strings.username}: ${usernameFieldValue.value.text}")
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
            Text(strings.login)
        }
    }
}