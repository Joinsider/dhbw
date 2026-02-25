/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package de.fampopprol.dhbwhorb.services.notifications

/**
 * Platform-specific notification dispatcher for showing lecture change notifications.
 * Implementations handle permission requests and notification display per platform.
 */
expect class NotificationDispatcher() {
    /**
     * Request notification permission from the user.
     * @return true if permission is granted, false otherwise
     */
    suspend fun requestPermission(): Boolean

    /**
     * Check if notification permission is currently granted.
     * @return true if permission is granted, false otherwise
     */
    suspend fun hasPermission(): Boolean

    /**
     * Show a notification for lecture changes.
     * @param title Notification title
     * @param message Notification message body
     * @param lectureId Associated lecture ID for potential click actions
     */
    suspend fun showNotification(title: String, message: String, lectureId: Long)

    /**
     * Show a notification for multiple lecture changes (summary).
     * @param title Notification title
     * @param message Notification message body
     * @param changeCount Number of changes detected
     */
    suspend fun showSummaryNotification(title: String, message: String, changeCount: Int)
}

