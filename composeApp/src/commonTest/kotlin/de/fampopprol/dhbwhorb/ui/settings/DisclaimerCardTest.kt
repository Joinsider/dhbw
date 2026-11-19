/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tags, not texts: the strings are localised and the machine's locale would decide the result. */
@OptIn(ExperimentalTestApi::class)
class DisclaimerCardTest {

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) { opened += uri }
    }

    @Test
    fun showsBothNoticesAndTheIssuesButton() = runComposeUiTest {
        setContent { DisclaimerCard() }

        onNodeWithTag("disclaimerUnofficialText").assertIsDisplayed()
        onNodeWithTag("disclaimerComplaintsText").assertIsDisplayed()
        onNodeWithTag("disclaimerIssuesButton").assertIsDisplayed()
    }

    @Test
    fun theButtonOpensTheGithubIssues() = runComposeUiTest {
        val handler = RecordingUriHandler()
        setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) { DisclaimerCard() }
        }

        onNodeWithTag("disclaimerIssuesButton").performClick()
        waitForIdle()

        assertEquals(listOf("https://github.com/Joinsider/dhbw/issues"), handler.opened)
    }
}
