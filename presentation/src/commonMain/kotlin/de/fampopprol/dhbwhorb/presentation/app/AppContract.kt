/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.app

/**
 * Who is logged in.
 *
 * No longer holds a screen: since P5 the navigation graph's back stack is the only answer to
 * "where am I", and having a second one here meant the two could disagree. What is left is the
 * one routing decision that is not navigation — login screen or app.
 *
 * [isRestoring] is what the root shows before the stored session has been checked. Without it the
 * first frame guesses, and it guessed wrong for anyone whose session had expired.
 *
 * [reLoginRequired] is why the login screen is showing when that is not obvious: Dualis rejected
 * the stored password often enough that the app stopped logging in by itself, and waits for the
 * user to type it again.
 */
data class AppState(
    val isLoggedIn: Boolean = false,
    val isRestoring: Boolean = true,
    val userFullName: String? = null,
    val isDemo: Boolean = false,
    val reLoginRequired: Boolean = false
)

sealed interface AppIntent {
    /** Check for a usable session. Dispatched once at start. */
    data object Started : AppIntent
    data object LoggedIn : AppIntent
    data object LogoutRequested : AppIntent

    /**
     * Follow the automatic-login guard for as long as the store lives. Dispatched by the store
     * itself on creation; a background check can close the guard while the app is open.
     */
    data object WatchAutoLogin : AppIntent
}

sealed interface AppMsg {
    data class SessionRestored(val userFullName: String?, val isDemo: Boolean) : AppMsg
    data object NoSession : AppMsg
    data object LoggedOut : AppMsg

    /** The stored credentials were rejected too often; the user has to log in by hand. */
    data object AutoLoginBlocked : AppMsg
}

sealed interface AppEffect {
    /** Logout finished but the cached data could not be cleared. */
    data object CacheNotCleared : AppEffect
}
