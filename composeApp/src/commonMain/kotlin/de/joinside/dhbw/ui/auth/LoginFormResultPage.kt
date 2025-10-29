package de.joinside.dhbw.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.storage.credentials.CredentialsStorageProvider
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun LoginFormResultPage(
    credentialsProvider: CredentialsStorageProvider,
    onLogout: () -> Unit,
    authService: AuthenticationService
) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var authData by remember { mutableStateOf<de.joinside.dhbw.data.dualis.remote.models.AuthData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Check authentication status on composition
    LaunchedEffect(Unit) {
        println("LoginFormResultPage: Starting authentication check")
        isLoggedIn = authService.isAuthenticated()
        println("LoginFormResultPage: isLoggedIn = $isLoggedIn")
        isLoading = false

        if (isLoggedIn) {
            // Get session data
            val sessionManager = authService.sessionManager
            authData = sessionManager.getAuthData()
            println("LoginFormResultPage: Retrieved authData from SessionManager")
            println("LoginFormResultPage: authData = $authData")
            println("LoginFormResultPage: authData.userFullName = ${authData?.userFullName}")

            username = sessionManager.getStoredCredentials()?.first ?: credentialsProvider.getUsername()
            println("LoginFormResultPage: username = $username")
        } else {
            println("LoginFormResultPage: User is not logged in")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("loginFormResultPage"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.testTag("loadingIndicator")
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Checking authentication...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else if (isLoggedIn) {
            // Success state - user is authenticated
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .width(64.dp)
                    .height(64.dp)
                    .testTag("successIcon")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Authentication Successful",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("credentialsStoredText")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Username: $username",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("usernameDisplayText")
            )

            // Display user's full name if available
            authData?.userFullName?.let { fullName ->
                println("LoginFormResultPage: Displaying full name: '$fullName'")
                Text(
                    text = "Name: $fullName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("fullNameDisplayText")
                )
            } ?: run {
                println("LoginFormResultPage: Full name is null, not displaying")
            }

            Text(
                text = "Password: ********",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("passwordDisplayText")
            )

            authData?.let { data ->
                Spacer(modifier = Modifier.height(8.dp))

                if (data.sessionId.isNotEmpty()) {
                    Text(
                        text = "Session ID: ${data.sessionId.take(10)}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }

                if (data.authToken.isNotEmpty()) {
                    Text(
                        text = "Auth Token: ${data.authToken.take(10)}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                text = "Dualis Login: Successful",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("dualisLoginStatusText")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    credentialsProvider.clearCredentials()
                    onLogout()
                },
                modifier = Modifier
                    .testTag("logoutButton")
                    .width(250.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Logout")
            }
        } else {
            // No credentials stored state
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "No Credentials",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .width(64.dp)
                    .height(64.dp)
                    .testTag("warningIcon")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Credentials Stored",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("noCredentialsText")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Please login to store your credentials",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("loginPromptText")
            )
        }
    }
}

