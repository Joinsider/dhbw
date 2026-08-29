/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputeGpaTest {

    private val computeGpa = ComputeGpa()

    private fun entry(
        module: String,
        grade: String?,
        credits: Double = 5.0,
        semester: String = "WiSe 2025/26",
        status: String? = "bestanden"
    ) = GradeEntry(
        semesterId = semester,
        semesterName = semester,
        moduleNumber = module,
        moduleName = module,
        grade = grade,
        credits = credits,
        status = status
    )

    private fun assertClose(expected: Double, actual: Double?) {
        assertTrue(actual != null && abs(expected - actual) < 1e-9, "expected $expected, was $actual")
    }

    @Test
    fun theAverageIsWeightedByCredits() {
        val gpa = computeGpa(listOf(entry("A", "1,0", 10.0), entry("B", "3,0", 5.0)))

        assertClose(1.6666666666666667, gpa.average)
        assertEquals(15.0, gpa.earnedCredits)
        assertEquals(2, gpa.completedModules)
    }

    @Test
    fun aFailedAttemptDragsNothingDown() {
        // The transcript that started this: the winter attempt at Mathematik III was left
        // unfinished, the summer one passed. Counting both put the average at 2,75 where Dualis
        // showed 2,6, and credited 6 credits twice.
        val grades = listOf(
            entry("T4INF2001", "4,6", 6.0, "WiSe 2025/26", "unvollständig"),
            entry("T4INF2001", "3,2", 6.0, "SoSe 2026", "bestanden (Wh.)"),
            entry("T4INF2002", "2,6", 6.0, "WiSe 2025/26")
        )

        val gpa = computeGpa(grades)

        assertClose(2.9, gpa.average)
        assertEquals(12.0, gpa.earnedCredits, "the repeated module's credits count once")
        assertEquals(2, gpa.completedModules)
    }

    @Test
    fun anUngradedPassKeepsItsCreditsButStaysOutOfTheAverage() {
        // Praxisprojekt I: 20 credits, "b" for bestanden, no number to average.
        val gpa = computeGpa(listOf(entry("A", "2,0", 5.0), entry("P", "b", 20.0)))

        assertClose(2.0, gpa.average)
        assertEquals(25.0, gpa.earnedCredits)
        assertEquals(2, gpa.completedModules)
    }

    @Test
    fun anOpenModuleCountsForNothing() {
        val gpa = computeGpa(listOf(entry("A", null, 5.0, status = null)))

        assertNull(gpa.average)
        assertEquals(0.0, gpa.earnedCredits)
        assertEquals(0, gpa.completedModules)
    }

    @Test
    fun withoutAnythingCountableThereIsNoAverageRatherThanZero() {
        assertNull(computeGpa(emptyList()).average)
    }

    @Test
    fun aModuleWithoutCreditsDoesNotDivideByZero() {
        val gpa = computeGpa(listOf(entry("A", "1,0", 0.0)))

        assertNull(gpa.average)
        assertEquals(0.0, gpa.earnedCredits)
    }
}
