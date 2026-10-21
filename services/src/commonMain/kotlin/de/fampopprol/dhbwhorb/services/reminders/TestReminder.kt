/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import de.fampopprol.dhbwhorb.services.notifications.LectureNotificationTexts
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * The reminder behind the debug-only "Erinnerung in 1 Minute" button.
 *
 * Only for checking the scheduler by hand. Nothing in the real planning path uses it.
 */
object TestReminder {

    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * One minute after [now], rounded up to the next whole minute.
     *
     * The Apple scheduler matches calendar components down to the minute, so a test started at
     * 10:00:30 fires at 10:02:00, not at a second-precise 10:01:30. A time that is already a whole
     * minute stays put. Done on the wall-clock fields, like the rest of the reminder planning, so
     * the result stays in the user's own day.
     */
    fun fireAt(now: LocalDateTime): LocalDateTime {
        val minutesAhead = if (now.second == 0 && now.nanosecond == 0) 1 else 2
        val minuteOfDay = now.hour * 60 + now.minute + minutesAhead
        val date = now.date.plus(minuteOfDay / MINUTES_PER_DAY, DateTimeUnit.DAY)
        val inDay = minuteOfDay % MINUTES_PER_DAY
        return LocalDateTime(date.year, date.month, date.day, inDay / 60, inDay % 60)
    }

    /**
     * A reminder for [fireAt] of [now]. The id keeps the `reminder_` prefix the Apple scheduler
     * uses to find its own requests, so a later replan removes it.
     */
    fun create(now: LocalDateTime): LectureReminder {
        val (title, body) = LectureNotificationTexts.testReminder()
        return LectureReminder(
            id = "reminder_test_${now.toInstant(TimeZone.UTC).toEpochMilliseconds()}",
            title = title,
            body = body,
            fireAt = fireAt(now),
        )
    }
}
