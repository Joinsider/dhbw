/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import androidx.test.core.app.ApplicationProvider
import de.fampopprol.dhbwhorb.testutil.TestPlatformSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The bookkeeping half of [AndroidLectureReminderScheduler]: which ids it remembers as scheduled,
 * so the next replan can cancel them. Robolectric supplies the AlarmManager shadow.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidLectureReminderSchedulerTest {

    private val settings = TestPlatformSettings()
    private val scheduler = AndroidLectureReminderScheduler(
        ApplicationProvider.getApplicationContext(),
        settings,
    )

    private fun reminder(id: String) = LectureReminder(
        id = id,
        title = "Vorlesung $id",
        body = "Gleich",
        fireAt = LocalDateTime(2030, 1, 7, 9, 0),
    )

    private fun rememberedIds(): String? = settings.getStringOrNull("lecture_reminder_scheduled_ids")

    @Test
    fun replaceAll_remembersTheIdsItScheduled() = runTest {
        scheduler.replaceAll(listOf(reminder("a"), reminder("b")))

        assertEquals("a\nb", rememberedIds())
    }

    @Test
    fun replaceAll_replacesThePreviousSet() = runTest {
        scheduler.replaceAll(listOf(reminder("a"), reminder("b")))

        scheduler.replaceAll(listOf(reminder("c")))

        assertEquals("c", rememberedIds())
    }

    @Test
    fun add_appendsTheIdSoTheNextReplanCancelsIt() = runTest {
        scheduler.replaceAll(listOf(reminder("a")))

        scheduler.add(reminder("b"))

        assertEquals("a\nb", rememberedIds())
    }

    @Test
    fun add_ofAnAlreadyRememberedIdDoesNotDuplicateIt() = runTest {
        scheduler.replaceAll(listOf(reminder("a")))

        scheduler.add(reminder("a"))

        assertEquals("a", rememberedIds())
    }

    @Test
    fun add_onAnEmptyStore_startsTheSet() = runTest {
        scheduler.add(reminder("solo"))

        assertEquals("solo", rememberedIds())
    }
}
