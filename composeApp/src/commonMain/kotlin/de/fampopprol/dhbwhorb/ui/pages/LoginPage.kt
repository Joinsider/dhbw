package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.app_name
import de.fampopprol.dhbwhorb.resources.relogin_required
import de.fampopprol.dhbwhorb.ui.auth.LoginForm
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * @param reLoginRequired Dualis rejected the stored password and the app stopped logging in by
 *   itself — the one case in which the login screen appears without the user having asked for it,
 *   so it says why.
 */
@Composable
@Preview
fun LoginPage(reLoginRequired: Boolean = false) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("appTitle"),
        text = stringResource(Res.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground
    )

    if (reLoginRequired) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("reLoginRequiredText"),
            text = stringResource(Res.string.relogin_required),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
    }

    LoginForm()
}