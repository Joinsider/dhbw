/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.grades

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades
import de.fampopprol.dhbwhorb.domain.usecase.GetGradesForSemester
import de.fampopprol.dhbwhorb.domain.usecase.GetSemesters
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import kotlinx.coroutines.CoroutineScope

class GradesStore(
    private val getSemesters: GetSemesters,
    private val getGradesForSemester: GetGradesForSemester,
    private val getAllGrades: GetAllGrades,
    private val computeGpa: ComputeGpa,
    private val sessionRepository: SessionRepository,
    scope: CoroutineScope
) : BaseStore<GradesState, GradesIntent, GradesMsg, GradesEffect>(
    initialState = GradesState(),
    scope = scope
) {

    /** Every intent loads grades, so only one of them may be in flight. */
    override fun dedupeKey(intent: GradesIntent): Any = "grades"

    override fun reduce(state: GradesState, msg: GradesMsg): GradesState = reduceGrades(state, msg)

    override suspend fun EffectScope<GradesMsg, GradesEffect>.handle(
        intent: GradesIntent,
        state: GradesState
    ) {
        when (intent) {
            GradesIntent.Load -> load()

            // Re-entering the screen must not refetch what the store already holds.
            GradesIntent.EnsureLoaded -> if (!state.hasLoaded && state.error == null) load()

            is GradesIntent.SemesterSelected -> {
                emit(GradesMsg.SemesterSelected(intent.semester))
                loadGrades(intent.semester, isRefresh = false)
            }

            GradesIntent.Refresh -> {
                val semester = state.selectedSemester
                if (semester == null) {
                    // Nothing selected yet means the first load never finished; start it over
                    // rather than refreshing nothing.
                    load()
                } else {
                    loadGrades(semester, isRefresh = true)
                }
            }
        }
    }

    private suspend fun EffectScope<GradesMsg, GradesEffect>.load() {
        emit(GradesMsg.LoadingSemesters)
        if (requireLogin()) return

        when (val result = getSemesters()) {
            is Outcome.Err -> {
                emit(GradesMsg.Failed(result.error))
                emit(GradesMsg.LoadFinished)
            }
            is Outcome.Ok -> {
                emit(GradesMsg.SemestersLoaded(result.value))
                emit(GradesMsg.SemesterSelected(Semester.All))
                loadGrades(Semester.All, isRefresh = false)
            }
        }
    }

    private suspend fun EffectScope<GradesMsg, GradesEffect>.loadGrades(
        semester: Semester,
        isRefresh: Boolean
    ) {
        emit(GradesMsg.LoadStarted(isRefresh))
        if (requireLogin()) return

        val forAll = Semester.isAll(semester)
        val result = if (forAll) {
            getAllGrades(forceRefresh = isRefresh)
        } else {
            getGradesForSemester(semester, forceRefresh = isRefresh)
        }

        when (result) {
            is Outcome.Ok -> emit(gradesLoaded(result.value, forAll))
            is Outcome.Err -> {
                emit(GradesMsg.Failed(result.error))
                if (isRefresh) send(GradesEffect.RefreshFailed(result.error))
            }
        }
        emit(GradesMsg.LoadFinished)
    }

    private fun gradesLoaded(grades: List<GradeEntry>, forAll: Boolean): GradesMsg.GradesLoaded {
        val ordered = if (forAll) {
            grades.sortedWith(
                compareByDescending<GradeEntry> { it.semesterName }.thenBy { it.moduleName }
            )
        } else {
            grades
        }
        val gpa = computeGpa(ordered)
        return GradesMsg.GradesLoaded(
            grades = ordered,
            average = gpa.average,
            earnedCredits = gpa.earnedCredits,
            forAllSemesters = forAll
        )
    }

    /** @return true when the caller should stop because there is nothing to authenticate with. */
    private fun EffectScope<GradesMsg, GradesEffect>.requireLogin(): Boolean {
        if (sessionRepository.canAuthenticate()) return false
        emit(GradesMsg.LoginRequired)
        emit(GradesMsg.LoadFinished)
        return true
    }
}

/**
 * The grades state after [msg].
 *
 * Top-level and therefore unable to reach a store, a repository or a scope: the reducer's purity
 * is structural rather than a promise. Its tests call it directly, with no coroutines involved.
 */
fun reduceGrades(state: GradesState, msg: GradesMsg): GradesState = when (msg) {
    GradesMsg.LoadingSemesters -> state.copy(
        isLoadingSemesters = true,
        isLoading = true,
        error = null,
        requiresLogin = false
    )

    is GradesMsg.SemestersLoaded -> state.copy(
        semesters = msg.semesters,
        isLoadingSemesters = false
    )

    is GradesMsg.LoadStarted -> state.copy(
        isLoading = !msg.isRefresh,
        isRefreshing = msg.isRefresh,
        error = null,
        requiresLogin = false
    )

    is GradesMsg.SemesterSelected -> state.copy(selectedSemester = msg.semester)

    is GradesMsg.GradesLoaded -> state.copy(
        grades = msg.grades,
        overallGpa = if (msg.forAllSemesters) msg.average else null,
        semesterGpa = if (msg.forAllSemesters) null else msg.average,
        totalCreditsEarned = msg.earnedCredits,
        error = null,
        hasLoaded = true
    )

    is GradesMsg.Failed -> state.copy(
        error = msg.error,
        // Credentials that are gone or no longer accepted mean the login screen, not a retry
        // button that could not work.
        requiresLogin = msg.error is AppError.NoCredentials ||
            msg.error is AppError.InvalidCredentials
    )

    GradesMsg.LoginRequired -> state.copy(requiresLogin = true, error = null)

    GradesMsg.LoadFinished -> state.copy(
        isLoading = false,
        isRefreshing = false,
        isLoadingSemesters = false
    )
}
