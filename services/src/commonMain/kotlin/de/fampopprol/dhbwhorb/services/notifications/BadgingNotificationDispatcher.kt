/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

/**
 * The [NotificationDispatcher] everything in the app is given: the platform's own, with the app
 * badge kept in step.
 *
 * Every notification that goes out adds to [badge] — one for a single change, the number of
 * changes for a summary — and taking the delivered notifications back clears it. Callers only
 * show notifications; whether and how the icon counts them is decided here, once, for every
 * caller there is and every one added later.
 *
 * A notification the system refuses (no permission) is not counted: the platform dispatcher
 * drops it silently, so the permission is asked first, the same way the platform side does.
 */
class BadgingNotificationDispatcher(
    private val platform: NotificationDispatcher,
    private val badge: AppBadge,
) : NotificationDispatcher by platform {

    override suspend fun showNotification(title: String, message: String, notificationKey: String) {
        val delivered = platform.hasPermission()
        platform.showNotification(title, message, notificationKey)
        if (delivered) badge.add(1)
    }

    override suspend fun showSummaryNotification(title: String, message: String, changeCount: Int) {
        val delivered = platform.hasPermission()
        platform.showSummaryNotification(title, message, changeCount)
        if (delivered) badge.add(changeCount)
    }

    override suspend fun cancelAllDelivered() {
        platform.cancelAllDelivered()
        badge.clear()
    }
}
