/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradeEntryTest {

    private fun entry(grade: String?, status: String?, resultId: String? = null) = GradeEntry(
        semesterId = "1",
        semesterName = "SoSe 2025",
        moduleNumber = "T4INF2001",
        moduleName = "Mathematik",
        grade = grade,
        credits = 5.0,
        status = status,
        resultId = resultId
    )

    @Test
    fun numericGradeParsesTheGermanCommaFormat() {
        assertEquals(1.3, entry("1,3", null).numericGrade)
    }

    @Test
    fun numericGradeIsNullForNonNumericGrades() {
        assertNull(entry("b", null).numericGrade)
        assertNull(entry(null, null).numericGrade)
        assertNull(entry("n.b.", null).numericGrade)
    }

    @Test
    fun withAnOpenStatusTheGradeDecides() {
        // "offen" is neither a failed nor a passed marker, so isPassed falls through to the grade.
        assertTrue(entry("1,3", "offen").isPassed)
        assertFalse(entry("5,0", "offen").isPassed)
        assertFalse(entry(null, "offen").isPassed)
    }

    @Test
    fun aNumericGradeWorseThanFourFailsWithoutAStatus() {
        assertFalse(entry("4,3", null).isPassed)
        assertTrue(entry("4,0", null).isPassed)
    }

    @Test
    fun countsTowardDegreeRequiresBothAGradeAndAPass() {
        assertFalse(entry(null, "bestanden").countsTowardDegree)
        assertFalse(entry("5,0", "nicht bestanden").countsTowardDegree)
        assertTrue(entry("1,0", "bestanden").countsTowardDegree)
    }

    @Test
    fun resultIdIsCarriedThrough() {
        assertNull(entry("1,0", "bestanden").resultId)
        assertEquals("abc123", entry("1,0", "bestanden", resultId = "abc123").resultId)
    }
}
