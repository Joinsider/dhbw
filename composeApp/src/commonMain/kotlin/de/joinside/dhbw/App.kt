package de.joinside.dhbw

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.style.TextAlign
import de.joinside.dhbw.i18n.ProvideStrings
import de.joinside.dhbw.i18n.rememberLanguageManager
import de.joinside.dhbw.i18n.strings
import de.joinside.dhbw.ui.auth.LoginForm
import de.joinside.dhbw.ui.theme.DHBWHorbTheme
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
@Preview
fun App() {
    val languageManager = rememberLanguageManager()
    val currentLanguage = remember { languageManager.getCurrentLanguage() }

    DHBWHorbTheme {
        ProvideStrings(language = currentLanguage) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val strings = strings()

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = strings.appName,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                /*
                Image(
                    painter = painterResource(),
                    contentDescription = "DHBW Horb Logo",
                    modifier = Modifier
                        .fillMaxWidth()
                )

                 */

                var showLoginForm by remember { mutableStateOf(false) }

                AnimatedVisibility(visible = !showLoginForm) {
                    Button(
                        onClick = { showLoginForm = true }
                    ) {
                        Text(text = strings.loginWithDualisAccount)
                    }
                }

                AnimatedVisibility(visible = showLoginForm) {
                    LoginForm()
                }
            }
        }
    }
}