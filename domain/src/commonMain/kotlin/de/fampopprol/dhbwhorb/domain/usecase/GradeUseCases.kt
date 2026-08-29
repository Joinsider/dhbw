/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.GradeAttempts
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.repository.GradeRepository

/** The semesters this student has results for. */
class GetSemesters(private val repository: GradeRepository) {
    suspend operator fun invoke(): Outcome<List<Semester>> = repository.getSemesters()
}

/** The grades of a single semester. */
class GetGradesForSemester(private val repository: GradeRepository) {
    suspend operator fun invoke(
        semester: Semester,
        forceRefresh: Boolean = false
    ): Outcome<List<GradeEntry>> = repository.getGrades(semester, forceRefresh)
}

/**
 * The grades of every semester, for the combined view.
 *
 * Fails only when *no* semester could be loaded: with one semester unreachable the overall
 * average would be wrong, but showing nothing at all is worse than showing what did arrive, so
 * partial results win as long as there is at least one.
 */
class GetAllGrades(
    private val getSemesters: GetSemesters,
    private val getGradesForSemester: GetGradesForSemester
) {
    suspend operator fun invoke(forceRefresh: Boolean = false): Outcome<List<GradeEntry>> {
        val semesters = when (val outcome = getSemesters()) {
            is Outcome.Ok -> outcome.value
            is Outcome.Err -> return outcome
        }
        if (semesters.isEmpty()) return Outcome.Ok(emptyList())

        val collected = mutableListOf<GradeEntry>()
        var lastError: AppError? = null
        var loadedAny = false

        for (semester in semesters) {
            when (val outcome = getGradesForSemester(semester, forceRefresh)) {
                is Outcome.Ok -> {
                    collected += outcome.value
                    loadedAny = true
                }
                is Outcome.Err -> lastError = outcome.error
            }
        }

        return if (loadedAny) Outcome.Ok(collected) else Outcome.Err(lastError ?: AppError.Unexpected("No semester could be loaded"))
    }
}

/**
 * Credit-weighted grade average over [grades].
 *
 * Pure and synchronous — no repository, no coroutine — so it is testable on its own. Only the
 * attempt that counts per module takes part (see [GradeAttempts]), so a repeated module is
 * weighed once and a failed attempt not at all; modules without a numeric grade or without
 * credits stay out of the average but keep their credits. The average is null when nothing
 * countable is left, which means "no average yet", not "an error occurred".
 */
class ComputeGpa {
    operator fun invoke(grades: List<GradeEntry>): Gpa {
        val counted = GradeAttempts.countable(grades)

        var weightedPoints = 0.0
        var gradedCredits = 0.0

        for (grade in counted) {
            val value = grade.numericGrade ?: continue
            if (grade.credits <= 0.0) continue
            weightedPoints += value * grade.credits
            gradedCredits += grade.credits
        }

        return Gpa(
            average = if (gradedCredits > 0.0) weightedPoints / gradedCredits else null,
            earnedCredits = counted.sumOf { it.credits },
            completedModules = counted.size
        )
    }
}

/**
 * @param average null when no module carries both a numeric grade and credits.
 * @param earnedCredits the credits of every passed module, graded ("2,3") or not ("b").
 * @param completedModules how many modules those credits came from.
 */
data class Gpa(
    val average: Double?,
    val earnedCredits: Double,
    val completedModules: Int
)
