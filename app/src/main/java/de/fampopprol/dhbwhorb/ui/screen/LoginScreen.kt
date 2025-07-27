/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import de.fampopprol.dhbwhorb.ui.screen.loginScreen.LoginCard
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    dualisService: DualisService,
    credentialManager: CredentialManager,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(credentialManager) {
        credentialManager.usernameFlow.collect { storedUsername ->
            if (storedUsername != null) {
                username = storedUsername
            }
        }
    }

    // Auto-fill password if credentials are stored
    LaunchedEffect(credentialManager) {
        val storedPassword = credentialManager.getPassword()
        if (storedPassword != null) {
            password = storedPassword
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoginCard(
            username = username,
            password = password,
            passwordVisible = passwordVisible,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onUsernameChange = {
                username = it
                errorMessage = null // Clear error when user types
            },
            onPasswordChange = {
                password = it
                errorMessage = null // Clear error when user types
            },
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
            onLoginClick = {
                isLoading = true
                errorMessage = null
                scope.launch {
                    dualisService.login(username, password) { result ->
                        isLoading = false
                        if (result != null) {
                            scope.launch {
                                // Always save credentials on successful login
                                credentialManager.saveCredentials(username, password)
                                onLoginSuccess()
                                Log.d("LoginScreen", "Login successful, credentials saved")
                            }
                        } else {
                            errorMessage = "Login failed. Please check your credentials."
                            Log.e("LoginScreen", "Login failed")
                        }
                    }
                }
            }
        )
    }
}