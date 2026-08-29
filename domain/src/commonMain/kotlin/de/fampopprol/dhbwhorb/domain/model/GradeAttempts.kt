/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

/**
 * Which attempt of a module counts.
 *
 * Dualis' Prüfungsergebnisse list one row per *attempt*, filed under the semester the exam took
 * place in, so a module that was failed and later repeated appears twice: once with the failed
 * grade and once with the one that counts. Summing the rows as they come therefore counts those
 * credits twice and drags the average towards a grade the transcript no longer holds against the
 * student — the Leistungsübersicht shows only the final result per module.
 */
object GradeAttempts {

    /**
     * One entry per module: the attempt that counts towards credits and the average.
     *
     * Attempts that were not passed drop out entirely, and of what is left the most recent one
     * per module wins — a module passed twice (a grade improvement) counts once, with the newer
     * result, and ties inside one semester go to the better grade. Input order is preserved so
     * callers can keep whatever ordering they arrived with.
     */
    fun countable(grades: List<GradeEntry>): List<GradeEntry> {
        val counted = grades.withIndex()
            .filter { it.value.countsTowardDegree }
            .groupBy { it.value.moduleNumber }
            .values
            .map { attempts -> attempts.maxWith(weakestFirst).index }
            .toSet()

        return grades.filterIndexed { index, _ -> index in counted }
    }

    /** Greatest = the attempt to keep: newest semester, then better grade, then later row. */
    private val weakestFirst: Comparator<IndexedValue<GradeEntry>> =
        compareBy<IndexedValue<GradeEntry>> { SemesterOrder.sortKey(it.value.semesterName) ?: Int.MIN_VALUE }
            // Lower is better in the German scale, so "greater" here means the smaller number.
            // An ungraded pass ("b") sorts last and only wins when it is the sole attempt.
            .thenByDescending { it.value.numericGrade ?: Double.MAX_VALUE }
            .thenBy { it.index }
}
