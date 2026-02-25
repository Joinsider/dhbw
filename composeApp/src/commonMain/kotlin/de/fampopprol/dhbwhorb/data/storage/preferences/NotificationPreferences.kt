/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.preferences

import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface

/**
 * Manages notification preferences using SecureStorage
 */
class NotificationPreferences(private val storage: SecureStorageInterface) {

    companion object {
        private const val NOTIFICATIONS_ENABLED_KEY = "notifications_enabled"
        private const val LECTURE_ALERTS_ENABLED_KEY = "lecture_alerts_enabled"
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
}

