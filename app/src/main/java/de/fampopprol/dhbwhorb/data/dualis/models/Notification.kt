/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.models

/**
 * Represents a notification from Dualis
 */
data class Notification(
    val id: String,
    val date: String,
    val time: String,
    val sender: String,
    val subject: String,
    val type: NotificationType,
    val isUnread: Boolean,
    val detailUrl: String?,
    val deleteUrl: String?
)

/**
 * Types of notifications that can be received from Dualis
 */
enum class NotificationType {
    SCHEDULE_CHANGE,
    SCHEDULE_SET,
    GENERAL_MESSAGE,
    UNKNOWN
}

/**
 * Container for notification data
 */
data class NotificationList(
    val unreadNotifications: List<Notification>,
    val totalUnreadCount: Int
)
