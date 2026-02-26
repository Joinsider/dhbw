/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UserNotifications.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS implementation of NotificationDispatcher using UNUserNotificationCenter.
 */
@OptIn(ExperimentalForeignApi::class)
actual class NotificationDispatcher {

    companion object {
        private const val TAG = "NotificationDispatcher"
        private const val CATEGORY_LECTURE_CHANGE = "LECTURE_CHANGE"
    }

    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()

    init {
        Napier.d("NotificationDispatcher (iOS) instance created", tag = TAG)
    }

    /**
     * Request notification permission from the user.
     */
    actual suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        Napier.d("requestPermission (iOS) called", tag = TAG)
        notificationCenter.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, error ->
            if (error != null) {
                Napier.e("Error requesting notification permission: $error", tag = TAG)
                continuation.resume(false)
            } else {
                Napier.d("Notification permission ${if (granted) "granted" else "denied"}", tag = TAG)
                continuation.resume(granted)
            }
        }
    }

    /**
     * Check if notification permission is currently granted.
     */
    actual suspend fun hasPermission(): Boolean = suspendCoroutine { continuation ->
        Napier.d("hasPermission (iOS) checking current authorization status", tag = TAG)
        notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
            val granted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
            Napier.d("hasPermission (iOS) -> $granted", tag = TAG)
            continuation.resume(granted)
        }
    }

    /**
     * Show a notification for a single lecture change.
     */
    actual suspend fun showNotification(title: String, message: String, lectureId: Long) {
        if (!hasPermission()) {
            Napier.w("Cannot show notification: permission not granted", tag = TAG)
            return
        }

        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(message)
            setSound(UNNotificationSound.defaultSound())
            setCategoryIdentifier(CATEGORY_LECTURE_CHANGE)
        }

        val identifier = "lecture_$lectureId"
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = content,
            trigger = null // Show immediately
        )

        Napier.d("showNotification (iOS) -> adding request id=$identifier title='$title' message='$message'", tag = TAG)
        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Napier.e("Error showing notification: $error", tag = TAG)
            } else {
                Napier.d("Notification shown for lecture $lectureId", tag = TAG)
            }
        }
    }

    /**
     * Show a summary notification for multiple lecture changes.
     */
    actual suspend fun showSummaryNotification(title: String, message: String, changeCount: Int) {
        if (!hasPermission()) {
            Napier.w("Cannot show notification: permission not granted", tag = TAG)
            return
        }

        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(message)
            setSound(UNNotificationSound.defaultSound())
            setBadge(changeCount)
            setCategoryIdentifier(CATEGORY_LECTURE_CHANGE)
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = "lecture_changes_summary",
            content = content,
            trigger = null // Show immediately
        )

        Napier.d("showSummaryNotification (iOS) -> adding summary request, count=$changeCount", tag = TAG)
        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                Napier.e("Error showing summary notification: $error", tag = TAG)
            } else {
                Napier.d("Summary notification shown for $changeCount changes", tag = TAG)
            }
        }
    }
}
