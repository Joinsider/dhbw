/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.settings

import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemePreferences
import de.fampopprol.dhbwhorb.domain.model.LectureCheck

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
    /**
     * The Apple accent: the identifier of one entry in the curated palette in `Theme.swift`
     * (`"brand"`, `"pink"`, …). Only iOS and macOS read it; Android and Desktop use [seedColor].
     *
     * It is an identifier rather than a colour on purpose. A seed colour yields a whole Material
     * scheme, while an Apple accent is one tint plus a lecture fill that somebody checked for
     * contrast by hand. A name also survives a later retuning of the values, which a stored ARGB
     * would not. Ids outside [KNOWN_ACCENT_CHOICES] are replaced by [DEFAULT_ACCENT_CHOICE].
     */
    val accentChoice: String = DEFAULT_ACCENT_CHOICE,
    val notificationsEnabled: Boolean = false,
    val lectureAlertsEnabled: Boolean = false,
    /**
     * Minutes before a lecture that its reminder fires; `0` means off.
     *
     * One number instead of a switch plus a duration: for the user it is one choice, and two
     * fields could disagree about whether reminders are on.
     */
    val reminderLeadMinutes: Int = 0,
    /**
     * When the background lecture check last finished, and how. Null until one has run.
     *
     * Read-only here: nothing in the settings writes it, the background run does. It is kept live by
     * [SettingsIntent.WatchLastLectureCheck].
     */
    val lastLectureCheck: LectureCheck? = null,
) {
    companion object {
        /** Purple40, the value `ThemePreferences` falls back to. */
        const val DEFAULT_SEED_COLOR: Long = 4284932260

        /** DHBW red, the Apple default. */
        const val DEFAULT_ACCENT_CHOICE: String = ThemePreferences.DEFAULT_ACCENT_CHOICE

        /**
         * The accent identifiers Apple can show, in palette order. The Swift `AccentChoice` enum
         * has to carry the same ids; adding one means adding it in both places.
         */
        val KNOWN_ACCENT_CHOICES: List<String> = listOf(
            "brand", "pink", "raspberry", "purple", "violet", "indigo", "blue", "lightBlue", "cyan",
            "teal", "green", "lightGreen", "lime", "yellow", "amber", "orange", "deepOrange", "blueGrey",
        )

        /** [id] if it is a known accent, otherwise the default: a downgrade or a removed colour must not break the screen. */
        fun accentChoiceOrDefault(id: String): String =
            if (id in KNOWN_ACCENT_CHOICES) id else DEFAULT_ACCENT_CHOICE
    }
}

sealed interface SettingsIntent {
    data object Load : SettingsIntent
    data class ThemeModeChanged(val mode: ThemeMode) : SettingsIntent
    data class MaterialYouChanged(val enabled: Boolean) : SettingsIntent
    data class SeedColorChanged(val argb: Long) : SettingsIntent

    /** Apple only. An unknown [id] is stored as [SettingsState.DEFAULT_ACCENT_CHOICE]. */
    data class AccentChoiceChanged(val id: String) : SettingsIntent
    data class NotificationsChanged(val enabled: Boolean) : SettingsIntent
    data class LectureAlertsChanged(val enabled: Boolean) : SettingsIntent
    data class ReminderLeadChanged(val minutes: Int) : SettingsIntent

    /** Keeps [SettingsState.lastLectureCheck] up to date for as long as the store lives. */
    data object WatchLastLectureCheck : SettingsIntent
}

sealed interface SettingsMsg {
    data class Loaded(val settings: SettingsState) : SettingsMsg
    data class ThemeModeChanged(val mode: ThemeMode) : SettingsMsg
    data class MaterialYouChanged(val enabled: Boolean) : SettingsMsg
    data class SeedColorChanged(val argb: Long) : SettingsMsg
    data class AccentChoiceChanged(val id: String) : SettingsMsg
    data class NotificationsChanged(val enabled: Boolean) : SettingsMsg
    data class LectureAlertsChanged(val enabled: Boolean) : SettingsMsg
    data class ReminderLeadChanged(val minutes: Int) : SettingsMsg
    data class LastLectureCheckChanged(val check: LectureCheck?) : SettingsMsg
}

/** Nothing here is one-shot: every setting is state the whole app reads. */
sealed interface SettingsEffect
