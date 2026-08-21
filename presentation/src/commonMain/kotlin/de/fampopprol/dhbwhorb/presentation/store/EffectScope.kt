/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.store

/**
 * What an effect handler is allowed to do: produce a message for the reducer, or emit a one-shot
 * effect. Nothing else.
 *
 * In particular there is no way to read or write the state from here. A handler that could would
 * be able to make a decision on a state that has already moved on — which is the shape of every
 * race condition this architecture removes.
 */
interface EffectScope<in M : Any, in E : Any> {

    /** Feed a result back through the reducer. This is the only way the state changes. */
    fun emit(msg: M)

    /** Emit a one-shot event for the UI. */
    suspend fun send(effect: E)
}
