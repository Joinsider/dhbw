/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import io.github.aakira.napier.Napier

/**
 * Accepts reminders and does nothing with them.
 *
 * Bound on Desktop and macOS, where there is no way to wake the app up: no background service and
 * no system scheduler to hand an alarm to. A "reminder" there would only arrive while the window
 * happens to be open — precisely when the user can see the timetable anyway — and pretending
 * otherwise would be worse than the gap.
 */
class NoLectureReminderScheduler : LectureReminderScheduler {
    override suspend fun replaceAll(reminders: List<LectureReminder>) {
        if (reminders.isNotEmpty()) {
            Napier.d(
                "Ignoring ${reminders.size} reminder(s): this platform cannot be woken up",
                tag = "LectureReminderScheduler",
            )
        }
    }

    override fun firesExactly(): Boolean = false
}
