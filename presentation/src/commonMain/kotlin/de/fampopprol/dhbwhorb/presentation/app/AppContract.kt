/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.app

/**
 * The screens the root can show.
 *
 * Still an enum: P5 replaces it with typed routes once a navigation library is in. It lives in
 * `:presentation` rather than in the Compose UI so the store can own the routing decision.
 */
enum class AppScreen {
    LOGIN,
    TIMETABLE,
    GRADES,
    DOCUMENTS,
    SETTINGS
}

/**
 * Session status and root routing.
 *
 * [isRestoring] is what the root shows before the stored session has been checked. Without it the
 * first frame guesses — and guessed wrong for anyone whose session had expired.
 */
data class AppState(
    val screen: AppScreen = AppScreen.LOGIN,
    val isLoggedIn: Boolean = false,
    val isRestoring: Boolean = true,
    val userFullName: String? = null,
    val isDemo: Boolean = false
)

sealed interface AppIntent {
    /** Check for a usable session. Dispatched once at start. */
    data object Started : AppIntent
    data class Navigated(val screen: AppScreen) : AppIntent
    data object LoggedIn : AppIntent
    data object LogoutRequested : AppIntent
}

sealed interface AppMsg {
    data class SessionRestored(val userFullName: String?, val isDemo: Boolean) : AppMsg
    data object NoSession : AppMsg
    data class Navigated(val screen: AppScreen) : AppMsg
    data object LoggedOut : AppMsg
}

sealed interface AppEffect {
    /** Logout finished but the cached data could not be cleared. */
    data object CacheNotCleared : AppEffect
}
