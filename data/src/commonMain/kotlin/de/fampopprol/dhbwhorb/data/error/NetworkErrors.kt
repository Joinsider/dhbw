/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.error

import de.fampopprol.dhbwhorb.core.error.AppError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.io.IOException

/**
 * Turns the exceptions Ktor throws into the cases the UI can act on.
 *
 * This is the one place where an exception becomes an [AppError]. Everything downstream works
 * with the classified value, which is what makes "you are offline" distinguishable from "Dualis
 * answered with something we could not read".
 *
 * @param source what was being fetched, for [AppError.Unexpected]'s message
 */
fun Throwable.toAppError(source: String): AppError = when (this) {
    // Every timeout and name-resolution failure means the same thing to the user: the request did
    // not reach Dualis, and trying again later may work.
    is UnresolvedAddressException,
    is ConnectTimeoutException,
    is SocketTimeoutException,
    is HttpRequestTimeoutException,
    is IOException -> AppError.Offline

    else -> AppError.Unexpected("$source: ${this::class.simpleName}: $message")
}

/**
 * Maps an HTTP status to an error.
 *
 * Dualis answers an expired session with 200 and the login page rather than with 401, so the
 * status alone rarely reveals that; [AppError.SessionExpired] is decided from the page content
 * instead. 401 and 403 are still honoured for the endpoints that do use them, such as the file
 * download.
 */
fun httpStatusToAppError(code: Int): AppError = when (code) {
    401, 403 -> AppError.SessionExpired
    else -> AppError.Http(code)
}
