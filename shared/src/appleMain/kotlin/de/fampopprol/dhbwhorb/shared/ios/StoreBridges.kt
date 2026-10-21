/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import de.fampopprol.dhbwhorb.presentation.app.AppEffect
import de.fampopprol.dhbwhorb.presentation.app.AppIntent
import de.fampopprol.dhbwhorb.presentation.app.AppState
import de.fampopprol.dhbwhorb.presentation.app.AppStore
import de.fampopprol.dhbwhorb.presentation.auth.AuthEffect
import de.fampopprol.dhbwhorb.presentation.auth.AuthIntent
import de.fampopprol.dhbwhorb.presentation.auth.AuthState
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsEffect
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsIntent
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsState
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsStore
import de.fampopprol.dhbwhorb.presentation.grades.GradesEffect
import de.fampopprol.dhbwhorb.presentation.grades.GradesIntent
import de.fampopprol.dhbwhorb.presentation.grades.GradesState
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.presentation.settings.SettingsEffect
import de.fampopprol.dhbwhorb.presentation.settings.SettingsIntent
import de.fampopprol.dhbwhorb.presentation.settings.SettingsState
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableEffect
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableIntent
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableState
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableStore

/*
 * One bridge per store, each with the same four members.
 *
 * They are written out rather than derived from a single generic class on purpose. Kotlin's
 * Objective-C export erases the type arguments of a generic supertype, so a generic
 * `StoreBridge<S, I, E>` would reach Swift as `dispatch(intent: Any)` and `observeState((Any) ->
 * Void)`, and every screen would have to cast. Spelled out, the compiler checks on both sides:
 * `AuthScreen` cannot dispatch a `GradesIntent`.
 *
 * All four members are deliberately the whole surface — the same one the `Store` interface gives
 * Compose. Nothing here decides anything; adding a rule to a bridge would put it on one platform
 * only, which is what P4 removed.
 */

class AppStoreBridge internal constructor(private val store: AppStore) {
    private val observer = FlowObserver()
    val state: AppState get() = store.state.value
    fun observeState(onEach: (AppState) -> Unit): ObservationHandle = observer.observe(store.state, onEach)
    fun observeEffects(onEach: (AppEffect) -> Unit): ObservationHandle = observer.observe(store.effects, onEach)
    fun dispatch(intent: AppIntent) = store.dispatch(intent)
}

class AuthStoreBridge internal constructor(private val store: AuthStore) {
    private val observer = FlowObserver()
    val state: AuthState get() = store.state.value
    fun observeState(onEach: (AuthState) -> Unit): ObservationHandle = observer.observe(store.state, onEach)
    fun observeEffects(onEach: (AuthEffect) -> Unit): ObservationHandle = observer.observe(store.effects, onEach)
    fun dispatch(intent: AuthIntent) = store.dispatch(intent)
}

class TimetableStoreBridge internal constructor(private val store: TimetableStore) {
    private val observer = FlowObserver()
    val state: TimetableState get() = store.state.value
    fun observeState(onEach: (TimetableState) -> Unit): ObservationHandle = observer.observe(store.state, onEach)
    fun observeEffects(onEach: (TimetableEffect) -> Unit): ObservationHandle = observer.observe(store.effects, onEach)
    fun dispatch(intent: TimetableIntent) = store.dispatch(intent)
}

class GradesStoreBridge internal constructor(private val store: GradesStore) {
    private val observer = FlowObserver()
    val state: GradesState get() = store.state.value
    fun observeState(onEach: (GradesState) -> Unit): ObservationHandle = observer.observe(store.state, onEach)
    fun observeEffects(onEach: (GradesEffect) -> Unit): ObservationHandle = observer.observe(store.effects, onEach)
    fun dispatch(intent: GradesIntent) = store.dispatch(intent)
}

class DocumentsStoreBridge internal constructor(private val store: DocumentsStore) {
    private val observer = FlowObserver()
    val state: DocumentsState get() = store.state.value
    fun observeState(onEach: (DocumentsState) -> Unit): ObservationHandle = observer.observe(store.state, onEach)
    fun observeEffects(onEach: (DocumentsEffect) -> Unit): ObservationHandle = observer.observe(store.effects, onEach)
    fun dispatch(intent: DocumentsIntent) = store.dispatch(intent)
}

class SettingsStoreBridge internal constructor(private val store: SettingsStore) {
    private val observer = FlowObserver()
    val state: SettingsState get() = store.state.value
    fun observeState(onEach: (SettingsState) -> Unit): ObservationHandle = observer.observe(store.state, onEach)
    fun observeEffects(onEach: (SettingsEffect) -> Unit): ObservationHandle = observer.observe(store.effects, onEach)
    fun dispatch(intent: SettingsIntent) = store.dispatch(intent)
}
