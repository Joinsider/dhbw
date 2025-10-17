/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.i18n.Language
import de.joinside.dhbw.i18n.LanguageManager
import de.joinside.dhbw.i18n.strings

/**
 * A composable that displays a language selector dialog.
 */
@Composable
fun LanguageSelector(
    languageManager: LanguageManager,
    onDismiss: () -> Unit
) {
    val strings = strings()
    var selectedLanguage by remember { mutableStateOf(languageManager.getCurrentLanguage()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = strings.language,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            // System default option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = languageManager.isUsingSystemDefault(),
                    onClick = {
                        languageManager.resetToSystemDefault()
                        selectedLanguage = languageManager.getCurrentLanguage()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.systemDefault)
            }

            // Language options
            Language.entries.forEach { language ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !languageManager.isUsingSystemDefault() && selectedLanguage == language,
                        onClick = {
                            languageManager.setLanguage(language)
                            selectedLanguage = language
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(language.displayName)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(strings.ok)
            }
        }
    }
}

