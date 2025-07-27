/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.loginScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoginCard(
    username: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginTitle()

            Spacer(modifier = Modifier.height(32.dp))

            UsernameField(
                value = username,
                onValueChange = onUsernameChange,
                isLoading = isLoading,
                hasError = errorMessage != null
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordField(
                value = password,
                onValueChange = onPasswordChange,
                passwordVisible = passwordVisible,
                onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                isLoading = isLoading,
                hasError = errorMessage != null,
                onDone = {
                    if (username.isNotBlank() && password.isNotBlank() && !isLoading) {
                        onLoginClick()
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            LoginButton(
                isLoading = isLoading,
                isEnabled = username.isNotBlank() && password.isNotBlank(),
                onClick = onLoginClick
            )

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                ErrorMessage(error = error)
            }
        }
    }
}
