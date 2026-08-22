// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import Observation
import SwiftUI
import Shared

/// One store, observed for SwiftUI.
///
/// The whole Swift side of the app talks to Kotlin through this type and nothing else. Written
/// once, before the screens, for the same reason `Outcome`/`AppError` came before the repositories
/// in P3 and `Store`/`BaseStore` before the screens in P4: if each screen brings its own wiring,
/// the five of them drift.
///
/// Lifetime: the boxes live on the root view, not on the screens. The stores behind them are
/// application singletons — a box per screen appearance would still be cheap, but the effect
/// subscription would not be: it would restart on every tab switch and drop whatever a store
/// emitted while the tab was away.
@MainActor
@Observable
final class StoreBox<Bridge: StoreBridge> {

    /// The current state. Assigned only from the observation, which Kotlin runs on the main thread.
    private(set) var state: Bridge.State

    private let bridge: Bridge

    /// The live observations. They sit in a class of their own because `deinit` is not
    /// main-actor isolated and so cannot touch this object's isolated properties.
    private nonisolated let handles = Handles()

    private final class Handles {
        var state: ObservationHandle?
        var effect: ObservationHandle?
        func cancelAll() {
            state?.cancel()
            effect?.cancel()
        }
    }

    init(_ bridge: Bridge) {
        self.bridge = bridge
        self.state = bridge.state
        // `state` is a StateFlow: this delivers the current value immediately and then every change.
        handles.state = bridge.observeState { [weak self] newState in
            MainActor.assumeIsolated { self?.state = newState }
        }
    }

    func dispatch(_ intent: Bridge.Intent) {
        bridge.dispatch(intent: intent)
    }

    /// Start listening for one-shot effects. Calling it again replaces the previous handler.
    ///
    /// Not a SwiftUI modifier on purpose: effects must be consumed for as long as the store is
    /// alive, and a `.task` bound to a screen would miss the ones that arrive while another tab
    /// is showing.
    func onEffect(_ handler: @escaping (Bridge.Effect) -> Void) {
        handles.effect?.cancel()
        handles.effect = bridge.observeEffects { effect in
            MainActor.assumeIsolated { handler(effect) }
        }
    }

    deinit {
        handles.cancelAll()
    }
}
