/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.session

import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.services.reminders.LectureReminder
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderScheduler
import de.fampopprol.dhbwhorb.services.widget.WidgetRefresher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingScheduler(
    private val onReplace: () -> Unit = {}
) : LectureReminderScheduler {
    var lastScheduled: List<LectureReminder>? = null

    override suspend fun replaceAll(reminders: List<LectureReminder>) {
        onReplace()
        lastScheduled = reminders
    }

    override fun firesExactly(): Boolean = true
}

private class RecordingDispatcher : NotificationDispatcher {
    var cancelled = 0

    override suspend fun requestPermission(): Boolean = true
    override suspend fun hasPermission(): Boolean = true
    override suspend fun showNotification(title: String, message: String, notificationKey: String) = Unit
    override suspend fun showSummaryNotification(title: String, message: String, changeCount: Int) = Unit
    override suspend fun cancelAllDelivered() {
        cancelled++
    }
}

class AppSessionDataCleanerTest {

    @Test
    fun clearing_cancelsRemindersNotificationsFilesAndRefreshesTheWidget() = runTest {
        val scheduler = RecordingScheduler()
        val dispatcher = RecordingDispatcher()
        var refreshes = 0
        var filesDeleted = 0

        AppSessionDataCleaner(
            reminders = scheduler,
            notifications = dispatcher,
            widgetRefresher = WidgetRefresher { refreshes++ },
            cachedFiles = CachedFileCleaner { filesDeleted++ },
        ).clearSessionData()

        assertEquals(emptyList(), scheduler.lastScheduled, "every alarm has to go")
        assertEquals(1, dispatcher.cancelled)
        assertEquals(1, filesDeleted)
        assertEquals(1, refreshes, "the widget still draws the old week until it is told")
    }

    @Test
    fun aFailingStep_doesNotStopTheOthers() = runTest {
        val dispatcher = RecordingDispatcher()
        var refreshes = 0

        AppSessionDataCleaner(
            reminders = RecordingScheduler { throw IllegalStateException("no alarm manager") },
            notifications = dispatcher,
            widgetRefresher = WidgetRefresher { refreshes++ },
            cachedFiles = null,
        ).clearSessionData()

        assertEquals(1, dispatcher.cancelled)
        assertEquals(1, refreshes)
    }

    @Test
    fun withoutAWidgetOrCachedFiles_theRestStillRuns() = runTest {
        val scheduler = RecordingScheduler()
        val dispatcher = RecordingDispatcher()

        AppSessionDataCleaner(
            reminders = scheduler,
            notifications = dispatcher,
            widgetRefresher = null,
            cachedFiles = null,
        ).clearSessionData()

        assertTrue(scheduler.lastScheduled?.isEmpty() == true)
        assertEquals(1, dispatcher.cancelled)
    }
}
