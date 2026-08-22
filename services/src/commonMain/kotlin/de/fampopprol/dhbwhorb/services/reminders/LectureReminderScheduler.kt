/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

/**
 * Hands a set of reminders to the operating system's own scheduler.
 *
 * Only [replaceAll], never "add one": the timetable is the source of truth, and every replan starts
 * from the whole picture. Adding incrementally would mean tracking which reminders belong to a
 * lecture that has since been cancelled — the sort of bookkeeping that ends with a notification for
 * a lecture nobody is having.
 *
 * An interface rather than an `expect class`, like `WidgetRefresher` and unlike
 * `NotificationDispatcher`: the platform implementations need constructor parameters (Android wants
 * a `Context`), and a test wants to see what was handed over without a platform underneath.
 *
 * Implemented on the two platforms that can wake an app up: `UNUserNotificationCenter` on iOS and
 * `AlarmManager` on Android. Desktop and macOS bind [NoLectureReminderScheduler]; a scheduler that
 * only works while the app happens to be open is not a reminder.
 */
interface LectureReminderScheduler {

    /** Cancels everything previously scheduled and schedules [reminders] instead. */
    suspend fun replaceAll(reminders: List<LectureReminder>)

    /**
     * Whether the system will run these at the minute they name.
     *
     * `false` on Android when the user has taken the exact-alarm permission away — the reminders
     * then still arrive, the system may just hold them back to save power. The settings screen
     * says so rather than being quietly late.
     */
    fun firesExactly(): Boolean
}
