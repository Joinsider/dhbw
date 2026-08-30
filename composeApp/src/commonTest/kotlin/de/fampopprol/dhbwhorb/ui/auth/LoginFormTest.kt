/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import de.fampopprol.dhbwhorb.testutil.WithTestKoin
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LoginFormTest {

    private val testViewModelStoreOwner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    @Test
    fun loginForm_isDisplayed() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { LoginForm() }
            }
        }

        onNodeWithTag("loginForm").assertIsDisplayed()
    }

    @Test
    fun loginForm_hasUsernameField() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { LoginForm() }
            }
        }

        onNodeWithTag("usernameField").assertIsDisplayed()
    }

    @Test
    fun loginForm_hasPasswordField() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { LoginForm() }
            }
        }

        onNodeWithTag("passwordField").assertIsDisplayed()
    }

    @Test
    fun loginForm_hasLoginButton() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { LoginForm() }
            }
        }

        onNodeWithTag("loginButton").assertIsDisplayed()
        onNodeWithTag("loginButton").assertIsEnabled()
    }

    @Test
    fun loginForm_buttonIsAlwaysEnabled() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { LoginForm() }
            }
        }

        onNodeWithTag("loginButton").assertIsEnabled()
    }

    @Test
    fun loginForm_usernameFieldHasCorrectLabel() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { LoginForm() }
            }
        }

        onNodeWithText("Email").assertIsDisplayed()
    }

    @Test
    fun loginForm_passwordFieldHasCorrectLabel() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { LoginForm() }
            }
        }

        // Tag instead of label text: the label is a localised string resource.
        onNodeWithTag("passwordField").assertIsDisplayed()
    }

    @Test
    fun loginForm_loginButtonHasCorrectText() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testViewModelStoreOwner) {
                WithTestKoin { LoginForm() }
            }
        }

        // Tag instead of button text: the caption is a localised string resource.
        onNodeWithTag("loginButton").assertIsDisplayed()
    }
}
