/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.repository

import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.domain.model.LectureCheck
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's settings.
 *
 * Local reads and writes with no failure mode worth reporting, so no [de.fampopprol.dhbwhorb.core.error.Outcome]
 * here — a preference that cannot be stored falls back to its default.
 */
interface PreferencesRepository {

    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)

    fun isMaterialYouEnabled(): Boolean
    fun setMaterialYouEnabled(enabled: Boolean)

    fun getCustomColor(): Long
    fun setCustomColor(color: Long)

    fun areNotificationsEnabled(): Boolean
    fun setNotificationsEnabled(enabled: Boolean)

    fun areLectureAlertsEnabled(): Boolean
    fun setLectureAlertsEnabled(enabled: Boolean)

    /** Minutes before a lecture that its reminder fires. `0` means no reminder. */
    fun getReminderLeadMinutes(): Int
    fun setReminderLeadMinutes(minutes: Int)

    /** The last completed background lecture check, or `null` when none has run yet. */
    fun getLastLectureCheck(): LectureCheck?

    /**
     * The last completed background lecture check, live: it changes when a background run finishes,
     * so a screen that shows it does not need to be reopened to see the new time.
     */
    fun observeLastLectureCheck(): StateFlow<LectureCheck?>
}
