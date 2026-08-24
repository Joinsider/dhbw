/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.store

import de.fampopprol.dhbwhorb.presentation.TestScopes
import de.fampopprol.dhbwhorb.presentation.collectEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The store contract itself, on a toy feature.
 *
 * These are the guarantees every feature store inherits, so they are checked once here rather than
 * six times over.
 */
class BaseStoreTest {

    private data class CounterState(val value: Int = 0, val busy: Boolean = false)

    private sealed interface CounterIntent {
        data object Increment : CounterIntent
        data object SlowIncrement : CounterIntent
        data object Announce : CounterIntent
    }

    private sealed interface CounterMsg {
        data object Incremented : CounterMsg
        data class Busy(val busy: Boolean) : CounterMsg
    }

    private data class Announcement(val value: Int)

    private class CounterStore(
        private val gate: CompletableDeferred<Unit>? = null
    ) : BaseStore<CounterState, CounterIntent, CounterMsg, Announcement>(
        initialState = CounterState(),
        scope = TestScopes.immediate()
    ) {
        var handled = 0
            private set

        override fun dedupeKey(intent: CounterIntent): Any? =
            if (intent is CounterIntent.SlowIncrement) "slow" else null

        override fun reduce(state: CounterState, msg: CounterMsg): CounterState = when (msg) {
            CounterMsg.Incremented -> state.copy(value = state.value + 1)
            is CounterMsg.Busy -> state.copy(busy = msg.busy)
        }

        override suspend fun EffectScope<CounterMsg, Announcement>.handle(
            intent: CounterIntent,
            state: CounterState
        ) {
            handled++
            when (intent) {
                CounterIntent.Increment -> emit(CounterMsg.Incremented)

                CounterIntent.SlowIncrement -> {
                    emit(CounterMsg.Busy(true))
                    gate?.await()
                    emit(CounterMsg.Incremented)
                    emit(CounterMsg.Busy(false))
                }

                CounterIntent.Announce -> send(Announcement(state.value))
            }
        }
    }

    @Test
    fun dispatch_runsTheHandlerAndTheStateFollows() = runTest {
        val store = CounterStore()

        store.dispatch(CounterIntent.Increment)
        store.dispatch(CounterIntent.Increment)

        assertEquals(2, store.state.value.value)
        store.close()
    }

    @Test
    fun aHandlerSeesTheStateAsItWasWhenDispatched() = runTest {
        val store = CounterStore()

        store.dispatch(CounterIntent.Increment)
        val effects = mutableListOf<Announcement>()
        val collector = collectEffects(store) { effects += it }
        store.dispatch(CounterIntent.Announce)

        assertEquals(listOf(Announcement(1)), effects)
        collector.cancel()
        store.close()
    }

    @Test
    fun anIntentWithADedupeKey_doesNotRunTwiceAtOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = CounterStore(gate)

        store.dispatch(CounterIntent.SlowIncrement)
        store.dispatch(CounterIntent.SlowIncrement)

        assertEquals(1, store.handled, "The second dispatch has to be dropped, not queued")
        assertTrue(store.state.value.busy)

        gate.complete(Unit)
        assertEquals(1, store.state.value.value)
        store.close()
    }

    @Test
    fun afterItFinishes_theSameKeyCanRunAgain() = runTest {
        val store = CounterStore()

        store.dispatch(CounterIntent.SlowIncrement)
        store.dispatch(CounterIntent.SlowIncrement)

        // Nothing gated them, so the first completed before the second was dispatched.
        assertEquals(2, store.handled)
        assertEquals(2, store.state.value.value)
        store.close()
    }

    @Test
    fun intentsWithoutAKey_areNotDeduped() = runTest {
        val store = CounterStore()

        repeat(5) { store.dispatch(CounterIntent.Increment) }

        assertEquals(5, store.handled)
        store.close()
    }

    @Test
    fun close_stopsInFlightWorkFromWritingToTheState() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = CounterStore(gate)

        store.dispatch(CounterIntent.SlowIncrement)
        assertTrue(store.state.value.busy, "The handler is waiting at the gate")

        store.close()
        gate.complete(Unit)

        // Navigating away must not let a half-finished operation land afterwards.
        assertEquals(0, store.state.value.value)
        store.close()
    }

    @Test
    fun close_makesFurtherDispatchesInert() = runTest {
        val store = CounterStore()

        store.close()
        store.dispatch(CounterIntent.Increment)

        assertEquals(0, store.handled)
        assertEquals(CounterState(), store.state.value)
    }

    @Test
    fun reset_dropsTheStateAndKeepsTheStoreUsable() = runTest {
        val store = CounterStore()

        store.dispatch(CounterIntent.Increment)
        assertEquals(1, store.state.value.value)

        store.reset()
        assertEquals(CounterState(), store.state.value, "logout leaves nothing behind")

        // Unlike close(), the store lives on — the next login uses the same instance.
        store.dispatch(CounterIntent.Increment)
        assertEquals(1, store.state.value.value)
        store.close()
    }

    @Test
    fun reset_cancelsWorkThatWouldLandAfterwards() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = CounterStore(gate)

        store.dispatch(CounterIntent.SlowIncrement)
        store.reset()
        gate.complete(Unit)

        // A load started before the logout must not write the previous user's data into the
        // emptied state.
        assertEquals(CounterState(), store.state.value)
        store.close()
    }

    @Test
    fun effectsAreNotReplayed() = runTest {
        val store = CounterStore()

        store.dispatch(CounterIntent.Announce)

        // A collector arriving afterwards must not be handed the effect it missed: a snackbar
        // shown once should not reappear on the next recomposition.
        val late = mutableListOf<Announcement>()
        val collector = collectEffects(store) { late += it }
        assertTrue(late.isEmpty())

        collector.cancel()
        store.close()
    }
}
