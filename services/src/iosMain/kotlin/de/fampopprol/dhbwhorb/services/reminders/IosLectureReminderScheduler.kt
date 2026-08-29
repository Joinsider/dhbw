/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.number
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

private const val TAG = "LectureReminderScheduler"
private const val ID_PREFIX = "reminder_"

/**
 * iOS holds the reminders itself, as pending notification requests.
 *
 * No alarm and no background task in between: a `UNCalendarNotificationTrigger` is a notification
 * the system will show at a wall-clock time whether or not the app ever runs again. It is also the
 * reason [LectureReminder] carries finished text — by the time it fires there is nothing left to
 * ask.
 *
 * Wall-clock and not an interval, so a reminder for next Tuesday at 07:15 stays at 07:15 across a
 * change of time zone rather than drifting by the offset.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLectureReminderScheduler : LectureReminderScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun replaceAll(reminders: List<LectureReminder>) {
        removeOurPendingRequests()

        reminders.forEach { reminder ->
            val content = UNMutableNotificationContent().apply {
                setTitle(reminder.title)
                setBody(reminder.body)
                setSound(UNNotificationSound.defaultSound())
            }

            val components = NSDateComponents().apply {
                year = reminder.fireAt.year.toLong()
                month = reminder.fireAt.month.number.toLong()
                day = reminder.fireAt.day.toLong()
                hour = reminder.fireAt.hour.toLong()
                minute = reminder.fireAt.minute.toLong()
            }

            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(
                    identifier = reminder.id,
                    content = content,
                    trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                        dateComponents = components,
                        repeats = false,
                    ),
                ),
            ) { error ->
                if (error != null) Napier.e("Could not schedule ${reminder.id}: $error", tag = TAG)
            }
        }
        Napier.d("Handed ${reminders.size} reminder(s) to iOS", tag = TAG)
    }

    /**
     * iOS keeps at most 64 pending requests per app, so ours are removed by name rather than with
     * `removeAllPendingNotificationRequests` — a future feature scheduling anything else must not
     * lose it to a replan of the timetable.
     */
    private suspend fun removeOurPendingRequests() = suspendCancellableCoroutine { continuation ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            val ours = requests.orEmpty()
                .mapNotNull { (it as? UNNotificationRequest)?.identifier }
                .filter { it.startsWith(ID_PREFIX) }
            if (ours.isNotEmpty()) {
                center.removePendingNotificationRequestsWithIdentifiers(ours)
                Napier.d("Removed ${ours.size} previously scheduled reminder(s)", tag = TAG)
            }
            continuation.resume(Unit)
        }
    }

    /** iOS delivers a calendar trigger at the minute it names. */
    override fun firesExactly(): Boolean = true
}
