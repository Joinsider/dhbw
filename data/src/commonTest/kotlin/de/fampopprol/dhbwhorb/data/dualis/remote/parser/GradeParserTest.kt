/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DualisFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for [GradeParser] against the HTML shapes documented in [DualisFixtures].
 * See the fixture KDoc on what these do and do not prove.
 */
class GradeParserTest {

    private val parser = GradeParser()

    private fun parseSemester(html: String) =
        parser.parseGrades(html, studentId = "s12345", semesterId = "000000015168000", semesterName = "WiSe 2025/26")

    // ── parseSemesterList ────────────────────────────────────────────────────

    @Test
    fun parseSemesterList_returnsNameToIdMapping() {
        val semesters = parser.parseSemesterList(DualisFixtures.Grades.SEMESTER_DROPDOWN)

        assertEquals(3, semesters.size)
        assertEquals("000000015168000", semesters["WiSe 2025/26"])
        assertEquals("000000015158000", semesters["SoSe 2025"])
        assertEquals("000000015148000", semesters["WiSe 2024/25"])
    }

    @Test
    fun parseSemesterList_keepsSelectedSemester() {
        // The selected option carries an extra attribute; it must not be skipped.
        val semesters = parser.parseSemesterList(DualisFixtures.Grades.SEMESTER_DROPDOWN)
        assertTrue(semesters.containsKey("WiSe 2025/26"), "selected='selected' must not break the match")
    }

    @Test
    fun parseSemesterList_onEmptyInput_returnsEmptyMap() {
        assertEquals(emptyMap(), parser.parseSemesterList(DualisFixtures.EMPTY))
    }

    @Test
    fun parseSemesterList_onSessionExpiredPage_returnsEmptyMap() {
        assertEquals(emptyMap(), parser.parseSemesterList(DualisFixtures.SESSION_EXPIRED))
    }

    // ── parseGrades ──────────────────────────────────────────────────────────

    @Test
    fun parseGrades_extractsAllModuleRows() {
        val grades = parseSemester(DualisFixtures.Grades.SEMESTER_TABLE)

        assertEquals(3, grades.size, "GPA subhead row must not be counted as a module")
        assertEquals(listOf("T3INF1001", "T3INF2002", "T4INF2904"), grades.map { it.moduleNumber })
    }

    @Test
    fun parseGrades_carriesSemesterAndStudentThrough() {
        val grades = parseSemester(DualisFixtures.Grades.SEMESTER_TABLE)

        assertTrue(grades.all { it.studentId == "s12345" })
        assertTrue(grades.all { it.semesterId == "000000015168000" })
        assertTrue(grades.all { it.semesterName == "WiSe 2025/26" })
    }

    @Test
    fun parseGrades_readsGradeCreditsAndStatus() {
        val math = parseSemester(DualisFixtures.Grades.SEMESTER_TABLE).first()

        assertEquals("Mathematik I", math.moduleName)
        assertEquals("1,7", math.grade)
        assertEquals(5.0, math.credits, "German decimal comma has to be converted")
        assertEquals("bestanden", math.status)
    }

    @Test
    fun parseGrades_ungradedModule_hasNullGradeAndZeroCredits() {
        val open = parseSemester(DualisFixtures.Grades.SEMESTER_TABLE)[1]

        assertNull(open.grade, "'noch nicht gesetzt' must become null, not a literal grade")
        assertEquals(0.0, open.credits, "&nbsp; in the credits column must not throw")
        assertEquals("offen", open.status)
    }

    @Test
    fun parseGrades_decimalCredits_areParsed() {
        val parallel = parseSemester(DualisFixtures.Grades.SEMESTER_TABLE)[2]
        assertEquals(7.5, parallel.credits)
    }

    @Test
    fun parseGrades_skipsGpaSubheadRow() {
        val grades = parseSemester(DualisFixtures.Grades.SEMESTER_TABLE)
        assertTrue(
            grades.none { it.moduleName.contains("GPA", ignoreCase = true) },
            "Semester-GPA is a summary row, not a module"
        )
    }

    // ── degradation ──────────────────────────────────────────────────────────

    @Test
    fun parseGrades_onEmptyTable_returnsEmptyList() {
        assertEquals(emptyList(), parseSemester(DualisFixtures.Grades.SEMESTER_TABLE_EMPTY))
    }

    @Test
    fun parseGrades_onEmptyInput_returnsEmptyList() {
        assertEquals(emptyList(), parseSemester(DualisFixtures.EMPTY))
    }

    @Test
    fun parseGrades_onSessionExpiredPage_returnsEmptyList() {
        assertEquals(
            emptyList(),
            parseSemester(DualisFixtures.SESSION_EXPIRED),
            "An expired session must not surface as grades"
        )
    }

    @Test
    fun parseGrades_onNonHtmlInput_returnsEmptyList() {
        assertEquals(emptyList(), parseSemester(DualisFixtures.NOT_HTML))
    }

    // ── the details link ─────────────────────────────────────────────────────

    /** A row as Dualis writes it, with the details pop-up on the module name. */
    private fun rowWithDetailsLink(resultId: String) = """
        <table><tr>
        <td class="tbdata">T4INF4211</td>
        <td class="tbdata">
            <a name="_$resultId" id="result_id_$resultId" href="#$resultId"
               onclick="javascript: dl_popUp('/scripts/mgrqispi.dll?APPNAME=CampusNet&amp;PRGNAME=RESULTDETAILS&amp;ARGUMENTS=-N507718714459690,-N000307,-N$resultId','Ergebnisdetails',800,600);">Compilerbau</a>
        </td>
        <td class="tbdata">1,0</td>
        <td class="tbdata">5,0</td>
        <td class="tbdata">bestanden</td>
        </tr></table>
    """.trimIndent()

    @Test
    fun parseGrades_readsTheResultIdNotTheSessionId() {
        // Both stand in the same URL as "-N…": ARGUMENTS is session, menu entry, result. Reading
        // the first one gave every module the same id, which pointed at no module at all.
        val grades = parseSemester(rowWithDetailsLink("396314694963893"))

        assertEquals("396314694963893", grades.single().resultId)
    }

    @Test
    fun parseGrades_hasNoResultIdWhenTheRowDoesNotLinkOne() {
        // A module with no result yet is plain text in Dualis, with nothing to open.
        val html = """
            <table><tr>
            <td class="tbdata">T4INF2006</td>
            <td class="tbdata">IT-Sicherheit</td>
            <td class="tbdata"></td>
            <td class="tbdata">5,0</td>
            <td class="tbdata">offen</td>
            </tr></table>
        """.trimIndent()

        assertNull(parseSemester(html).single().resultId)
    }
}
