/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StartPageTest {

    @Test
    fun startpage_displaysTitleAndLoginButton() = runComposeUiTest {
        setContent { Startpage() }

        onNodeWithTag("appTitle").assertIsDisplayed()
        onNodeWithTag("loginWithDualisButton").assertIsDisplayed()
    }

    @Test
    fun startpage_clickingLoginButton_navigatesToLogin() = runComposeUiTest {
        var navigated = false
        setContent { Startpage(navigateToLoginPage = { navigated = true }) }

        onNodeWithTag("loginWithDualisButton").performClick()

        kotlin.test.assertTrue(navigated)
    }
}
