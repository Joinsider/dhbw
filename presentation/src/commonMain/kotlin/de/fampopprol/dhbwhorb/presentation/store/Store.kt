/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A feature's state holder.
 *
 * Everything the UI can do is [dispatch] an intent; everything it can see is [state] and
 * [effects]. There is no other entry point, which is what makes the two UIs — Compose and, from
 * P7, SwiftUI — able to share one implementation.
 *
 * @param S the state, an immutable data class
 * @param I what the user or the system asks for
 * @param E one-shot events: a snackbar, a navigation, a permission prompt
 */
interface Store<S : Any, I : Any, E : Any> {

    /** The current state. Always has a value; never fails. */
    val state: StateFlow<S>

    /**
     * One-shot events.
     *
     * Separate from [state] because they must not be replayed: a snackbar shown once should not
     * reappear when the screen is recomposed after a rotation.
     */
    val effects: Flow<E>

    /** Hand the store something to do. Never blocks. */
    fun dispatch(intent: I)

    /** Cancel any work still running. After this the store is unusable. */
    fun close()
}
