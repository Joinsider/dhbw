/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradeAttemptsTest {

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

    @Test
    fun aRepeatedModuleCountsOnceWithTheAttemptThatPassed() {
        // Straight from a real transcript: Mathematik III was left unfinished in the winter term
        // and passed in the summer. Dualis files the two attempts under their own semesters, so
        // both arrive here.
        val failed = entry("T4INF2001", "4,6", 6.0, "WiSe 2025/26", "unvollständig")
        val passed = entry("T4INF2001", "3,2", 6.0, "SoSe 2026", "bestanden (Wh.)")

        val counted = GradeAttempts.countable(listOf(failed, passed))

        assertEquals(listOf(passed), counted)
    }

    @Test
    fun anAttemptThatWasNotPassedNeverCounts() {
        val failed = entry("A", "5,0", status = "nicht bestanden")

        assertFalse(failed.isPassed)
        assertEquals(emptyList(), GradeAttempts.countable(listOf(failed)))
    }

    @Test
    fun theStatusDecidesEvenWhenItContradictsTheGrade() {
        // "nicht bestanden" contains "bestanden"; reading the status carelessly passes a 5,0.
        assertFalse(entry("A", "5,0", status = "nicht bestanden").isPassed)
        assertTrue(entry("B", "3,2", status = "bestanden (Wh.)").isPassed)
        assertFalse(entry("C", "4,6", status = "unvollständig").isPassed)
    }

    @Test
    fun withoutAStatusTheGradeDecides() {
        assertTrue(entry("A", "4,0", status = null).isPassed)
        assertFalse(entry("B", "4,1", status = "").isPassed)
    }

    @Test
    fun aPassWithoutAGradeIsNotCountedYet() {
        // "bestanden" and an empty grade column is a module Dualis has not finished booking.
        assertEquals(emptyList(), GradeAttempts.countable(listOf(entry("A", null))))
        // "b" — bestanden, ungraded — is a finished module and does count.
        assertEquals(1, GradeAttempts.countable(listOf(entry("A", "b"))).size)
    }

    @Test
    fun aModulePassedTwiceKeepsTheNewerResult() {
        val first = entry("A", "2,7", semester = "WiSe 2024/25")
        val improved = entry("A", "1,7", semester = "SoSe 2025")

        assertEquals(listOf(improved), GradeAttempts.countable(listOf(first, improved)))
    }

    @Test
    fun differentModulesAreNeverMerged() {
        val a = entry("A", "1,0")
        val b = entry("B", "2,0")

        assertEquals(listOf(a, b), GradeAttempts.countable(listOf(a, b)))
    }

    @Test
    fun theInputOrderSurvives() {
        val newer = entry("A", "1,0", semester = "SoSe 2026")
        val older = entry("B", "2,0", semester = "WiSe 2024/25")

        assertEquals(listOf(newer, older), GradeAttempts.countable(listOf(newer, older)))
    }

    @Test
    fun aTieInsideOneSemesterGoesToTheBetterGrade() {
        val worse = entry("A", "2,7", semester = "SoSe 2025")
        val better = entry("A", "1,7", semester = "SoSe 2025")

        assertEquals(listOf(better), GradeAttempts.countable(listOf(worse, better)))
        // Order in the input must not matter.
        assertEquals(listOf(better), GradeAttempts.countable(listOf(better, worse)))
    }

    @Test
    fun anUngradedPassLosesToAGradedAttemptInTheSameSemester() {
        val ungraded = entry("A", "b", semester = "SoSe 2025")
        val graded = entry("A", "2,0", semester = "SoSe 2025")

        assertEquals(listOf(graded), GradeAttempts.countable(listOf(ungraded, graded)))
    }

    @Test
    fun whenEverythingTiesTheLaterRowWins() {
        val first = entry("A", "2,0", credits = 5.0)
        val duplicate = entry("A", "2,0", credits = 5.0)

        assertEquals(listOf(duplicate), GradeAttempts.countable(listOf(first, duplicate)))
    }

    @Test
    fun anAttemptWithAnUnreadableSemesterLosesToAReadableOne() {
        val unreadable = entry("A", "1,0", semester = "Unbekannt")
        val readable = entry("A", "3,0", semester = "SoSe 2025")

        assertEquals(listOf(readable), GradeAttempts.countable(listOf(unreadable, readable)))
    }
}
