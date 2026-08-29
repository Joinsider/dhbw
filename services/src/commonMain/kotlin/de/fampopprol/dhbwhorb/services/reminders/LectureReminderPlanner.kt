/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.services.notifications.LectureNotificationTexts
import io.github.aakira.napier.Napier
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

private const val TAG = "LectureReminderPlanner"

/**
 * Turns the cached timetable into the alarms the system should hold.
 *
 * Reads only the local cache — a replan must work in a background wake-up with no session and no
 * network, and the cache is what the app already believes the timetable to be.
 *
 * Called after every monitoring run and whenever the setting changes. Each call replaces the whole
 * set, so a cancelled lecture stops reminding by simply not being planned again.
 */
class LectureReminderPlanner(
    private val lectureEventDao: LectureEventDao,
    private val preferences: NotificationPreferencesInteractor,
    private val scheduler: LectureReminderScheduler,
    private val clock: () -> LocalDateTime = { TimeHelper.now() },
) {
    companion object {
        /**
         * How many reminders are held at once.
         *
         * iOS keeps at most 64 pending notification requests per app and silently drops the rest,
         * so this stays well under it. Two weeks of lectures fit comfortably; anything further out
         * gets planned by a later run, of which there is one every hour.
         */
        const val MAX_REMINDERS = 40

        /** How far ahead to look. Beyond this the next replan will have taken over anyway. */
        const val HORIZON_DAYS = 14
    }

    suspend fun reschedule() {
        val lead = preferences.getReminderLeadMinutes()
        if (lead <= 0 || !preferences.getNotificationsEnabled()) {
            Napier.d("Reminders are off — clearing whatever was scheduled", tag = TAG)
            scheduler.replaceAll(emptyList())
            return
        }

        val now = clock()
        val horizon = now.date.plus(HORIZON_DAYS, DateTimeUnit.DAY)

        val reminders = lectureEventDao.getAll()
            .asSequence()
            .filter { it.startTime > now && it.startTime.date <= horizon }
            .sortedBy { it.startTime }
            .map { it to it.startTime.minusMinutes(lead) }
            // A lecture starting in half an hour cannot be reminded of an hour beforehand. Skipping
            // it is the honest outcome: firing immediately would say "in 60 minutes" about
            // something that starts in 30.
            .filter { (_, fireAt) -> fireAt > now }
            .take(MAX_REMINDERS)
            .map { (lecture, fireAt) -> lecture.toReminder(fireAt, lead) }
            .toList()

        Napier.d("Scheduling ${reminders.size} reminder(s), $lead minutes ahead", tag = TAG)
        scheduler.replaceAll(reminders)
    }

    private fun LectureEventEntity.toReminder(fireAt: LocalDateTime, lead: Int): LectureReminder {
        val (title, body) = LectureNotificationTexts.reminder(
            courseName = fullSubjectName?.takeIf { it.isNotBlank() } ?: shortSubjectName,
            location = location,
            startsAt = startTime,
            leadMinutes = lead,
        )
        return LectureReminder(
            // Subject and start, like a change notification's key — not the database id, which is
            // reassigned every time a week is written and would leave orphans behind.
            id = "reminder_${shortSubjectName}_$startTime",
            title = title,
            body = body,
            fireAt = fireAt,
        )
    }
}

/**
 * [minutes] before this wall-clock time.
 *
 * Done on the date rather than on an instant so it stays in the user's own day: a lecture at 08:15
 * with an hour's lead reminds at 07:15 whatever the time zone database thinks.
 */
internal fun LocalDateTime.minusMinutes(minutes: Int): LocalDateTime {
    val total = hour * 60 + minute - minutes
    val dayShift = if (total < 0) (total - 1439) / 1440 else total / 1440
    val inDay = total - dayShift * 1440
    val date = date.plus(dayShift, DateTimeUnit.DAY)
    return LocalDateTime(date.year, date.month, date.day, inDay / 60, inDay % 60)
}
