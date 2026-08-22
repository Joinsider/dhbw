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
     *
     * @param notificationKey identifies this notification. Two calls with the same key replace
     *   each other, which is why it comes from [LectureChange.notificationKey] rather than from a
     *   database id — a lecture that does not exist yet has no id, and every one of them used to
     *   land under `lecture_0`.
     */
    suspend fun showNotification(title: String, message: String, notificationKey: String)

    /**
     * Show a notification for multiple lecture changes (summary).
     * @param title Notification title
     * @param message Notification message body
     * @param changeCount Number of changes detected
     */
    suspend fun showSummaryNotification(title: String, message: String, changeCount: Int)
}

