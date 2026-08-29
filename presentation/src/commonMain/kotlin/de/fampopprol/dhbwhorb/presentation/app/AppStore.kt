/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.app

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.usecase.Logout
import de.fampopprol.dhbwhorb.domain.usecase.PurgeExpiredDocuments
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import de.fampopprol.dhbwhorb.presentation.store.SessionScopedStore
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
    private val purgeExpiredDocuments: PurgeExpiredDocuments,
    scope: CoroutineScope,
    /**
     * The stores holding account data, resolved when a logout happens.
     *
     * A lambda rather than the list itself: these stores and this one are all singletons in the
     * same graph, and asking for them while this one is being built is a cycle.
     */
    private val sessionScopedStores: () -> List<SessionScopedStore> = { emptyList() }
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
                // Housekeeping, not part of restoring the session: cached documents have a
                // deletion deadline, and app start is the one moment every user reaches.
                purgeExpiredDocuments()

                val session = sessionRepository.currentSession()
                if (session == null) {
                    emit(AppMsg.NoSession)
                } else {
                    emit(AppMsg.SessionRestored(session.userFullName, session.isDemo))
                }
            }

            AppIntent.LogoutRequested -> {
                val result = logout()

                // The screens are singletons and would otherwise still be holding the previous
                // user's grades and timetable when the next one logs in — visible until the first
                // fetch replaces them.
                sessionScopedStores().forEach { it.reset() }

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
