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
}
