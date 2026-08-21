/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.settings

import de.fampopprol.dhbwhorb.domain.repository.PreferencesRepository
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import kotlinx.coroutines.CoroutineScope

/**
 * Settings, read once and written through.
 *
 * Replaces the preference callbacks that used to live in the root composable, where every screen
 * that wanted a setting had to have it threaded down as a parameter pair.
 */
class SettingsStore(
    private val preferences: PreferencesRepository,
    scope: CoroutineScope
) : BaseStore<SettingsState, SettingsIntent, SettingsMsg, SettingsEffect>(
    initialState = SettingsState(),
    scope = scope
) {

    override fun reduce(state: SettingsState, msg: SettingsMsg): SettingsState = reduceSettings(state, msg)

    override suspend fun EffectScope<SettingsMsg, SettingsEffect>.handle(
        intent: SettingsIntent,
        state: SettingsState
    ) {
        when (intent) {
            SettingsIntent.Load -> emit(
                SettingsMsg.Loaded(
                    SettingsState(
                        themeMode = preferences.getThemeMode(),
                        materialYouEnabled = preferences.isMaterialYouEnabled(),
                        seedColor = preferences.getCustomColor(),
                        notificationsEnabled = preferences.areNotificationsEnabled(),
                        lectureAlertsEnabled = preferences.areLectureAlertsEnabled()
                    )
                )
            )

            // Persist first, then reduce: if the write fails the state must not claim otherwise.
            is SettingsIntent.ThemeModeChanged -> {
                preferences.setThemeMode(intent.mode)
                emit(SettingsMsg.ThemeModeChanged(intent.mode))
            }

            is SettingsIntent.MaterialYouChanged -> {
                preferences.setMaterialYouEnabled(intent.enabled)
                emit(SettingsMsg.MaterialYouChanged(intent.enabled))
            }

            is SettingsIntent.SeedColorChanged -> {
                preferences.setCustomColor(intent.argb)
                emit(SettingsMsg.SeedColorChanged(intent.argb))
            }

            is SettingsIntent.NotificationsChanged -> {
                preferences.setNotificationsEnabled(intent.enabled)
                emit(SettingsMsg.NotificationsChanged(intent.enabled))
            }

            is SettingsIntent.LectureAlertsChanged -> {
                preferences.setLectureAlertsEnabled(intent.enabled)
                emit(SettingsMsg.LectureAlertsChanged(intent.enabled))
            }
        }
    }
}

/**
 * The settings state after [msg].
 *
 * Top-level and therefore unable to reach a store, a repository or a scope: the reducer's purity
 * is structural rather than a promise. Its tests call it directly, with no coroutines involved.
 */
fun reduceSettings(state: SettingsState, msg: SettingsMsg): SettingsState = when (msg) {
    is SettingsMsg.Loaded -> msg.settings
    is SettingsMsg.ThemeModeChanged -> state.copy(themeMode = msg.mode)
    is SettingsMsg.MaterialYouChanged -> state.copy(materialYouEnabled = msg.enabled)
    is SettingsMsg.SeedColorChanged -> state.copy(seedColor = msg.argb)
    is SettingsMsg.NotificationsChanged -> state.copy(notificationsEnabled = msg.enabled)
    is SettingsMsg.LectureAlertsChanged -> state.copy(lectureAlertsEnabled = msg.enabled)
}
