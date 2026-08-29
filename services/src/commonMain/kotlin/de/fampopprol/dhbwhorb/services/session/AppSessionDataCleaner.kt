/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.session

import de.fampopprol.dhbwhorb.domain.session.SessionDataCleaner
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderScheduler
import de.fampopprol.dhbwhorb.services.widget.WidgetRefresher
import io.github.aakira.napier.Napier

private const val TAG = "SessionDataCleaner"

/**
 * Everything the app left outside its own storage, undone.
 *
 * Clearing the database is not enough on a phone: the alarms for next week's lectures are held by
 * the system, the widget keeps drawing whatever it last read, notifications about changed lectures
 * stay in the shade, and a document that was opened is a file in the cache directory. All four
 * outlive both the session and the tables it came from.
 *
 * Each step is independent and none may stop the others — a widget that cannot be refreshed must
 * not leave next week's alarms in place.
 */
class AppSessionDataCleaner(
    private val reminders: LectureReminderScheduler,
    private val notifications: NotificationDispatcher,
    private val widgetRefresher: WidgetRefresher?,
    private val cachedFiles: CachedFileCleaner?,
) : SessionDataCleaner {

    override suspend fun clearSessionData() {
        step("cancelling scheduled reminders") { reminders.replaceAll(emptyList()) }
        step("removing delivered notifications") { notifications.cancelAllDelivered() }
        step("deleting cached files") { cachedFiles?.deleteAll() }
        // Last: the widget reads the database, which by now is empty, so this is what makes it
        // draw an empty week instead of the previous user's.
        step("refreshing the widget") { widgetRefresher?.requestRefresh() }
    }

    private suspend fun step(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Napier.e("Logout: $what failed: ${e.message}", e, tag = TAG)
        }
    }
}

/**
 * Files the app wrote outside the database — on Android, the copies in the cache directory that
 * let a viewer open a downloaded document.
 *
 * Bound only where such files exist; [AppSessionDataCleaner] takes it as null everywhere else.
 */
fun interface CachedFileCleaner {
    fun deleteAll()
}
