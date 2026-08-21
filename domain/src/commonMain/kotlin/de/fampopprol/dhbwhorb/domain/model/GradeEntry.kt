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
    val status: String?
) {
    /** The grade as a number, or null when it is not numeric ("b", "n.b.", not graded yet). */
    val numericGrade: Double? get() = grade?.replace(',', '.')?.toDoubleOrNull()
}
