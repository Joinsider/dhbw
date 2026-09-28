/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import kotlin.test.Test

/**
 * The login screen normally explains nothing — the user came to log in. The exception is a closed
 * automatic-login guard, which puts the screen up unasked; only then does it say why.
 */
@OptIn(ExperimentalTestApi::class)
class LoginPageTest {

    private val testViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Test
    fun aRegularLogin_showsNoNotice() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { Column { LoginPage() } }
            }
        }

        onNodeWithTag("appTitle").assertIsDisplayed()
        onNodeWithTag("loginForm").assertIsDisplayed()
        onNodeWithTag("reLoginRequiredText").assertDoesNotExist()
    }

    @Test
    fun aClosedAutoLoginGuard_explainsWhyTheLoginIsBack() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { Column { LoginPage(reLoginRequired = true) } }
            }
        }

        onNodeWithTag("reLoginRequiredText").assertIsDisplayed()
        onNodeWithTag("loginForm").assertIsDisplayed()
    }
}
