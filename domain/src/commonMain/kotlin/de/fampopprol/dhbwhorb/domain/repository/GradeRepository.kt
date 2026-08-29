/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Semester

/**
 * Grades per semester, cached locally for an hour.
 */
interface GradeRepository {

    /** The semesters Dualis lists for this student, newest first as Dualis orders them. */
    suspend fun getSemesters(): Outcome<List<Semester>>

    /**
     * The grades of one semester.
     *
     * @param forceRefresh skip the local cache even if it is still considered fresh
     */
    suspend fun getGrades(semester: Semester, forceRefresh: Boolean = false): Outcome<List<GradeEntry>>
}
