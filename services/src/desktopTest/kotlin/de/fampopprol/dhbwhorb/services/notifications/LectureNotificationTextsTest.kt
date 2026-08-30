/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import kotlinx.datetime.LocalDateTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [LectureNotificationTexts.strings] picks its table from [de.fampopprol.dhbwhorb.util.currentLanguage],
 * which on the desktop target reads `Locale.getDefault()` — so these tests drive the two tables by
 * switching the JVM default locale, the same thing the OS does on a real machine.
 */
class LectureNotificationTextsTest {

    private lateinit var originalLocale: Locale

    @BeforeTest
    fun captureLocale() {
        originalLocale = Locale.getDefault()
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    private fun lecture(subject: String, location: String = "HOR-100") = LectureEventEntity(
        lectureId = 1,
        shortSubjectName = subject,
        fullSubjectName = subject,
        startTime = LocalDateTime(2026, 3, 2, 8, 0),
        endTime = LocalDateTime(2026, 3, 2, 9, 30),
        location = location,
    )

    @Test
    fun single_timeChange_german() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.TimeChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            oldStartTime = LocalDateTime(2026, 3, 2, 8, 0),
            newStartTime = LocalDateTime(2026, 3, 2, 10, 0),
            oldEndTime = LocalDateTime(2026, 3, 2, 9, 30),
            newEndTime = LocalDateTime(2026, 3, 2, 11, 30),
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Verschoben: Mathematik 1", title)
        assertEquals("Von 02.03., 08:00 Uhr auf 02.03., 10:00 Uhr", body)
    }

    @Test
    fun single_timeChange_english() {
        Locale.setDefault(Locale.ENGLISH)
        val change = LectureChange.TimeChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            oldStartTime = LocalDateTime(2026, 3, 2, 8, 0),
            newStartTime = LocalDateTime(2026, 3, 2, 10, 0),
            oldEndTime = LocalDateTime(2026, 3, 2, 9, 30),
            newEndTime = LocalDateTime(2026, 3, 2, 11, 30),
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Moved: Mathematik 1", title)
        assertEquals("From 02/03, 08:00 to 02/03, 10:00", body)
    }

