/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

/**
 * One module's result in one semester.
 *
 * [grade] is the raw Dualis string ("1,3", "b" for bestanden, …) and is null while the module is
 * still running — a graded module with no grade yet is a normal state, not an error.
 */
data class GradeEntry(
    val semesterId: String,
    val semesterName: String,
    val moduleNumber: String,
    val moduleName: String,
    val grade: String?,
    val credits: Double,
    val status: String?,
    /**
     * Id of the module's details page in Dualis, when its row links one.
     *
     * Null means the details cannot be looked up for this entry — the screen then offers no way
     * in rather than opening an empty sheet.
     */
    val resultId: String? = null
) {
    /** The grade as a number, or null when it is not numeric ("b", "n.b.", not graded yet). */
    val numericGrade: Double? get() = grade?.replace(',', '.')?.toDoubleOrNull()

    /**
     * Whether this attempt was passed.
     *
     * Dualis states it in the status column ("bestanden", "bestanden (Wh.)" for a repeat,
     * "nicht bestanden", "unvollständig", "offen"), and the status is what decides — a repeat
     * that finally passed carries the same 4,0-or-better grade as a first attempt, so the number
     * alone cannot tell the two apart. Note that "nicht bestanden" contains "bestanden", which is
     * why the failure cases are asked first. Without a usable status the grade decides, with 4,0
     * the worst passing grade in the German scale.
     */
    val isPassed: Boolean
        get() {
            val state = status?.lowercase()?.trim()
            return when {
                state.isNullOrBlank() -> passesByGrade()
                FAILED_MARKERS.any { state.contains(it) } -> false
                PASSED_MARKERS.any { state.contains(it) } -> true
                else -> passesByGrade()
            }
        }

    /**
     * Whether this attempt counts towards credits and the average.
     *
     * A passed module still needs a grade: "bestanden" without any entry in the grade column is a
     * module Dualis has not finished booking, and counting its credits would promise the student
     * something the transcript does not yet show.
     */
    val countsTowardDegree: Boolean get() = grade != null && isPassed

    private fun passesByGrade(): Boolean = numericGrade?.let { it <= WORST_PASSING_GRADE } ?: false

    private companion object {
        const val WORST_PASSING_GRADE = 4.0

        /** Checked before [PASSED_MARKERS]: "nicht bestanden" also contains "bestanden". */
        val FAILED_MARKERS = listOf("nicht bestanden", "unvollständig", "unvollstaendig", "failed", "incomplete")
        val PASSED_MARKERS = listOf("bestanden", "passed")
    }
}
