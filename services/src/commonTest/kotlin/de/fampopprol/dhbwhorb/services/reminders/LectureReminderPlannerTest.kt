/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.reminders

import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.settings.PlatformSettings
import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
import de.fampopprol.dhbwhorb.testutil.MockLectureEventDao
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the planner hands to the system is the whole feature: once an alarm is scheduled, nothing
 * gets a second look at it until the next replan.
 */
class LectureReminderPlannerTest {

    private val now = LocalDateTime(2026, 3, 4, 9, 0)

    @Test
    fun `plans one reminder per upcoming lecture at the chosen lead time`() = runTest {
        val scheduler = RecordingScheduler()
        planner(
            lectures = listOf(lecture("MATHE", day = 4, from = 14), lecture("PROG", day = 5, from = 10)),
            leadMinutes = 60,
            scheduler = scheduler,
        ).reschedule()

        assertEquals(2, scheduler.scheduled.size)
        assertEquals(LocalDateTime(2026, 3, 4, 13, 0), scheduler.scheduled[0].fireAt)
        assertEquals(LocalDateTime(2026, 3, 5, 9, 0), scheduler.scheduled[1].fireAt)
    }

    @Test
    fun `a lead time that reaches back into the previous day still lands on it`() = runTest {
        val scheduler = RecordingScheduler()
        planner(
            lectures = listOf(lecture("FRUEH", day = 6, from = 0, minute = 30)),
            leadMinutes = 60,
            scheduler = scheduler,
        ).reschedule()

        assertEquals(LocalDateTime(2026, 3, 5, 23, 30), scheduler.scheduled.single().fireAt)
    }

    @Test
    fun `lectures that have started are not reminded of`() = runTest {
        val scheduler = RecordingScheduler()
        planner(
            lectures = listOf(lecture("VORBEI", day = 4, from = 8), lecture("SPAETER", day = 4, from = 14)),
            leadMinutes = 30,
            scheduler = scheduler,
        ).reschedule()

        assertEquals(listOf("SPAETER"), scheduler.scheduled.map { it.title.substringAfterLast(": ") })
    }

    @Test
    fun `a lecture closer than the lead time is skipped rather than fired at once`() = runTest {
        val scheduler = RecordingScheduler()
        // Starts in half an hour; an hour's warning is no longer possible.
        planner(
            lectures = listOf(lecture("GLEICH", day = 4, from = 9, minute = 30)),
            leadMinutes = 60,
            scheduler = scheduler,
        ).reschedule()

        assertTrue(scheduler.scheduled.isEmpty(), "${scheduler.scheduled}")
    }

    @Test
    fun `switching the reminder off clears what was scheduled`() = runTest {
        val scheduler = RecordingScheduler()
        planner(
            lectures = listOf(lecture("MATHE", day = 5, from = 10)),
            leadMinutes = 0,
            scheduler = scheduler,
        ).reschedule()

        assertEquals(1, scheduler.calls, "the platform still has to be told to forget the old set")
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `nothing is planned while notifications are off altogether`() = runTest {
        val scheduler = RecordingScheduler()
        planner(
            lectures = listOf(lecture("MATHE", day = 5, from = 10)),
            leadMinutes = 60,
            notificationsEnabled = false,
            scheduler = scheduler,
        ).reschedule()

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `never hands over more than iOS will hold`() = runTest {
        val scheduler = RecordingScheduler()
        // Three a day for two weeks is more than the cap; iOS silently drops anything past 64.
        val many = (4..17).flatMap { day ->
            listOf(
                lecture("A", day = day, from = 10),
                lecture("B", day = day, from = 14),
                lecture("C", day = day, from = 16),
            )
        }
        planner(lectures = many, leadMinutes = 30, scheduler = scheduler).reschedule()

        assertEquals(LectureReminderPlanner.MAX_REMINDERS, scheduler.scheduled.size)
    }

    @Test
    fun `two lectures never share an id`() = runTest {
        val scheduler = RecordingScheduler()
        planner(
            lectures = listOf(lecture("MATHE", day = 5, from = 10), lecture("MATHE", day = 5, from = 14)),
            leadMinutes = 30,
            scheduler = scheduler,
        ).reschedule()

        val ids = scheduler.scheduled.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "an id collision silently replaces a reminder: $ids")
    }

    // ── Scaffolding ─────────────────────────────────────────────────────────────────────────

    private fun planner(
        lectures: List<LectureEventEntity>,
        leadMinutes: Int,
        scheduler: RecordingScheduler,
        notificationsEnabled: Boolean = true,
    ): LectureReminderPlanner {
        val preferences = NotificationPreferencesInteractor(
            NotificationPreferences(SettingsStorage(InMemorySettings(), FakeSecureStorage()))
        )
        preferences.setNotificationsEnabled(notificationsEnabled)
        preferences.setReminderLeadMinutes(leadMinutes)

        return LectureReminderPlanner(
            lectureEventDao = StoredLectureDao(lectures),
            preferences = preferences,
            scheduler = scheduler,
            clock = { now },
        )
    }

    private fun lecture(subject: String, day: Int, from: Int, minute: Int = 0) = LectureEventEntity(
        lectureId = 0,
        shortSubjectName = subject,
        fullSubjectName = subject,
        startTime = LocalDateTime(2026, 3, day, from, minute),
        endTime = LocalDateTime(2026, 3, day, from + 2, minute),
        location = "HOR-100",
    )

    private class StoredLectureDao(private val stored: List<LectureEventEntity>) : MockLectureEventDao() {
        override suspend fun getAll(): List<LectureEventEntity> = stored
    }

    private class RecordingScheduler : LectureReminderScheduler {
        var scheduled: List<LectureReminder> = emptyList()
        var calls = 0

        override suspend fun replaceAll(reminders: List<LectureReminder>) {
            calls++
            scheduled = reminders
        }

        override fun firesExactly(): Boolean = true
    }

    private class InMemorySettings : PlatformSettings {
        private val values = mutableMapOf<String, String>()
        override fun getStringOrNull(key: String): String? = values[key]
        override fun setString(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }
}
