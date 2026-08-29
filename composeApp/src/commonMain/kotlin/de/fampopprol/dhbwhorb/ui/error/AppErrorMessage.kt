/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.error

import androidx.compose.runtime.Composable
import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.error_login_required
import de.fampopprol.dhbwhorb.resources.error_server
import de.fampopprol.dhbwhorb.resources.error_session_expired
import de.fampopprol.dhbwhorb.resources.error_storage
import de.fampopprol.dhbwhorb.resources.error_unreadable_response
import de.fampopprol.dhbwhorb.resources.error_unsupported_in_demo
import de.fampopprol.dhbwhorb.resources.invalid_credentials
import de.fampopprol.dhbwhorb.resources.network_error
import de.fampopprol.dhbwhorb.resources.unknown_error
import org.jetbrains.compose.resources.stringResource

/**
 * The sentence to show the user for an [AppError].
 *
 * This is the only place the classification turns into words. Before it, every layer built its own
 * `"Failed to load: ${e.message}"` out of an exception, so the user read an English stack-trace
 * fragment no matter which language the app ran in — and could not tell a lost connection from a
 * changed Dualis page.
 */
@Composable
fun AppError.toUserMessage(): String = when (this) {
    AppError.Offline -> stringResource(Res.string.network_error)
    AppError.SessionExpired -> stringResource(Res.string.error_session_expired)
    AppError.InvalidCredentials -> stringResource(Res.string.invalid_credentials)
    AppError.NoCredentials -> stringResource(Res.string.error_login_required)
    is AppError.Http -> stringResource(Res.string.error_server, code)
    is AppError.Parse -> stringResource(Res.string.error_unreadable_response)
    is AppError.Storage -> stringResource(Res.string.error_storage)
    is AppError.Unsupported -> stringResource(Res.string.error_unsupported_in_demo)
    is AppError.Unexpected -> stringResource(Res.string.unknown_error)
}
