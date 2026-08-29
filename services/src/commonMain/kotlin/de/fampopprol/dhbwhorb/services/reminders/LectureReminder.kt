/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import kotlinx.datetime.LocalDateTime

/**
 * One notification the system should show at [fireAt], on its own, without the app running.
 *
 * Fully formed on purpose: title and body are computed when the reminder is planned, not when it
 * fires. Whatever wakes up an hour later — an `AlarmManager` broadcast, an iOS notification
 * request — then needs neither the database nor a session, which is the difference between a
 * reminder that arrives and one that depends on the app still being alive.
 */
data class LectureReminder(
    /** Stable across replanning: the same lecture keeps the same id, so it is replaced, not doubled. */
    val id: String,
    val title: String,
    val body: String,
    val fireAt: LocalDateTime,
)
