/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.navItemTestTag
import kotlin.test.Test

/**
 * The one routing decision that is not navigation: login screen or app.
 *
 * The pages used to take an `isLoggedIn` flag and hide their own navigation bar when it was false
 * — four screens each deciding half of the same question. There is one place for it now, and a
 * page only ever renders inside the logged-in graph.
 */
@OptIn(ExperimentalTestApi::class)
class AppRoutingTest {

    @Test
    fun withoutASession_theLoginScreenIsShown() = runComposeUiTest {
        // The mock graph has empty secure storage, so no session can be restored.
        setContent { WithTestKoin(authenticated = false) { App() } }
        waitForIdle()

        onNodeWithTag("loginForm").assertIsDisplayed()
        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertDoesNotExist()
    }

    @Test
    fun withASession_theAppGraphIsShown() = runComposeUiTest {
        setContent { WithTestKoin(authenticated = true) { App() } }
        waitForIdle()

        onNodeWithTag(navItemTestTag(BottomNavItem.TIMETABLE)).assertIsDisplayed()
        onNodeWithTag("loginForm").assertDoesNotExist()
    }
}
