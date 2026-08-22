/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.settings

import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode

/**
 * Theme and notification settings.
 *
 * The seed colour travels as an ARGB `Long`, not as a Compose `Color`: this state has to survive
 * the move into `Shared.framework`, where Compose does not exist.
 */
data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val materialYouEnabled: Boolean = true,
    val seedColor: Long = DEFAULT_SEED_COLOR,
    val notificationsEnabled: Boolean = false,
    val lectureAlertsEnabled: Boolean = false,
    /**
     * Minutes before a lecture that its reminder fires; `0` means off.
     *
     * One number instead of a switch plus a duration: for the user it is one choice, and two
     * fields could disagree about whether reminders are on.
     */
    val reminderLeadMinutes: Int = 0
) {
    companion object {
        /** Purple40, the value `ThemePreferences` falls back to. */
        const val DEFAULT_SEED_COLOR: Long = 4284932260
    }
}

sealed interface SettingsIntent {
    data object Load : SettingsIntent
    data class ThemeModeChanged(val mode: ThemeMode) : SettingsIntent
    data class MaterialYouChanged(val enabled: Boolean) : SettingsIntent
    data class SeedColorChanged(val argb: Long) : SettingsIntent
    data class NotificationsChanged(val enabled: Boolean) : SettingsIntent
    data class LectureAlertsChanged(val enabled: Boolean) : SettingsIntent
    data class ReminderLeadChanged(val minutes: Int) : SettingsIntent
}

sealed interface SettingsMsg {
    data class Loaded(val settings: SettingsState) : SettingsMsg
    data class ThemeModeChanged(val mode: ThemeMode) : SettingsMsg
    data class MaterialYouChanged(val enabled: Boolean) : SettingsMsg
    data class SeedColorChanged(val argb: Long) : SettingsMsg
    data class NotificationsChanged(val enabled: Boolean) : SettingsMsg
    data class LectureAlertsChanged(val enabled: Boolean) : SettingsMsg
    data class ReminderLeadChanged(val minutes: Int) : SettingsMsg
}

/** Nothing here is one-shot: every setting is state the whole app reads. */
sealed interface SettingsEffect
