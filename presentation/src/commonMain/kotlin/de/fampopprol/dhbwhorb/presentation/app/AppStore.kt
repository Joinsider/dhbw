/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.app

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.usecase.Logout
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import kotlinx.coroutines.CoroutineScope

/**
 * Who is logged in.
 *
 * Replaces the half a dozen `remember { mutableStateOf(...) }` in the root composable, where the
 * session was read once during composition and again in a `LaunchedEffect` that could disagree
 * with it. Navigation moved to the graph's back stack in P5.
 */
class AppStore(
    private val sessionRepository: SessionRepository,
    private val logout: Logout,
    scope: CoroutineScope
) : BaseStore<AppState, AppIntent, AppMsg, AppEffect>(
    initialState = AppState(),
    scope = scope
) {

    override fun dedupeKey(intent: AppIntent): Any = when (intent) {
        AppIntent.LogoutRequested -> "logout"
        AppIntent.Started, AppIntent.LoggedIn -> "session"
    }

    override fun reduce(state: AppState, msg: AppMsg): AppState = reduceApp(state, msg)

    override suspend fun EffectScope<AppMsg, AppEffect>.handle(intent: AppIntent, state: AppState) {
        when (intent) {
            AppIntent.Started, AppIntent.LoggedIn -> {
                val session = sessionRepository.currentSession()
                if (session == null) {
                    emit(AppMsg.NoSession)
                } else {
                    emit(AppMsg.SessionRestored(session.userFullName, session.isDemo))
                }
            }

            AppIntent.LogoutRequested -> {
                val result = logout()
                // The session is gone either way, so the UI leaves regardless; a cache that could
                // not be wiped is worth telling the user about, not worth blocking on.
                emit(AppMsg.LoggedOut)
                if (result is Outcome.Err) send(AppEffect.CacheNotCleared)
            }
        }
    }
}

/**
 * The root state after [msg].
 *
 * Top-level and therefore unable to reach a store, a repository or a scope: the reducer's purity
 * is structural rather than a promise. Its tests call it directly, with no coroutines involved.
 */
fun reduceApp(state: AppState, msg: AppMsg): AppState = when (msg) {
    is AppMsg.SessionRestored -> state.copy(
        isLoggedIn = true,
        isRestoring = false,
        userFullName = msg.userFullName,
        isDemo = msg.isDemo
    )

    AppMsg.NoSession -> state.copy(
        isLoggedIn = false,
        isRestoring = false,
        userFullName = null,
        isDemo = false
    )

    AppMsg.LoggedOut -> AppState(isRestoring = false)
}
