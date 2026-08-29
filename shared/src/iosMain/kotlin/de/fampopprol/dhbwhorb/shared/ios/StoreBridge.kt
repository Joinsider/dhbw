/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * A running observation, handed back to Swift so it can stop collecting.
 *
 * Swift has no `Job`, and exposing one would drag the whole coroutines surface into the Swift
 * side. One method is all `StoreBox.deinit` needs.
 *
 * Not called `Cancellable`: SwiftUI re-exports Combine, whose protocol of that name would make
 * every mention of it ambiguous on the Swift side.
 */
class ObservationHandle internal constructor(private val job: Job) {
    fun cancel() {
        job.cancel()
    }
}

/**
 * The Kotlin half of the Swift bridge.
 *
 * Why the collection happens here and not in Swift: without SKIE (see [SharedApp] for why it is
 * not used) a `Flow` reaches Swift as a protocol whose only entry point is a suspending
 * `collect`, which imports as a completion handler and cannot be cancelled from Swift. Collecting
 * inside Kotlin and calling a Swift closure keeps that entirely on this side, and lets the
 * bridge guarantee the one thing SwiftUI requires: every callback arrives on the main thread.
 *
 * The scope is per bridge, not per app, so cancelling one screen's observation cannot touch
 * another's.
 */
internal class FlowObserver {

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main) + SupervisorJob()

    fun <T> observe(flow: Flow<T>, onEach: (T) -> Unit): ObservationHandle {
        val job = scope.launch {
            flow.collect { value -> onEach(value) }
        }
        return ObservationHandle(job)
    }
}