    @Test
    fun single_timeChange_withoutOldStart_german() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.TimeChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            oldStartTime = null,
            newStartTime = LocalDateTime(2026, 3, 2, 10, 0),
            oldEndTime = null,
            newEndTime = LocalDateTime(2026, 3, 2, 11, 30),
        )

        val (_, body) = LectureNotificationTexts.single(change)

        assertEquals("Von unbekannt auf 02.03., 10:00 Uhr", body)
    }

    @Test
    fun single_locationChange_german() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.LocationChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldLocation = "HOR-100",
            newLocation = "HOR-200",
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Raumwechsel: Mathematik 1", title)
        assertEquals("02.03., 08:00 Uhr: HOR-100 → HOR-200", body)
    }

    @Test
    fun single_locationChange_blankLocations_useDash_german() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.LocationChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldLocation = "",
            newLocation = "",
        )

        val (_, body) = LectureNotificationTexts.single(change)

        assertEquals("02.03., 08:00 Uhr: — → —", body)
    }

    @Test
    fun single_lecturerChange_german_usesGrammaticalCaseFallbacks() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.LecturerChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldLecturers = emptyList(),
            newLecturers = emptyList(),
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Dozentenwechsel: Mathematik 1", title)
        // "niemandem" (dative, after "statt") vs "niemand" (nominative, after "jetzt") — the two
        // German fallback words differ by grammatical case, which is exactly why this table isn't
        // templated into one shared implementation with English.
        assertEquals("02.03., 08:00 Uhr: statt niemandem jetzt niemand", body)
    }

    @Test
    fun single_lecturerChange_english() {
        Locale.setDefault(Locale.ENGLISH)
        val change = LectureChange.LecturerChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldLecturers = listOf("Schmidt"),
            newLecturers = listOf("Müller"),
        )

        val (_, body) = LectureNotificationTexts.single(change)

        assertEquals("02/03, 08:00: Schmidt replaced by Müller", body)
    }

    @Test
    fun single_typeChange_fromExam_german() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.TypeChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldIsTest = true,
            newIsTest = false,
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Doch keine Prüfung: Mathematik 1", title)
        assertEquals("Am 02.03., 08:00 Uhr wieder eine normale Vorlesung", body)
    }

    @Test
    fun single_typeChange_toExam_english() {
        Locale.setDefault(Locale.ENGLISH)
        val change = LectureChange.TypeChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldIsTest = false,
            newIsTest = true,
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Now an exam: Mathematik 1", title)
        assertEquals("On 02/03, 08:00, instead of the lecture", body)
    }

    @Test
    fun single_newLecture_german() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.NewLecture(
            lectureId = 1,
            courseName = "Mathematik 1",
            lecture = lecture("Mathematik 1"),
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Neu im Plan: Mathematik 1", title)
        assertEquals("Am 02.03., 08:00 Uhr", body)
    }

    @Test
    fun single_cancellation_english() {
        Locale.setDefault(Locale.ENGLISH)
        val change = LectureChange.Cancellation(
            lectureId = 1,
            courseName = "Mathematik 1",
            cancelledLecture = lecture("Mathematik 1"),
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Cancelled: Mathematik 1", title)
        assertEquals("On 02/03, 08:00", body)
    }

    @Test
    fun single_locationChange_english() {
        Locale.setDefault(Locale.ENGLISH)
        val change = LectureChange.LocationChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldLocation = "HOR-100",
            newLocation = "HOR-200",
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Room change: Mathematik 1", title)
        assertEquals("02/03, 08:00: HOR-100 → HOR-200", body)
    }

    @Test
    fun single_timeChange_withoutOldStart_english() {
        Locale.setDefault(Locale.ENGLISH)
        val change = LectureChange.TimeChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            oldStartTime = null,
            newStartTime = LocalDateTime(2026, 3, 2, 10, 0),
            oldEndTime = null,
            newEndTime = LocalDateTime(2026, 3, 2, 11, 30),
        )

        val (_, body) = LectureNotificationTexts.single(change)

        assertEquals("From unknown to 02/03, 10:00", body)
    }

    @Test
    fun reminder_wholeHour_english() {
        Locale.setDefault(Locale.ENGLISH)
        val (title, body) = LectureNotificationTexts.reminder(
            courseName = "Mathematik 1",
            location = "HOR-100",
            startsAt = LocalDateTime(2026, 3, 2, 8, 0),
            leadMinutes = 60,
        )

        assertEquals("In an hour: Mathematik 1", title)
        assertEquals("08:00, HOR-100", body)
    }

    @Test
    fun reminder_multipleWholeHours_english() {
        Locale.setDefault(Locale.ENGLISH)
        val (title, _) = LectureNotificationTexts.reminder(
            courseName = "Mathematik 1",
            location = "",
            startsAt = LocalDateTime(2026, 3, 2, 8, 0),
            leadMinutes = 120,
        )

        assertEquals("In 2 hours: Mathematik 1", title)
    }

    @Test
    fun single_typeChange_toExam_german() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.TypeChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldIsTest = false,
            newIsTest = true,
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Jetzt eine Prüfung: Mathematik 1", title)
        assertEquals("Am 02.03., 08:00 Uhr statt der Vorlesung", body)
    }

    @Test
    fun single_typeChange_fromExam_english() {
        Locale.setDefault(Locale.ENGLISH)
        val change = LectureChange.TypeChange(
            lectureId = 1,
            courseName = "Mathematik 1",
            occursAt = LocalDateTime(2026, 3, 2, 8, 0),
            oldIsTest = true,
            newIsTest = false,
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("No longer an exam: Mathematik 1", title)
        assertEquals("On 02/03, 08:00, an ordinary lecture again", body)
    }

    @Test
    fun single_cancellation_german() {
        Locale.setDefault(Locale.GERMAN)
        val change = LectureChange.Cancellation(
            lectureId = 1,
            courseName = "Mathematik 1",
            cancelledLecture = lecture("Mathematik 1"),
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Fällt aus: Mathematik 1", title)
        assertEquals("Am 02.03., 08:00 Uhr", body)
    }

    @Test
    fun single_newLecture_english() {
        Locale.setDefault(Locale.ENGLISH)
        val change = LectureChange.NewLecture(
            lectureId = 1,
            courseName = "Mathematik 1",
            lecture = lecture("Mathematik 1"),
        )

        val (title, body) = LectureNotificationTexts.single(change)

        assertEquals("Added: Mathematik 1", title)
        assertEquals("On 02/03, 08:00", body)
    }

    @Test
    fun summary_countsEachCategory_german() {
        Locale.setDefault(Locale.GERMAN)
        val changes = listOf(
            LectureChange.Cancellation(1, "A", lecture("A")),
            LectureChange.TimeChange(
                2, "B",
                LocalDateTime(2026, 3, 2, 8, 0), LocalDateTime(2026, 3, 2, 9, 0),
                LocalDateTime(2026, 3, 2, 9, 0), LocalDateTime(2026, 3, 2, 10, 0),
            ),
            LectureChange.NewLecture(3, "C", lecture("C")),
        )

        val (title, body) = LectureNotificationTexts.summary(changes)

        assertEquals("Stundenplan geändert", title)
        assertEquals("1 Absage, 1 Verschiebung und 1 neue Vorlesung", body)
    }

    @Test
    fun summary_pluralizesWhenMoreThanOnePerCategory_english() {
        Locale.setDefault(Locale.ENGLISH)
        val changes = listOf(
            LectureChange.NewLecture(1, "A", lecture("A")),
            LectureChange.NewLecture(2, "B", lecture("B")),
        )

        val (title, body) = LectureNotificationTexts.summary(changes)

        assertEquals("Timetable changed", title)
        assertEquals("2 new lectures", body)
    }

    @Test
    fun summary_empty_returnsEmptyBody() {
        Locale.setDefault(Locale.ENGLISH)
        val (_, body) = LectureNotificationTexts.summary(emptyList())
        assertEquals("", body)
    }

    @Test
    fun reminder_wholeHour_german() {
        Locale.setDefault(Locale.GERMAN)
        val (title, body) = LectureNotificationTexts.reminder(
            courseName = "Mathematik 1",
            location = "HOR-100",
            startsAt = LocalDateTime(2026, 3, 2, 8, 0),
            leadMinutes = 60,
        )

        assertEquals("In einer Stunde: Mathematik 1", title)
        assertEquals("08:00 Uhr, HOR-100", body)
    }

    @Test
    fun reminder_multipleWholeHours_german() {
        Locale.setDefault(Locale.GERMAN)
        val (title, _) = LectureNotificationTexts.reminder(
            courseName = "Mathematik 1",
            location = "",
            startsAt = LocalDateTime(2026, 3, 2, 8, 0),
            leadMinutes = 120,
        )

        assertEquals("In 2 Stunden: Mathematik 1", title)
    }

    @Test
    fun reminder_nonWholeHour_english_withoutLocation() {
        Locale.setDefault(Locale.ENGLISH)
        val (title, body) = LectureNotificationTexts.reminder(
            courseName = "Mathematik 1",
            location = "",
            startsAt = LocalDateTime(2026, 3, 2, 8, 5),
            leadMinutes = 15,
        )

        assertEquals("In 15 minutes: Mathematik 1", title)
        assertEquals("08:05", body)
    }
}
