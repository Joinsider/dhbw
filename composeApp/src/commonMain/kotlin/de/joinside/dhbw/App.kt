package de.joinside.dhbw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import de.joinside.dhbw.data.storage.credentials.CredentialsStorageProvider
import de.joinside.dhbw.data.storage.credentials.SecureStorage
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.app_name
import de.joinside.dhbw.resources.login_with_dualis_account
import de.joinside.dhbw.ui.auth.LoginForm
import de.joinside.dhbw.ui.auth.LoginFormResultPage
import de.joinside.dhbw.ui.theme.DHBWHorbTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class AppScreen {
    WELCOME,
    LOGIN,
    RESULT
}

@Composable
@Preview
fun App() {
    // Initialize SecureStorage and CredentialsProvider
    val secureStorage = remember { SecureStorage() }
    val credentialsProvider = remember { CredentialsStorageProvider(secureStorage) }

    // Navigation state
    var currentScreen by remember { mutableStateOf(AppScreen.WELCOME) }
    var isLoggedIn by remember { mutableStateOf(false) }

    // Session check on startup
    LaunchedEffect(Unit) {
        isLoggedIn = credentialsProvider.hasStoredCredentials()
        if (isLoggedIn) {
            currentScreen = AppScreen.RESULT
        }
    }

    DHBWHorbTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .background(MaterialTheme.colorScheme.background)
                .testTag("appContainer"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (currentScreen) {
                AppScreen.WELCOME -> {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("appTitle"),
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Button(
                        onClick = { currentScreen = AppScreen.LOGIN },
                        modifier = Modifier.testTag("loginWithDualisButton")
                    ) {
                        Text(text = stringResource(Res.string.login_with_dualis_account))
                    }
                }

                AppScreen.LOGIN -> {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("appTitle"),
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    LoginForm(
                        credentialsProvider = credentialsProvider,
                        onLoginSuccess = {
                            isLoggedIn = true
                            currentScreen = AppScreen.RESULT
                        }
                    )
                }

                AppScreen.RESULT -> {
                    LoginFormResultPage(
                        credentialsProvider = credentialsProvider,
                        onLogout = {
                            isLoggedIn = false
                            currentScreen = AppScreen.WELCOME
                        }
                    )
                }
            }
        }
    }
}