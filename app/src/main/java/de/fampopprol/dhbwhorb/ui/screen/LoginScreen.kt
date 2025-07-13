package de.fampopprol.dhbwhorb.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
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
        // Login Card with Material 3 elevated surface
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Enhanced title with Material 3 typography
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Enhanced username field with leading icon
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null // Clear error when user types
                    },
                    label = { Text(stringResource(R.string.username)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Enhanced password field with visibility toggle
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null // Clear error when user types
                    },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    isError = errorMessage != null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Enhanced login button with loading state
                Button(
                    onClick = {
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
                    },
                    enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(20.dp)
                                .width(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isLoading) stringResource(R.string.logging_in) else stringResource(
                            R.string.login
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Error message with better styling
                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}