// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.disclaimer_complaints
import de.fampopprol.dhbwhorb.resources.disclaimer_unofficial
import org.jetbrains.compose.resources.stringResource

/** Where complaints about the app belong — the project's issue tracker, not the university. */
const val GITHUB_ISSUES_URL = "https://github.com/Joinsider/dhbw/issues"

/**
 * The settings form of the disclaimer: not DHBW's app, and complaints go to GitHub. It sits in
 * [HelpSelectionCard] directly above the GitHub Issues button, which is the link it refers to —
 * a card of its own would need a second button to the same page.
 */
@Composable
fun DisclaimerText(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag("disclaimerText"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.disclaimer_unofficial),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("disclaimerUnofficialText")
        )
        Text(
            text = stringResource(Res.string.disclaimer_complaints),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("disclaimerComplaintsText")
        )
    }
}

/** The one-line form for the login screen. */
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
