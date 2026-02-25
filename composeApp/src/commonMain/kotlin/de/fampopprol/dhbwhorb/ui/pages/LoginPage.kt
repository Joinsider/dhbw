package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsStorageProvider
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.app_name
import de.fampopprol.dhbwhorb.ui.auth.LoginForm
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun Startpage(
    onLoginSuccess: () -> Unit = {},
    authenticationService: AuthenticationService,
    credentialsProvider: CredentialsStorageProvider,
) {


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
        authenticationService = authenticationService,
        credentialsProvider = credentialsProvider,
        onLoginSuccess = onLoginSuccess
    )
}