/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.core.error.map
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisGradeService
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.ModuleResultDetails
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.repository.GradeRepository

/** [GradeRepository] on top of [DualisGradeService], which owns fetching and the one-hour cache. */
class GradeRepositoryImpl(
    private val gradeService: DualisGradeService
) : GradeRepository {

    override suspend fun getSemesters(): Outcome<List<Semester>> = gradeService.getSemesters()

    override suspend fun getGrades(semester: Semester, forceRefresh: Boolean): Outcome<List<GradeEntry>> =
        gradeService.getGradesForSemester(semester, forceRefresh).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getModuleDetails(resultId: String): Outcome<ModuleResultDetails> =
        gradeService.getModuleDetails(resultId)
}
