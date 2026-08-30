/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DualisFixtures
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
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
    fun parseWeeklyView_weekInAnotherYear_takesTheYearFromTheRequestedWeek() {
        // Dualis' headers carry no year ("Mo 05.01."). Until P3 the parser filled in the current
        // one from the clock, so every week the pager reached outside this year — it allows
        // +/- 1000 — came back dated wrongly.
        val januaryWeek = DualisFixtures.Timetable.WEEK_FULL
            .replace("03.11.", "05.01.")
            .replace("04.11.", "06.01.")
            .replace("05.11.", "07.01.")
            .replace("06.11.", "08.01.")
            .replace("07.11.", "09.01.")

        val lectures = parser.parseWeeklyView(januaryWeek, weekStart = LocalDate(2030, 1, 5))

        assertTrue(lectures.isNotEmpty())
        assertTrue(
            lectures.all { it.startTime.year == 2030 },
            "Every lecture must be dated in the year of the week that was asked for"
        )
    }

    @Test
    fun parseWeeklyView_weekAcrossNewYear_datesEachDayInItsOwnYear() {
        // Mon 30.12.2030 to Fri 03.01.2031: one week, two years. Deciding the year once per week
        // would stamp five days with the same one.
        val turnOfYearWeek = DualisFixtures.Timetable.WEEK_FULL
            .replace("03.11.", "30.12.")
            .replace("04.11.", "31.12.")
            .replace("05.11.", "01.01.")
            .replace("06.11.", "02.01.")
            .replace("07.11.", "03.01.")

        val lectures = parser.parseWeeklyView(turnOfYearWeek, weekStart = LocalDate(2030, 12, 30))

        assertTrue(lectures.isNotEmpty())
        for (lecture in lectures) {
            val expectedYear = if (lecture.startTime.month == Month.DECEMBER) 2030 else 2031
            assertEquals(
                expectedYear,
                lecture.startTime.year,
                "${lecture.startTime} landed in the wrong year"
            )
        }
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

    // ── extractWeekDates edge cases ────────────────────────────────────────────

    @Test
    fun parseWeeklyView_headerWithInvalidMonth_isCaughtAndReturnsEmptyList() {
        // "13" is not a valid month; Month(13) throws, which must be caught rather than crash
        // the whole parse.
        val invalidMonthWeek = DualisFixtures.Timetable.WEEK_FULL.replace("Mo 03.11.", "Mo 03.13.")

        assertEquals(emptyList(), parser.parseWeeklyView(invalidMonthWeek))
    }

    @Test
    fun parseWeeklyView_resolvesSaturdayAndSundayHeaders() {
        val weekendWeek = """
            <html><body>
            <table class="nb rw-table rw-all">
              <thead>
                <tr>
                  <th class="fixedTimeColumn">Zeit</th>
                  <th class="weekday" abbr="Samstag"><a href="#">Sa 08.11.</a></th>
                  <th class="weekday" abbr="Sonntag"><a href="#">So 09.11.</a></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="appointment" style="background-color:#FFFFFF;" abbr="Samstag Spalte 1">
                    <span class="timePeriod">08:15 - 12:00 HOR-120</span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?ARGUMENTS=-N1" class="link" title="Samstagskurs">T1</a>
                  </td>
                  <td class="appointment" style="background-color:#FFFFFF;" abbr="Sonntag Spalte 1">
                    <span class="timePeriod">08:15 - 12:00 HOR-120</span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?ARGUMENTS=-N2" class="link" title="Sonntagskurs">T2</a>
                  </td>
                </tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val lectures = parser.parseWeeklyView(weekendWeek)

        assertEquals(2, lectures.size)
        assertEquals(8, lectures.single { it.shortSubjectName == "T1" }.startTime.day)
        assertEquals(9, lectures.single { it.shortSubjectName == "T2" }.startTime.day)
    }

    @Test
    fun parseWeeklyView_headerNotMatchingStrictPattern_fallsBackToLenientPattern() {
        // Any extra markup between the <th> and <a> (e.g. a screen-reader label) breaks the
        // strict header pattern; the lenient fallback must still resolve the date.
        val looseHeaderWeek = DualisFixtures.Timetable.WEEK_FULL.replace(
            Regex("""(<th class="weekday" abbr="\w+">)(<a)"""),
            "$1<span class=\"sr-only\">Woche</span>$2"
        )

        val lectures = parser.parseWeeklyView(looseHeaderWeek)

        assertEquals(3, lectures.size, "The lenient fallback pattern must still resolve every appointment's date")
        assertEquals(3, lectures[0].startTime.day)
    }

    // ── parseLectureCell link building ─────────────────────────────────────────

    @Test
    fun parseWeeklyView_keepsAbsoluteHttpLinkUnchanged() {
        val week = DualisFixtures.Timetable.WEEK_FULL.replace(
            """href="/scripts/mgrqispi.dll?APPNAME=CampusNet&amp;PRGNAME=COURSEPREP&amp;ARGUMENTS=-N1,-N2,-N3"""",
            """href="https://dualis.dhbw.de/scripts/mgrqispi.dll?ARGUMENTS=-N1""""
        )

        val monday = parser.parseWeeklyView(week).first()

        assertEquals("https://dualis.dhbw.de/scripts/mgrqispi.dll?ARGUMENTS=-N1", monday.linkToIndividualPage)
    }

    @Test
    fun parseWeeklyView_prefixesRelativeLinkWithoutLeadingSlash() {
        val week = DualisFixtures.Timetable.WEEK_FULL.replace(
            """href="/scripts/mgrqispi.dll?APPNAME=CampusNet&amp;PRGNAME=COURSEPREP&amp;ARGUMENTS=-N1,-N2,-N3"""",
            """href="scripts/mgrqispi.dll?ARGUMENTS=-N1""""
        )

        val monday = parser.parseWeeklyView(week).first()

        assertEquals("https://dualis.dhbw.de/scripts/mgrqispi.dll?ARGUMENTS=-N1", monday.linkToIndividualPage)
    }

    @Test
    fun parseWeeklyView_lenientFallback_alsoResolvesWeekendHeaders() {
        // The lenient fallback pattern re-implements the Mo..So mapping independently of the
        // strict one; a weekend day forces this test through its own Sa/So branches rather than
        // the strict pattern's.
        val looseWeekendHeaders = """
            <html><body>
            <table class="nb rw-table rw-all">
              <thead>
                <tr>
                  <th class="fixedTimeColumn">Zeit</th>
                  <th class="weekday" abbr="Samstag"><span class="sr-only">Woche</span><a href="#">Sa 08.11.</a></th>
                  <th class="weekday" abbr="Sonntag"><span class="sr-only">Woche</span><a href="#">So 09.11.</a></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="appointment" style="background-color:#FFFFFF;" abbr="Samstag Spalte 1">
                    <span class="timePeriod">08:15 - 12:00 HOR-120</span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?ARGUMENTS=-N1" class="link" title="Samstagskurs">T1</a>
                  </td>
                  <td class="appointment" style="background-color:#FFFFFF;" abbr="Sonntag Spalte 1">
                    <span class="timePeriod">08:15 - 12:00 HOR-120</span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?ARGUMENTS=-N2" class="link" title="Sonntagskurs">T2</a>
                  </td>
                </tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val lectures = parser.parseWeeklyView(looseWeekendHeaders)

        assertEquals(2, lectures.size)
        assertEquals(8, lectures.single { it.shortSubjectName == "T1" }.startTime.day)
        assertEquals(9, lectures.single { it.shortSubjectName == "T2" }.startTime.day)
    }

    @Test
    fun yearNearest_leapDayHeader_skipsNonLeapCandidateYears() {
        // "Mo 29.02." only exists in a leap year. With an anchor of 1 Mar 2027 (not itself a leap
        // year), the +/-1 neighbours 2026 and 2027 are not leap either - only 2028 is, so the
        // parser must skip the two invalid candidates via yearNearest's `continue` before finding it.
        val leapDayWeek = """
            <html><body>
            <table class="nb rw-table rw-all">
              <thead>
                <tr>
                  <th class="fixedTimeColumn">Zeit</th>
                  <th class="weekday" abbr="Montag"><a href="#">Mo 29.02.</a></th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="appointment" style="background-color:#FFFFFF;" abbr="Montag Spalte 1">
                    <span class="timePeriod">08:15 - 12:00 HOR-120</span>
                    <br />
                    <a href="/scripts/mgrqispi.dll?ARGUMENTS=-N1" class="link" title="Kurs">T1</a>
                  </td>
                </tr>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val lectures = parser.parseWeeklyView(leapDayWeek, weekStart = LocalDate(2027, 3, 1))

        assertEquals(1, lectures.size)
        assertEquals(2028, lectures.single().startTime.year, "2028 is the nearest year where 29 Feb exists")
    }

    @Test
    fun parseWeeklyView_cellWithoutATimePeriod_isDropped() {
        val week = DualisFixtures.Timetable.WEEK_FULL.replace(
            "08:15 - 12:00",
            "no time here",
        )

        val lectures = parser.parseWeeklyView(week)

        assertEquals(2, lectures.size, "the appointment with no parseable time must be skipped, not crash the parse")
    }

    @Test
    fun parseWeeklyView_cellWithoutALink_isDropped() {
        val week = DualisFixtures.Timetable.WEEK_FULL.replaceFirst(
            Regex("""<a\s+href="[^"]*"[^>]*title="[^"]*"[^>]*>\s*[^<]+\s*</a>"""),
            "",
        )

        val lectures = parser.parseWeeklyView(week)

        assertEquals(2, lectures.size, "the appointment with no <a> tag must be skipped, not crash the parse")
    }

    @Test
    fun parseWeeklyView_titleEqualToShortName_leavesFullSubjectNameNull() {
        val week = DualisFixtures.Timetable.WEEK_FULL.replace(
            """title="Paralleles Programmieren  HOR-TINF2024">""",
            """title="T4INF2904.1">""",
        )

        val monday = parser.parseWeeklyView(week).first()

        assertNull(monday.fullSubjectName, "when the title equals the short name there is nothing extra to keep")
    }

    @Test
    fun parseWeeklyView_appointmentWithAnOutOfRangeHour_isDroppedRatherThanCrashing() {
        // The time regex accepts two digits for the hour, but LocalDateTime rejects hour=99 -
        // this must be caught per-appointment rather than aborting the whole week.
        val week = DualisFixtures.Timetable.WEEK_FULL.replace("08:15 - 12:00", "99:15 - 12:00")

        val lectures = parser.parseWeeklyView(week)

        assertEquals(2, lectures.size, "only the malformed appointment is dropped")
    }

    // ── parseIndividualPage edge cases ─────────────────────────────────────────

    @Test
    fun parseIndividualPage_headingWithoutTheExpectedNbspFormat_returnsNull() {
        val html = "<html><body><h1>No nbsp here HOR-TINF2024</h1></body></html>"

        assertNull(parser.parseIndividualPage(html))
    }

    @Test
    fun parseIndividualPage_filtersOutStandardLinkUndefLecturers() {
        val html = DualisFixtures.Timetable.INDIVIDUAL_PAGE.replace(
            """<td class="tbdata" style="text-align:center;" name="instructorName">B.Sc. Julian Schmidt</td>""",
            """<td class="tbdata" style="text-align:center;" name="instructorName">standardLink undef</td>""",
        )

        val result = assertNotNull(parser.parseIndividualPage(html))

        assertTrue(
            result.second.none { it.contains("standardLink undef") },
            "a placeholder 'standardLink undef' cell must not be reported as a lecturer",
        )
    }
}
