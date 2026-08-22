/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation

import de.fampopprol.dhbwhorb.presentation.store.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Scopes and helpers for store tests.
 *
 * Effect handlers are the only part of a store that needs coroutines, and they only ever await a
 * fake. On an unconfined dispatcher a `dispatch` therefore runs to completion before it returns,
 * so a test can dispatch and then read `state.value` without scheduling gymnastics.
 */
object TestScopes {

    /** A scope whose coroutines run eagerly, inline, on the calling thread. */
    fun immediate(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
}

/**
 * Record the store's one-shot effects.
 *
 * @return the collecting job; cancel it when the test is done.
 */
fun <S : Any, I : Any, E : Any> collectEffects(
    store: Store<S, I, E>,
    scope: CoroutineScope = TestScopes.immediate(),
    onEffect: (E) -> Unit
): Job = scope.launch { store.effects.collect { onEffect(it) } }
