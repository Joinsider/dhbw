/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

/**
 * Shows a notification through whatever the platform offers.
 *
 * An interface with one implementation per platform, like [de.fampopprol.dhbwhorb.services.reminders.LectureReminderScheduler]
 * and [de.fampopprol.dhbwhorb.services.widget.WidgetRefresher]. It used to be an `expect class`,
 * and because `expect class` forbids a platform-specific constructor parameter, the Android side
 * kept its `Context` in a static field that `DualisApplication.onCreate()` had to fill. Forgetting
 * that call was not a compile error — it crashed the settings screen from P2 to P4. The Context is
 * now a constructor parameter that Koin supplies, so there is nothing left to forget.
 */
interface NotificationDispatcher {

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
     * Remove the notifications this app has already delivered.
     *
     * Called on logout: a notification saying a lecture moved is as much the previous user's data
     * as the row it was derived from, and it survives the app being closed.
     *
     * Default-empty rather than abstract, because on the desktop a notification is handed to the
     * system and forgotten — there is nothing left to take back.
     */
    suspend fun cancelAllDelivered() {
        // No-op by default — see the class doc above: desktop hands notifications to the system
        // and forgets them, so there is nothing here to take back.
    }

    /**
     * Show a notification for multiple lecture changes (summary).
     * @param title Notification title
     * @param message Notification message body
     * @param changeCount Number of changes detected
     */
    suspend fun showSummaryNotification(title: String, message: String, changeCount: Int)
}
