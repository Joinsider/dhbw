package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import org.jetbrains.compose.resources.stringResource
import de.fampopprol.dhbwhorb.resources.help_settings
import de.fampopprol.dhbwhorb.resources.privacy_button
import de.fampopprol.dhbwhorb.resources.github_issues
import de.fampopprol.dhbwhorb.resources.logout
import de.fampopprol.dhbwhorb.resources.report_issue

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HelpSelectionCard(
    onLogout: () -> Unit,
    showLogout: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
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
                text = stringResource(Res.string.help_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            HelpActionButtons(onLogout = onLogout, showLogout = showLogout)
        }
    }
}

/** Buttons flow in a row on wide layouts and stack in a column on narrow ones. */
@Composable
private fun HelpActionButtons(onLogout: () -> Unit, showLogout: Boolean) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val useRowLayout = maxWidth > 600.dp

        if (useRowLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PrivacyPolicyButton(modifier = Modifier.weight(1f).height(48.dp))
                GithubIssuesButton(modifier = Modifier.weight(1f).height(48.dp))
                if (showLogout) {
                    // weight(1f), not fillMaxWidth(): a fillMaxWidth() sibling in a Row is measured
                    // before its weighted siblings and claims the whole row for itself, leaving the
                    // other two at zero width — invisible next to a logout button spanning the row.
                    LogoutButton(
                        onLogout = onLogout,
                        modifier = Modifier.testTag("logoutButton").weight(1f).height(48.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrivacyPolicyButton(modifier = Modifier.fillMaxWidth().height(48.dp))
                GithubIssuesButton(modifier = Modifier.fillMaxWidth().height(48.dp))
                if (showLogout) {
                    LogoutButton(
                        onLogout = onLogout,
                        modifier = Modifier.testTag("logoutButton").fillMaxWidth().height(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicyButton(modifier: Modifier = Modifier) {
    val hapticFeedback = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current

    Button(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            uriHandler.openUri("https://www.datenschutz.dhbw.joinside.de")
        },
        modifier = modifier.testTag("privacyPolicyButton"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        content = {
            Icon(
                imageVector = Icons.Default.PrivacyTip,
                contentDescription = stringResource(Res.string.privacy_button),
                modifier = Modifier
                    .padding(end = 8.dp)
            )
            Text(
                text = stringResource(Res.string.privacy_button),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

@Composable
private fun GithubIssuesButton(modifier: Modifier = Modifier) {
    val hapticFeedback = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current

    Button(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            uriHandler.openUri("https://github.com/Joinsider/dhbw/issues/")
        },
        modifier = modifier.testTag("githubIssuesButton"),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        content = {
            Icon(
                imageVector = Icons.Default.Commit,
                contentDescription = stringResource(Res.string.report_issue),
                modifier = Modifier
                    .padding(end = 8.dp)
            )
            Text(
                text = stringResource(Res.string.github_issues),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

/** Only meaningful while a session exists — callers gate this on `showLogout`. */
@Composable
private fun LogoutButton(onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val hapticFeedback = LocalHapticFeedback.current

    Button(
        onClick = {
            onLogout()
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = stringResource(Res.string.logout),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(stringResource(Res.string.logout))
    }
}