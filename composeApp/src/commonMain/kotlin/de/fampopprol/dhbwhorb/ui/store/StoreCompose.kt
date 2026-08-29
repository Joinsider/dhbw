/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import de.fampopprol.dhbwhorb.presentation.store.Store
import kotlinx.coroutines.flow.collectLatest

/**
 * The bridge between a [Store] and Compose.
 *
 * Two functions, deliberately small: everything else about a store — what it holds, what it does —
 * lives in `:presentation`, where SwiftUI can reach it too.
 */

/** The store's state, as Compose state. */
@Composable
fun <S : Any, I : Any, E : Any> Store<S, I, E>.collectState(): State<S> = state.collectAsState()

/**
 * Run [onEffect] for each one-shot effect while this composable is in the tree.
 *
 * Keyed on the store, so it survives recomposition but restarts if the store is replaced. Effects
 * emitted while nothing is collecting are buffered by the store rather than lost, which is what
 * keeps a snackbar from disappearing across a configuration change.
 */
@Composable
fun <S : Any, I : Any, E : Any> Store<S, I, E>.HandleEffects(onEffect: suspend (E) -> Unit) {
    LaunchedEffect(this) {
        effects.collectLatest { onEffect(it) }
    }
}
