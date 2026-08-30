/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.error

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import de.fampopprol.dhbwhorb.core.error.AppError
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppErrorMessageTest {

    @Composable
    private fun ShowError(error: AppError) {
        Text(text = error.toUserMessage())
    }

    @Test
    fun offline_showsNetworkErrorMessage() = runComposeUiTest {
        setContent { ShowError(AppError.Offline) }
        waitForIdle()

        onNodeWithText("Network error. Please check your connection.").assertIsDisplayed()
    }

    @Test
    fun sessionExpired_showsSessionExpiredMessage() = runComposeUiTest {
        setContent { ShowError(AppError.SessionExpired) }
        waitForIdle()

        onNodeWithText("Your session has expired. Please log in again.").assertIsDisplayed()
    }

    @Test
    fun invalidCredentials_showsInvalidCredentialsMessage() = runComposeUiTest {
        setContent { ShowError(AppError.InvalidCredentials) }
        waitForIdle()

        onNodeWithText("Invalid username or password.").assertIsDisplayed()
    }

    @Test
    fun noCredentials_showsLoginRequiredMessage() = runComposeUiTest {
        setContent { ShowError(AppError.NoCredentials) }
        waitForIdle()

        onNodeWithText("Please log in to continue.").assertIsDisplayed()
    }

    @Test
    fun http_showsServerErrorMessageWithCode() = runComposeUiTest {
        setContent { ShowError(AppError.Http(503)) }
        waitForIdle()

        onNodeWithText("Dualis answered with an error (503).").assertIsDisplayed()
    }

    @Test
    fun parse_showsUnreadableResponseMessage() = runComposeUiTest {
        setContent { ShowError(AppError.Parse(source = "timetable", hint = "missing table")) }
        waitForIdle()

        onNodeWithText("Dualis answered with something the app could not read. It may have changed.")
            .assertIsDisplayed()
    }

    @Test
    fun storage_showsStorageErrorMessage() = runComposeUiTest {
        setContent { ShowError(AppError.Storage(hint = "disk full")) }
        waitForIdle()

        onNodeWithText("Local data could not be read or written.").assertIsDisplayed()
    }

    @Test
    fun unsupported_showsUnsupportedInDemoMessage() = runComposeUiTest {
        setContent { ShowError(AppError.Unsupported(hint = "downloads")) }
        waitForIdle()

        onNodeWithText("This is not available in demo mode.").assertIsDisplayed()
    }

    @Test
    fun unexpected_showsUnknownErrorMessage() = runComposeUiTest {
        setContent { ShowError(AppError.Unexpected(hint = "boom")) }
        waitForIdle()

        onNodeWithText("An unknown error occurred.").assertIsDisplayed()
    }
}
