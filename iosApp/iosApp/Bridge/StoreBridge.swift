// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import Shared

/// What every store looks like from Swift.
///
/// The six Kotlin bridges already have these four members with these exact names, so each
/// conformance below is empty — the protocol only gives `StoreBox` something to be generic over.
/// The associated types are spelled out rather than inferred: an inferred `Effect` picks up the
/// existential from `observeEffects`, which compiles but produces error messages nobody can read
/// once a screen gets an intent wrong.
protocol StoreBridge: AnyObject {
    associatedtype State: AnyObject
    associatedtype Intent
    associatedtype Effect

    var state: State { get }
    func observeState(onEach: @escaping (State) -> Void) -> ObservationHandle
    func observeEffects(onEach: @escaping (Effect) -> Void) -> ObservationHandle
    func dispatch(intent: Intent)
}

extension AppStoreBridge: StoreBridge {
    typealias State = AppState
    typealias Intent = any AppIntent
    typealias Effect = any AppEffect
}

extension AuthStoreBridge: StoreBridge {
    typealias State = AuthState
    typealias Intent = any AuthIntent
    typealias Effect = any AuthEffect
}

extension TimetableStoreBridge: StoreBridge {
    typealias State = TimetableState
    typealias Intent = any TimetableIntent
    typealias Effect = any TimetableEffect
}

extension GradesStoreBridge: StoreBridge {
    typealias State = GradesState
    typealias Intent = any GradesIntent
    typealias Effect = any GradesEffect
}

extension DocumentsStoreBridge: StoreBridge {
    typealias State = DocumentsState
    typealias Intent = any DocumentsIntent
    typealias Effect = any DocumentsEffect
}

extension SettingsStoreBridge: StoreBridge {
    typealias State = SettingsState
    typealias Intent = any SettingsIntent
    typealias Effect = any SettingsEffect
}
