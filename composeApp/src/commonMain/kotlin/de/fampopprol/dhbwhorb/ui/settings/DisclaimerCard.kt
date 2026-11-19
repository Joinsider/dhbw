// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.disclaimer_complaints
import de.fampopprol.dhbwhorb.resources.disclaimer_open_issues
import de.fampopprol.dhbwhorb.resources.disclaimer_title
import de.fampopprol.dhbwhorb.resources.disclaimer_unofficial
import org.jetbrains.compose.resources.stringResource

/** Where complaints about the app belong — the project's issue tracker, not the university. */
const val GITHUB_ISSUES_URL = "https://github.com/Joinsider/dhbw/issues"

/**
 * States that the app is a private project and not DHBW's, and sends complaints to GitHub. The
 * login screen carries the first sentence only ([UnofficialAppNotice]); here it comes with the link.
 */
@Composable
fun DisclaimerCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("disclaimerCard"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.disclaimer_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = stringResource(Res.string.disclaimer_unofficial),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("disclaimerUnofficialText")
            )
            Text(
                text = stringResource(Res.string.disclaimer_complaints),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("disclaimerComplaintsText")
            )
            OutlinedButton(
                onClick = { uriHandler.openUri(GITHUB_ISSUES_URL) },
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .testTag("disclaimerIssuesButton"),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(Res.string.disclaimer_open_issues))
            }
        }
    }
}

/** The one-line form of [DisclaimerCard] for the login screen. */
@Composable
fun UnofficialAppNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.disclaimer_unofficial),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.testTag("unofficialAppNotice")
    )
}
