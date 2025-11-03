package de.joinside.dhbw.ui.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import de.joinside.dhbw.AppScreen
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.app_name
import de.joinside.dhbw.resources.login_with_dualis_account
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun Startpage(
    navigateToLoginPage: () -> Unit = {}
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

    Button(
        onClick = { navigateToLoginPage() },
        modifier = Modifier.testTag("loginWithDualisButton")
    ) {
        Text(text = stringResource(Res.string.login_with_dualis_account))
    }
}