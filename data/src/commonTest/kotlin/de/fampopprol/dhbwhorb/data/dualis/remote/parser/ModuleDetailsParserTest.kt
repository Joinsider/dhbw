/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.ModuleDetailsFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleDetailsParserTest {

    private val parser = ModuleDetailsParser()

    @Test
    fun theHeadingSplitsIntoNumberNameAndSemester() {
        val details = assertNotNull(parser.parse(ModuleDetailsFixtures.compilerbau))

        assertEquals("T4INF4211", details.moduleNumber)
        assertEquals("Compilerbau", details.moduleName)
        assertEquals("SoSe 2026", details.semesterName)
    }

    @Test
    fun aSingleExamModuleHasOneAttemptWithOneExam() {
        val details = assertNotNull(parser.parse(ModuleDetailsFixtures.compilerbau))

        assertEquals(1, details.attempts.size)
        val attempt = details.attempts.single()
        assertEquals(1, attempt.number)
        assertEquals("1,0 bestanden", attempt.result)

        val exam = attempt.exams.single()
        assertEquals("Klausur oder Kombinierte Prüfung", exam.name)
        assertEquals(100.0, exam.weightPercent)
        assertEquals("1,0", exam.grade)
        assertEquals("SoSe 2026", exam.semesterName)
        assertEquals("Modulabschlussleistungen", exam.unitName)
        assertNull(exam.date)
    }

    @Test
    fun theTwoGradesOfMathematikIIIAreBothReadWithTheirBausteine() {
        // The whole point of the page: the list says "3,2 bestanden (Wh.)" and this says which
        // two exams that is — a 3,6 in Angewandte Mathematik and a 2,8 in Statistik.
        val details = assertNotNull(parser.parse(ModuleDetailsFixtures.mathematikIII))

        assertEquals("T4INF2001", details.moduleNumber)
        assertEquals("Mathematik III", details.moduleName)
        assertEquals(2, details.attempts.size)

        val second = details.attempts[1]
        assertEquals(2, second.number)
        assertEquals("3,2 bestanden (Wh.)", second.result)
        assertEquals(
            listOf("3,6" to "T4INF2001.1 Angewandte Mathematik HOR-TINF2024", "2,8" to "T4INF2001.2 Statistik HOR-TINF2024"),
            second.exams.map { it.grade to it.unitName }
        )
        assertEquals(listOf("WiSe 2025/26", "SoSe 2026"), second.exams.map { it.semesterName })
    }

    @Test
    fun theFailedFirstAttemptStaysItsOwnAttempt() {
        // Attempt 1 held only the Angewandte-Mathematik exam, which is why Dualis calls it
        // unvollständig. Its 4,6 must not leak into attempt 2.
        val details = assertNotNull(parser.parse(ModuleDetailsFixtures.mathematikIII))

        val first = details.attempts[0]
        assertEquals(1, first.number)
        assertEquals("4,6 unvollständig", first.result)
        assertEquals(listOf("4,6"), first.exams.map { it.grade })
    }

    @Test
    fun theBausteineTableIsRead() {
        val details = assertNotNull(parser.parse(ModuleDetailsFixtures.mathematikIII))

        assertEquals(2, details.units.size)
        assertEquals("T4INF2001.1", details.units[0].number)
        assertEquals("Angewandte Mathematik", details.units[0].name)
        assertTrue(details.units[0].attended, "the tick is an image, not text")
        assertEquals("Statistik", details.units[1].name)
    }

    @Test
    fun theBausteineTableDoesNotLeakIntoTheAttempts() {
        val details = assertNotNull(parser.parse(ModuleDetailsFixtures.compilerbau))

        assertEquals(1, details.attempts.single().exams.size, "the two units are not exams")
    }

    @Test
    fun aPageWithoutAHeadingIsNotDetails() {
        assertNull(parser.parse("<html><body>Bitte melden Sie sich an</body></html>"))
    }

    // ── splitHeading edge cases ─────────────────────────────────────────────

    @Test
    fun aHeadingWithoutParenthesesHasNoSemester() {
        val html = """<h1>T1INF1000 Grundlagenmodul</h1>"""
        val details = assertNotNull(parser.parse(html))

        assertEquals("T1INF1000", details.moduleNumber)
        assertEquals("Grundlagenmodul", details.moduleName)
        assertNull(details.semesterName)
    }

    @Test
    fun aTrailingCloseParenWithoutAnOpenOneIsKeptAsPartOfTheName() {
        // A stray ")" with no matching "(" must not be misread as a semester marker.
        val html = """<h1>T1INF1000 Sonderfall)</h1>"""
        val details = assertNotNull(parser.parse(html))

        assertEquals("Sonderfall)", details.moduleName)
        assertNull(details.semesterName)
    }

    @Test
    fun aBlankParentheticalYieldsNoSemester() {
        val html = """<h1>T1INF1000 Modulname ( )</h1>"""
        val details = assertNotNull(parser.parse(html))

        assertNull(details.semesterName)
    }

    // ── Zugehörige Bausteine heading variants ───────────────────────────────

    @Test
    fun theHtmlEntitySpellingOfTheUnitsHeadingIsAlsoRecognised() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table><tr><td class="tbdata">a</td><td class="tbdata">Klausur</td></tr></table>
            <h2>Zugeh&ouml;rige Bausteine</h2>
            <table>
              <tr><td class="tbdata">T1INF1000.1</td><td class="tbdata">Baustein A</td><td class="tbdata">Event A</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(1, details.units.size)
        assertEquals("T1INF1000.1", details.units.single().number)
    }

    @Test
    fun withoutAUnitsHeading_everythingIsTreatedAsTheAttemptsTable() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertTrue(details.units.isEmpty())
        assertEquals(1, details.attempts.size)
    }

    // ── Row classification edge cases ───────────────────────────────────────

    @Test
    fun anExamRowBeforeAnyVersuchHeadingIsKeptRatherThanDropped() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(1, details.attempts.size)
        assertEquals(1, details.attempts.single().exams.size)
        assertNull(details.attempts.single().number, "no Versuch row was ever seen")
    }

    @Test
    fun anEmptyRowIsSkippedAsAHeaderRow() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(1, details.attempts.single().exams.size, "the empty row must not become a phantom attempt")
    }

    @Test
    fun aLevel01RowThatDoesNotMatchVersuchIsIgnored() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Sonstiges</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        // Falls through to the "kept before any Versuch" case, same as if the row weren't there.
        assertEquals(1, details.attempts.size)
        assertNull(details.attempts.single().number)
    }

    @Test
    fun aRowMatchingNoKnownShapeContributesNothing() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td>plain, unclassed cell</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(1, details.attempts.single().exams.size)
    }

    @Test
    fun aBlankUnitHeadingCellIsTreatedAsNoUnit() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="level02">&nbsp;</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertNull(details.attempts.single().exams.single().unitName)
    }

    @Test
    fun anExamRowWithOnlyOneCellIsIgnored() {
        // addExam requires at least a semester cell and a name cell; a row with just one tbdata
        // cell must be dropped rather than crash or produce a half-built ExamResult.
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="tbdata">OnlyOneCell</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(1, details.attempts.single().exams.size, "the single-cell row contributes nothing")
    }

    @Test
    fun aUnitRowWithFewerThanThreeCellsIsSkipped() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <h2>Zugeh&ouml;rige Bausteine</h2>
            <table>
              <tr><td class="tbdata">T1INF1000.1</td><td class="tbdata">Incomplete row</td></tr>
              <tr><td class="tbdata">T1INF1000.2</td><td class="tbdata">Baustein B</td><td class="tbdata">Event B</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(1, details.units.size, "the two-cell row must not become a unit")
        assertEquals("T1INF1000.2", details.units.single().number)
    }

    @Test
    fun aUnitWithoutThePassIconIsNotAttended() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <h2>Zugeh&ouml;rige Bausteine</h2>
            <table>
              <tr><td class="tbdata">T1INF1000.1</td><td class="tbdata">Baustein A</td><td class="tbdata">Vorlesung</td><td class="tbdata"></td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(false, details.units.single().attended)
    }

    @Test
    fun aGesamtRowWithASingleLevel02CellDoesNotCloseTheAttempt() {
        // level02.size == 1 is read as a unit heading, not a Gesamt row - even when its own text
        // happens to say "Gesamt".
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="level02">Gesamt</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(1, details.attempts.single().exams.size)
        assertNull(details.attempts.single().result, "a single-cell 'Gesamt' row is a unit heading, not a verdict")
    }

    @Test
    fun anExamWithoutAWeightPercentageHasANullWeight() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur ohne Gewichtung</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))
        val exam = details.attempts.single().exams.single()

        assertNull(exam.weightPercent)
        assertEquals("Klausur ohne Gewichtung", exam.name)
    }

    @Test
    fun aWeightPercentageWrittenWithAGermanCommaIsParsed() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur (33,5%)</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))
        val exam = details.attempts.single().exams.single()

        assertEquals(33.5, exam.weightPercent)
        assertEquals("Klausur", exam.name)
    }

    @Test
    fun anExamRowWithABlankSemesterCellHasANullSemesterName() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="tbdata"></td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertNull(details.attempts.single().exams.single().semesterName)
    }

    @Test
    fun anExamRowWithABlankGradeCellHasANullGrade() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td><td class="tbdata">01.03.2026</td><td class="tbdata"></td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))
        val exam = details.attempts.single().exams.single()

        assertEquals("01.03.2026", exam.date)
        assertNull(exam.grade)
    }

    @Test
    fun aUnitRowWithExactlyThreeCells_isNotAttended() {
        // No fourth (tick) cell at all, as opposed to an empty one.
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <h2>Zugeh&ouml;rige Bausteine</h2>
            <table>
              <tr><td class="tbdata">T1INF1000.1</td><td class="tbdata">Baustein A</td><td class="tbdata">Vorlesung</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(false, details.units.single().attended)
    }

    // ── Additional edge cases ────────────────────────────────────────────────

    @Test
    fun aBlankHeadingIsNotDetails() {
        // Present but empty h1 content: heading is non-null but blank, a different code path
        // than the h1 tag missing entirely.
        assertNull(parser.parse("<h1>   </h1>"))
    }

    @Test
    fun aHeadingThatIsEntirelyParentheticalKeepsTheParenthesesInTheNameAndHasNoSemester() {
        // The open paren sits at index 0 of "rest", so `open > 0` is false and no split happens.
        val html = """<h1>T1INF1000 (OnlyParen)</h1>"""
        val details = assertNotNull(parser.parse(html))

        assertEquals("(OnlyParen)", details.moduleName)
        assertNull(details.semesterName)
    }

    @Test
    fun anEmptyAttemptsTableProducesNoAttempts() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table></table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertTrue(details.attempts.isEmpty())
    }

    @Test
    fun aVersuchNumberThatOverflowsIntIsKeptAsNull() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  99999999999999999999</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertNull(
            details.attempts.single().number,
            "an out-of-range attempt number degrades to null rather than crashing"
        )
    }

    @Test
    fun multipleLevel02CellsWithoutGesamtTextDoNotCloseTheAttempt() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="level02">Foo</td><td class="level02">Bar</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertEquals(1, details.attempts.single().exams.size)
        assertNull(details.attempts.single().result, "level02.size > 1 without 'Gesamt' text is not a verdict row")
    }

    @Test
    fun aGesamtRowWhereEveryLevel02CellIsBlankOrGesamtYieldsANullVerdict() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td></tr>
              <tr><td class="level02">Gesamt</td><td class="level02">&nbsp;</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertNull(details.attempts.single().result, "no non-blank, non-Gesamt cell means no readable verdict")
    }

    @Test
    fun anExamRowWithExactlyThreeCellsHasNoGradeCellAtAll() {
        // As opposed to a present-but-blank grade cell: here there is no fourth cell at all.
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <table>
              <tr><td class="level01">Versuch  1</td></tr>
              <tr><td class="tbdata">WiSe 2025/26</td><td class="tbdata">Klausur</td><td class="tbdata">01.03.2026</td></tr>
            </table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))
        val exam = details.attempts.single().exams.single()

        assertEquals("01.03.2026", exam.date)
        assertNull(exam.grade)
    }

    @Test
    fun aUnitsTableWithNoRowsProducesNoUnits() {
        val html = """
            <h1>T1INF1000 Grundlagenmodul</h1>
            <h2>Zugeh&ouml;rige Bausteine</h2>
            <table></table>
        """.trimIndent()

        val details = assertNotNull(parser.parse(html))

        assertTrue(details.units.isEmpty())
    }
}
