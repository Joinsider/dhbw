/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.store

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * The half of a store that is the same for every feature.
 *
 * The split that matters:
 *
 * * [reduce] is pure and synchronous. Same state, same message, same result — no coroutines, no
 *   I/O, no clock, no randomness. Its tests need no `runTest` and no dispatcher.
 * * [handle] is where everything uncertain lives. It cannot touch the state; it can only [emit]
 *   a message and let the reducer decide what that means.
 *
 * That is what makes the current race conditions unrepresentable: there is no window between
 * reading the state and writing it, because nothing outside the reducer writes it.
 *
 * @param scope the store's lifetime. Every effect runs in a child of it, so [close] stops them all.
 */
abstract class BaseStore<S : Any, I : Any, M : Any, E : Any>(
    initialState: S,
    scope: CoroutineScope
) : Store<S, I, E> {

    private companion object {
        const val TAG = "Store"

        /**
         * Effects are one-shot and must not be dropped while the screen is briefly not collecting
         * — during a configuration change, for instance. A small buffer covers that; going over it
         * means something is producing effects nobody consumes, which the oldest-dropping policy
         * makes visible rather than deadlocking on.
         */
        const val EFFECT_BUFFER = 16
    }

    // A SupervisorJob of its own, so one failing effect does not take down the store, and so
    // close() cancels this store's work without touching the scope it was given.
    private val storeScope = scope + SupervisorJob(scope.coroutineContext[Job])

    private val initial = initialState

    private val _state = MutableStateFlow(initialState)
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<E>(
        extraBufferCapacity = EFFECT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val effects: Flow<E> = _effects.asSharedFlow()

    private val effectScope = object : EffectScope<M, E> {
        override fun emit(msg: M) {
            // Synchronous, so a handler that emits twice cannot have the two arrive out of order.
            _state.update { current -> reduce(current, msg) }
        }

        override suspend fun send(effect: E) {
            _effects.emit(effect)
        }
    }

    /**
     * The state after [msg] is applied. Must be pure.
     */
    protected abstract fun reduce(state: S, msg: M): S

    /**
     * React to [intent]. Runs on the store's scope, one coroutine per dispatch.
     *
     * @param state the state at the moment of dispatch — a snapshot for deciding *what* to do,
     *   never something to write back.
     */
    protected abstract suspend fun EffectScope<M, E>.handle(intent: I, state: S)

    /**
     * Intents that must not run twice concurrently return a key here; a second dispatch with the
     * same key while the first is still running is dropped.
     *
     * This is where "refresh while a refresh is running" is decided, rather than with a boolean
     * in the state that two coroutines can both read as false.
     *
     * @return null to allow unlimited concurrency for this intent
     */
    protected open fun dedupeKey(intent: I): Any? = null

    /**
     * The in-flight jobs by dedupe key.
     *
     * An immutable map behind [MutableStateFlow.update] rather than a `mutableMapOf`, because
     * `dispatch` is called from the UI thread while `invokeOnCompletion` fires on whichever thread
     * the effect finished on — and on Kotlin/Native that combination is a real data race.
     */
    private val running = MutableStateFlow<Map<Any, Job>>(emptyMap())

    override fun dispatch(intent: I) {
        val key = dedupeKey(intent)
        if (key != null && running.value[key]?.isActive == true) {
            Napier.d("Dropping $intent: one is already in flight", tag = TAG)
            return
        }

        // LAZY so the job is registered before it can run — otherwise a handler that finishes
        // immediately could complete before its entry exists and leave the key behind forever.
        val job = storeScope.launch(start = CoroutineStart.LAZY) {
            with(effectScope) { handle(intent, _state.value) }
        }

        if (key != null) {
            running.update { it + (key to job) }
            job.invokeOnCompletion {
                running.update { current -> if (current[key] === job) current - key else current }
            }
        }
        job.start()
    }

    /**
     * Drop the state and everything still running, and start over.
     *
     * For logout: the store outlives the session, so what it holds has to be taken from it. The
     * in-flight effects go first — a load that started before the logout would otherwise emit the
     * previous user's data into the freshly emptied state.
     */
    open fun reset() {
        val inFlight = running.value.values
        running.value = emptyMap()
        inFlight.forEach { it.cancel() }
        _state.value = initial
    }

    override fun close() {
        storeScope.cancel()
        running.value = emptyMap()
    }
}
