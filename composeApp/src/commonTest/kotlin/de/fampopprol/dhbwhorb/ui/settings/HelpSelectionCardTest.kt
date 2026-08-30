/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Uses tags rather than the button labels: they're localised `stringResource`s, and this test
 * would otherwise pass or fail depending on the machine's system locale — see
 * `reminderLeadTestTag`'s doc comment for the same trap elsewhere in this codebase.
 */
@OptIn(ExperimentalTestApi::class)
class HelpSelectionCardTest {

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) { opened += uri }
    }

    /** [showRowLayout] controls the container width BoxWithConstraints reads to pick Row vs Column. */
    private fun setUpCard(
        showRowLayout: Boolean,
        onLogout: () -> Unit = {},
        showLogout: Boolean = true,
        uriHandler: UriHandler = RecordingUriHandler(),
    ): (androidx.compose.ui.test.ComposeUiTest) -> Unit = { test ->
        test.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                if (showRowLayout) {
                    // The default test surface is already wider than the 600dp row/column
                    // threshold, so nothing needs constraining for this case.
                    HelpSelectionCard(onLogout = onLogout, showLogout = showLogout)
                } else {
                    Box(Modifier.width(300.dp)) {
                        HelpSelectionCard(onLogout = onLogout, showLogout = showLogout)
                    }
                }
            }
        }
        test.waitForIdle()
    }

    @Test
    fun wideLayout_showsPrivacyGithubAndLogoutButtons() = runComposeUiTest {
        setUpCard(showRowLayout = true)(this)

        onNodeWithTag("privacyPolicyButton").assertIsDisplayed()
        onNodeWithTag("githubIssuesButton").assertIsDisplayed()
        onNodeWithTag("logoutButton").assertIsDisplayed()
    }

    @Test
    fun narrowLayout_alsoShowsAllButtons() = runComposeUiTest {
        setUpCard(showRowLayout = false)(this)

        onNodeWithTag("privacyPolicyButton").assertIsDisplayed()
        onNodeWithTag("githubIssuesButton").assertIsDisplayed()
        onNodeWithTag("logoutButton").assertIsDisplayed()
    }

    @Test
    fun showLogoutFalse_hidesLogoutButton_inBothLayouts() = runComposeUiTest {
        setUpCard(showRowLayout = true, showLogout = false)(this)
        assertFailsWith<AssertionError> { onNodeWithTag("logoutButton").assertIsDisplayed() }
    }

    @Test
    fun clickingLogout_invokesTheCallback() = runComposeUiTest {
        var loggedOut = false
        setUpCard(showRowLayout = true, onLogout = { loggedOut = true })(this)

        onNodeWithTag("logoutButton").performClick()

        assertTrue(loggedOut)
    }

    @Test
    fun clickingPrivacyPolicy_opensTheUrl() = runComposeUiTest {
        val handler = RecordingUriHandler()
        setUpCard(showRowLayout = true, uriHandler = handler)(this)

        onNodeWithTag("privacyPolicyButton").performClick()

        assertEquals(listOf("https://www.datenschutz.dhbw.joinside.de"), handler.opened)
    }

    @Test
    fun clickingGithubIssues_opensTheUrl() = runComposeUiTest {
        val handler = RecordingUriHandler()
        setUpCard(showRowLayout = true, uriHandler = handler)(this)

        onNodeWithTag("githubIssuesButton").performClick()

        assertEquals(listOf("https://github.com/Joinsider/dhbw/issues/"), handler.opened)
    }
}
