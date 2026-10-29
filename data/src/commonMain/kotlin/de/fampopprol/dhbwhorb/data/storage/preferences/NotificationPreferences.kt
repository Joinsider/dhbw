/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.preferences

import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
import de.fampopprol.dhbwhorb.domain.model.LectureCheck
import de.fampopprol.dhbwhorb.domain.model.LectureCheckOutcome

/**
 * Manages notification preferences. These are settings, not secrets — see [SettingsStorage].
 */
class NotificationPreferences(private val storage: SettingsStorage) {

    companion object {
        private const val NOTIFICATIONS_ENABLED_KEY = "notifications_enabled"
        private const val LECTURE_ALERTS_ENABLED_KEY = "lecture_alerts_enabled"
        private const val REMINDER_LEAD_MINUTES_KEY = "lecture_reminder_lead_minutes"
        private const val LAST_LECTURE_CHECK_KEY = "last_lecture_check"
        private const val UNSEEN_LECTURE_CHANGES_KEY = "unseen_lecture_changes"

        /** The offsets the settings screen offers. `0` means the reminder is off. */
        val REMINDER_LEAD_CHOICES = listOf(0, 15, 30, 60)
    }

    /**
     * Get the master notifications enabled preference
     * @return True if notifications are enabled, defaults to false
     */
    fun getNotificationsEnabled(): Boolean {
        val storedValue = storage.getString(NOTIFICATIONS_ENABLED_KEY, "false")
        return storedValue == "true"
    }

    /**
     * Set the master notifications enabled preference
     * @param enabled True to enable notifications, false to disable
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        storage.setString(NOTIFICATIONS_ENABLED_KEY, enabled.toString())
    }

    /**
     * Get the lecture alerts enabled preference
     * @return True if lecture alerts are enabled, defaults to false
     */
    fun getLectureAlertsEnabled(): Boolean {
        val storedValue = storage.getString(LECTURE_ALERTS_ENABLED_KEY, "false")
        return storedValue == "true"
    }

    /**
     * Set the lecture alerts enabled preference
     * @param enabled True to enable lecture alerts, false to disable
     */
    fun setLectureAlertsEnabled(enabled: Boolean) {
        storage.setString(LECTURE_ALERTS_ENABLED_KEY, enabled.toString())
    }

    /**
     * How many minutes before a lecture starts the reminder fires; `0` when it is off.
     *
     * A number rather than a flag plus a duration: "off" and "how long before" are the same choice
     * for the user — one picker, one stored value, no state where the two can disagree.
     */
    fun getReminderLeadMinutes(): Int =
        storage.getString(REMINDER_LEAD_MINUTES_KEY, "0").toIntOrNull()
            ?.takeIf { it in REMINDER_LEAD_CHOICES }
            ?: 0

    fun setReminderLeadMinutes(minutes: Int) {
        storage.setString(REMINDER_LEAD_MINUTES_KEY, minutes.toString())
    }

    /**
     * When the last background check finished and how it ended, stored as `"<epochMillis>;<OUTCOME>"`
     * so the time and the outcome are written in one step and cannot disagree. Null when no check
     * has run, or when the stored value is unreadable — the next run overwrites it either way.
     */
    fun getLastLectureCheck(): LectureCheck? {
        val stored = storage.getString(LAST_LECTURE_CHECK_KEY, "")
        val parts = stored.split(';')
        if (parts.size != 2) return null
        val epochMillis = parts[0].toLongOrNull() ?: return null
        val outcome = LectureCheckOutcome.entries.firstOrNull { it.name == parts[1] } ?: return null
        return LectureCheck(epochMillis, outcome)
    }

    fun setLastLectureCheck(check: LectureCheck) {
        storage.setString(LAST_LECTURE_CHECK_KEY, "${check.epochMillis};${check.outcome.name}")
    }

    /**
     * Lecture changes notified since the app was last opened — the number on the app icon. `0`
     * when nothing is stored or the stored value is unreadable.
     */
    fun getUnseenLectureChanges(): Int =
        storage.getString(UNSEEN_LECTURE_CHANGES_KEY, "0").toIntOrNull()?.takeIf { it >= 0 } ?: 0

    fun setUnseenLectureChanges(count: Int) {
        storage.setString(UNSEEN_LECTURE_CHANGES_KEY, count.toString())
    }
}
