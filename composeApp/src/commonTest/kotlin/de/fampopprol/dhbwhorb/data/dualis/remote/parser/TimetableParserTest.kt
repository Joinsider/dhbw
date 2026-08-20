/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DualisFixtures
import kotlinx.datetime.Month
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for [TimetableParser] against the HTML shapes documented in [DualisFixtures].
 * See the fixture KDoc on what these do and do not prove.
 */
class TimetableParserTest {

    private val parser = TimetableParser()

    // ── parseWeeklyView ──────────────────────────────────────────────────────

    @Test
    fun parseWeeklyView_extractsEveryAppointment() {
        val lectures = parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_FULL)

        assertEquals(3, lectures.size)
        assertEquals(
            listOf("T4INF2904.1", "T3INF2002.1", "T3INF1001.2"),
            lectures.map { it.shortSubjectName }
        )
    }

    @Test
    fun parseWeeklyView_mapsAppointmentToItsWeekday() {
        val lectures = parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_FULL)

        // Fixture week: Mo 03.11., Mi 05.11., Fr 07.11.
        assertEquals(3, lectures[0].startTime.day, "abbr='Montag Spalte 1' has to resolve to Mo 03.11.")
        assertEquals(5, lectures[1].startTime.day)
        assertEquals(7, lectures[2].startTime.day)
        assertTrue(lectures.all { it.startTime.month == Month.NOVEMBER })
    }

    @Test
    fun parseWeeklyView_readsStartAndEndTime() {
        val monday = parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_FULL).first()

        assertEquals(8, monday.startTime.hour)
        assertEquals(15, monday.startTime.minute)
        assertEquals(12, monday.endTime.hour)
        assertEquals(0, monday.endTime.minute)
    }

    @Test
    fun parseWeeklyView_readsSingleRoom() {
        val monday = parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_FULL).first()
        assertEquals("HOR-120", monday.location)
    }

    @Test
    fun parseWeeklyView_splitsConcatenatedRooms() {
        // Dualis renders two rooms without a separator: "HOR-231HOR-232".
        val wednesday = parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_FULL)[1]
        assertEquals("HOR-231, HOR-232", wednesday.location)
    }

    @Test
    fun parseWeeklyView_flagsExamsByCellColour() {
        val lectures = parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_FULL)

        assertTrue(lectures.none { it.shortSubjectName == "T4INF2904.1" && it.isTest })
        assertTrue(
            lectures.single { it.shortSubjectName == "T3INF1001.2" }.isTest,
            "background-color:#FF6666 marks an exam"
        )
    }

    @Test
    fun parseWeeklyView_buildsAbsoluteLinkFromRelativeHref() {
        val monday = parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_FULL).first()

        assertNotNull(monday.linkToIndividualPage)
        assertTrue(
            monday.linkToIndividualPage!!.startsWith("https://dualis.dhbw.de/scripts/mgrqispi.dll"),
            "Relative hrefs must be prefixed with the base URL, was: ${monday.linkToIndividualPage}"
        )
    }

    @Test
    fun parseWeeklyView_keepsFullTitleFromLinkAttribute() {
        val monday = parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_FULL).first()
        assertEquals("Paralleles Programmieren  HOR-TINF2024", monday.fullSubjectName)
    }

    // ── degradation ──────────────────────────────────────────────────────────

    @Test
    fun parseWeeklyView_onWeekWithoutLectures_returnsEmptyList() {
        assertEquals(
            emptyList(),
            parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_EMPTY),
            "A lecture-free week is valid, not an error"
        )
    }

    @Test
    fun parseWeeklyView_withoutWeekdayHeaders_dropsUndatableLectures() {
        // Without headers no date can be resolved, so the appointment has to be skipped
        // rather than dated to an arbitrary day.
        assertEquals(
            emptyList(),
            parser.parseWeeklyView(DualisFixtures.Timetable.WEEK_WITHOUT_HEADERS)
        )
    }

    @Test
    fun parseWeeklyView_onEmptyInput_returnsEmptyList() {
        assertEquals(emptyList(), parser.parseWeeklyView(DualisFixtures.EMPTY))
    }

    @Test
    fun parseWeeklyView_onSessionExpiredPage_returnsEmptyList() {
        assertEquals(emptyList(), parser.parseWeeklyView(DualisFixtures.SESSION_EXPIRED))
    }

    @Test
    fun parseWeeklyView_onNonHtmlInput_returnsEmptyList() {
        assertEquals(emptyList(), parser.parseWeeklyView(DualisFixtures.NOT_HTML))
    }

    @Test
    @Ignore // Known defect, scheduled for P3 — see comment.
    fun parseWeeklyView_weekInAnotherYear_usesTheYearFromTheHeader() {
        // extractWeekDates() takes the year from Clock.System.now() because the Dualis header
        // only carries "Mo 05.01." without a year. The pager allows +/- 1000 weeks, so any week
        // outside the current calendar year is dated to the wrong year — and a week spanning
        // New Year gets two different years' worth of days stamped with the same one.
        //
        // Fixing this needs the requested week's date range, which the parser does not have
        // today. It belongs in the repository layer (P3), which knows which week it asked for.
        val januaryWeek = DualisFixtures.Timetable.WEEK_FULL
            .replace("03.11.", "05.01.")
            .replace("Mo 03.11.", "Mo 05.01.")

        val lectures = parser.parseWeeklyView(januaryWeek)
        assertTrue(lectures.isNotEmpty())
        // Would need to assert the year the caller asked for, not the current one.
    }

    // ── parseIndividualPage ──────────────────────────────────────────────────

    @Test
    fun parseIndividualPage_extractsSubjectLecturersAndRooms() {
        val result = parser.parseIndividualPage(DualisFixtures.Timetable.INDIVIDUAL_PAGE)
        assertNotNull(result)

        val (subject, lecturers, rooms) = result
        assertEquals("Form. Sp+Autom.1+2 Gr. B", subject, "The HOR-* course code must be stripped")
        assertEquals(2, lecturers.size)
        assertTrue(lecturers.contains("B.Sc. Julian Schmidt"))
        assertEquals(listOf("HOR-231", "HOR-232"), rooms)
    }

    @Test
    fun parseIndividualPage_readsRoomsGivenAsLinks() {
        val result = parser.parseIndividualPage(DualisFixtures.Timetable.INDIVIDUAL_PAGE_ROOM_AS_LINK)
        assertNotNull(result)

        assertEquals(listOf("HOR-ONLINE"), result.third, "Online lectures render the room as <a>, not <span>")
        assertEquals(listOf("Dr. Erika Musterfrau"), result.second)
    }

    @Test
    fun parseIndividualPage_onEmptyInput_returnsNull() {
        assertNull(parser.parseIndividualPage(DualisFixtures.EMPTY))
    }

    @Test
    fun parseIndividualPage_onSessionExpiredPage_returnsNull() {
        assertNull(
            parser.parseIndividualPage(DualisFixtures.SESSION_EXPIRED),
            "An expired session must not be reported as a lecture"
        )
    }

    @Test
    fun parseIndividualPage_onNonHtmlInput_returnsNull() {
        assertNull(parser.parseIndividualPage(DualisFixtures.NOT_HTML))
    }
}
