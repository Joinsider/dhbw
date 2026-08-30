/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * These are plain data classes with no behaviour of their own — what needs exercising is the
 * generated `equals`/`copy`/`toString`, which nothing else in the codebase happens to touch for
 * [ModuleAttempt], [ExamResult] and [ModuleUnit] (only [ModuleResultDetails] itself gets built,
 * always with empty `attempts`/`units`, elsewhere).
 */
class ModuleResultDetailsTest {

    private fun exam(name: String = "Klausur") = ExamResult(
        unitName = "T4INF2001.1 Angewandte Mathematik",
        semesterName = "SoSe 2026",
        name = name,
        weightPercent = 100.0,
        date = "12.02.2026",
        grade = "1,3",
    )

    private fun attempt(number: Int? = 1) = ModuleAttempt(
        number = number,
        exams = listOf(exam()),
        result = "1,3 bestanden",
    )

    private fun unit() = ModuleUnit(
        number = "T4INF2001.1",
        name = "Angewandte Mathematik",
        event = "Vorlesung",
        attended = true,
    )

    @Test
    fun moduleResultDetails_equalsAndCopy_compareByValue() {
        val details = ModuleResultDetails(
            moduleNumber = "T4INF2001",
            moduleName = "Mathematik III",
            semesterName = "SoSe 2026",
            attempts = listOf(attempt()),
            units = listOf(unit()),
        )
        val same = details.copy()
        val different = details.copy(moduleName = "Statistik")

        assertEquals(details, same)
        assertNotEquals(details, different)
        assertEquals(details.hashCode(), same.hashCode())
        assertTrue(details.toString().contains("Mathematik III"))
    }

    @Test
    fun moduleAttempt_equalsAndCopy_compareByValue() {
        val a = attempt(number = 1)
        val same = a.copy()
        val different = a.copy(number = 2)

        assertEquals(a, same)
        assertNotEquals(a, different)
        assertEquals(a.hashCode(), same.hashCode())
        assertTrue(a.toString().contains("bestanden"))
    }

    @Test
    fun moduleAttempt_nullNumber_isItsOwnValue() {
        val unreadable = attempt(number = null)

        assertEquals(null, unreadable.number)
        assertNotEquals(unreadable, attempt(number = 1))
    }

    @Test
    fun examResult_equalsAndCopy_compareByValue() {
        val e = exam("Klausur")
        val same = e.copy()
        val different = e.copy(grade = "5,0")

        assertEquals(e, same)
        assertNotEquals(e, different)
        assertEquals(e.hashCode(), same.hashCode())
        assertTrue(e.toString().contains("Klausur"))
    }

    @Test
    fun examResult_withoutWeight_isStillValid() {
        val unweighted = exam().copy(weightPercent = null)

        assertEquals(null, unweighted.weightPercent)
    }

    @Test
    fun moduleUnit_equalsAndCopy_compareByValue() {
        val u = unit()
        val same = u.copy()
        val different = u.copy(attended = false)

        assertEquals(u, same)
        assertNotEquals(u, different)
        assertEquals(u.hashCode(), same.hashCode())
        assertTrue(u.toString().contains("Angewandte Mathematik"))
    }
}
