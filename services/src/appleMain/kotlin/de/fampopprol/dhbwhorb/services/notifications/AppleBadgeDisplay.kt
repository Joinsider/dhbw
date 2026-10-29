/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import io.github.aakira.napier.Napier
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The icon badge on iOS and macOS. Absolute: the number is always the total [AppBadge] holds, so
 * the icon cannot drift from it the way an increment sent along with a notification could.
 */
class AppleBadgeDisplay : BadgeDisplay {

    companion object {
        private const val TAG = "AppleBadgeDisplay"
    }

    override suspend fun show(count: Int) {
        UNUserNotificationCenter.currentNotificationCenter().setBadgeCount(count.toLong()) { error ->
            if (error != null) Napier.e("Error setting app badge to $count: $error", tag = TAG)
        }
    }
}
