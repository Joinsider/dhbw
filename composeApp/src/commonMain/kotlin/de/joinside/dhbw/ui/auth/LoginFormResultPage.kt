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
    var dualisLoginSuccessful by remember { mutableStateOf(false) }

    // Check credentials on composition and whenever they might change
    LaunchedEffect(Unit) {
        isLoggedIn = credentialsProvider.hasStoredCredentials()
        if (isLoggedIn) {
            username = credentialsProvider.getUsername()
        }

        // Login to Dualis API was successful
        dualisLoginSuccessful = authService.login(
            username = credentialsProvider.getUsername(),
            password = credentialsProvider.getPassword()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("loginFormResultPage"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoggedIn) {
            // Success state - credentials are stored
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
                text = "Credentials Stored",
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

            Text(
                text = "Password: ********",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("passwordDisplayText")
            )

            Text(
                text = if (dualisLoginSuccessful) {
                    "Dualis Login: Successful"
                } else {
                    "Dualis Login: Failed"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (dualisLoginSuccessful) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
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
