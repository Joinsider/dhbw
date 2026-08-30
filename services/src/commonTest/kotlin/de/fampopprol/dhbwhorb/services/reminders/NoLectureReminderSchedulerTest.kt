/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse

class NoLectureReminderSchedulerTest {

    private fun reminder(id: String) = LectureReminder(
        id = id,
        title = "Mathematik III",
        body = "in 60 minutes",
        fireAt = LocalDateTime(2026, 3, 4, 13, 0)
    )

    @Test
    fun replaceAll_withRemindersDoesNotThrow_thisPlatformJustIgnoresThem() = runTest {
        // Nothing to assert on directly — Desktop/macOS have no scheduler to hand these to. The
        // point of the test is that a non-empty list takes the "log and ignore" branch safely.
        NoLectureReminderScheduler().replaceAll(listOf(reminder("a"), reminder("b")))
    }

    @Test
    fun replaceAll_withNoRemindersDoesNotThrow() = runTest {
        NoLectureReminderScheduler().replaceAll(emptyList())
    }

    @Test
    fun firesExactly_isFalse_thisPlatformCannotWakeUpToFireOnTime() {
        assertFalse(NoLectureReminderScheduler().firesExactly())
    }
}
