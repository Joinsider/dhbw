/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestReminderTest {

    @Test
    fun fireAt_wholeMinuteStaysOneMinuteLater() {
        assertEquals(LocalDateTime(2026, 3, 4, 10, 1, 0), TestReminder.fireAt(LocalDateTime(2026, 3, 4, 10, 0, 0)))
    }

    @Test
    fun fireAt_roundsUpToTheNextWholeMinute() {
        assertEquals(LocalDateTime(2026, 3, 4, 10, 2, 0), TestReminder.fireAt(LocalDateTime(2026, 3, 4, 10, 0, 30)))
        assertEquals(LocalDateTime(2026, 3, 4, 10, 2, 0), TestReminder.fireAt(LocalDateTime(2026, 3, 4, 10, 0, 59)))
    }

    @Test
    fun fireAt_crossesMidnight() {
        assertEquals(LocalDateTime(2026, 3, 5, 0, 1, 0), TestReminder.fireAt(LocalDateTime(2026, 3, 4, 23, 59, 30)))
    }

    @Test
    fun fireAt_isAtLeastOneMinuteAhead_andOnAWholeMinute() {
        for (second in 0..59) {
            val now = LocalDateTime(2026, 3, 4, 10, 0, second)
            val fireAt = TestReminder.fireAt(now)
            assertEquals(0, fireAt.second, "seconds for $now")
            val expected = if (second == 0) LocalDateTime(2026, 3, 4, 10, 1, 0) else LocalDateTime(2026, 3, 4, 10, 2, 0)
            assertEquals(expected, fireAt, "fire time for $now")
        }
    }

    @Test
    fun create_usesTheReminderPrefixSoAReplanRemovesIt() {
        val reminder = TestReminder.create(LocalDateTime(2026, 3, 4, 10, 0, 30))
        assertTrue(reminder.id.startsWith("reminder_"), reminder.id)
        assertEquals(LocalDateTime(2026, 3, 4, 10, 2, 0), reminder.fireAt)
    }
}
