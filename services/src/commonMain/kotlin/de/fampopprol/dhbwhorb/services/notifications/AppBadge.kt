/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Draws a number on the app icon. iOS and macOS do; Android shows the count on the notification
 * itself and the desktop has no icon to draw on, so both bind [None].
 */
fun interface BadgeDisplay {

    suspend fun show(count: Int)

    companion object {
        val None = BadgeDisplay { }
    }
}

/**
 * The number on the app icon: notifications shown since the app was last opened (ADR-0001).
 *
 * This is the only class that knows the count. Nothing that shows a notification touches it —
 * [BadgingNotificationDispatcher] adds to it for every notification that actually went out — and
 * nothing that opens the app does more than call [clear]. It used to be spread over the
 * notification manager, the logout cleaner and both Apple dispatchers, and every new way to show
 * a notification (the debug test buttons were the first) had to remember to count; they did not.
 *
 * The count lives in the settings, not in memory, because the hourly check runs in a process that
 * may be started for it alone, and the next launch has to see what that run added. Adding is a
 * read and a write, so it happens under [mutex]: two notifications in quick succession used to
 * read the same old value and one of them was lost.
 */
class AppBadge(
    private val store: NotificationPreferencesInteractor,
    private val display: BadgeDisplay,
) {
    companion object {
        private const val TAG = "AppBadge"
    }

    private val mutex = Mutex()

    /** The number the icon shows now. */
    fun count(): Int = store.getUnseenLectureChanges()

    /** Adds [count] shown notifications and redraws the icon. Returns the new total. */
    suspend fun add(count: Int = 1): Int {
        if (count <= 0) return count()
        val total = mutex.withLock { store.addUnseenLectureChanges(count) }
        Napier.d("Badge +$count -> $total", tag = TAG)
        display.show(total)
        return total
    }

    /** The user has seen them: the app came to the foreground, or the account was logged out. */
    suspend fun clear() {
        mutex.withLock { store.resetUnseenLectureChanges() }
        display.show(0)
    }
}
