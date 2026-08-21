/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.core.error

/**
 * Every way a request through this app can fail, as data rather than as an exception.
 *
 * The point is that the UI can tell these apart. Before this existed, 69 `catch (e: Exception)`
 * blocks degraded to `null` or `emptyList()`, so "you are offline", "your session expired" and
 * "Dualis changed its HTML" all reached the screen as an empty list.
 *
 * A sealed hierarchy rather than `kotlin.Result`: SKIE turns this into a real Swift enum in P7,
 * so `switch error { case .offline: … }` works without casting.
 */
sealed interface AppError {

    /** No usable network: DNS failure, connection refused, timeout. Retrying later can work. */
    data object Offline : AppError

    /** Dualis no longer accepts the session and re-authentication did not recover it. */
    data object SessionExpired : AppError

    /** Credentials were rejected. Distinct from [SessionExpired]: retrying will not help. */
    data object InvalidCredentials : AppError

    /** Nothing is stored to log in with — the user has to enter credentials. */
    data object NoCredentials : AppError

    /** The server answered, but not with success. */
    data class Http(val code: Int) : AppError

    /**
     * The response arrived but did not look like what [source] promised.
     *
     * Dualis is scraped, so this is the expected failure when the portal is redesigned.
     */
    data class Parse(val source: String, val hint: String) : AppError

    /** The local database or secure storage refused a read or write. */
    data class Storage(val hint: String) : AppError

    /** The operation is not available in the current mode, e.g. downloads in demo mode. */
    data class Unsupported(val hint: String) : AppError

    /** Nothing above fits. Every occurrence is a candidate for a more precise case. */
    data class Unexpected(val hint: String) : AppError
}
