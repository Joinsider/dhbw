/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.grades

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.SemesterOrder
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import kotlinx.coroutines.CoroutineScope

class GradesStore(
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
            GradesIntent.Load -> load(isRefresh = false)

            // Re-entering the screen must not refetch what the store already holds.
            GradesIntent.EnsureLoaded -> if (!state.hasLoaded && state.error == null) {
                load(isRefresh = false)
            }

            GradesIntent.Refresh -> load(isRefresh = true)
        }
    }

    private suspend fun EffectScope<GradesMsg, GradesEffect>.load(isRefresh: Boolean) {
        emit(GradesMsg.LoadStarted(isRefresh))

        if (!sessionRepository.canAuthenticate()) {
            emit(GradesMsg.LoginRequired)
            emit(GradesMsg.LoadFinished)
            return
        }

        when (val result = getAllGrades(forceRefresh = isRefresh)) {
            is Outcome.Ok -> emit(gradesLoaded(result.value))
            is Outcome.Err -> {
                emit(GradesMsg.Failed(result.error))
                if (isRefresh) send(GradesEffect.RefreshFailed(result.error))
            }
        }
        emit(GradesMsg.LoadFinished)
    }

    private fun gradesLoaded(grades: List<GradeEntry>): GradesMsg.GradesLoaded {
        // Sorted here, once, so that both UIs and the section grouping in GradesState agree on
        // what "in order" means. By semester name it would read WiSe 2024/25, WiSe 2025/26,
        // SoSe 2025 — alphabetical, and not the order anybody studied them in.
        val ordered = grades.sortedWith { a, b ->
            val bySemester = SemesterOrder.oldestFirst.compare(a.semesterName, b.semesterName)
            if (bySemester != 0) bySemester else a.moduleName.compareTo(b.moduleName)
        }
        val gpa = computeGpa(ordered)
        return GradesMsg.GradesLoaded(
            grades = ordered,
            average = gpa.average,
            earnedCredits = gpa.earnedCredits
        )
    }
}

/**
 * The grades state after [msg].
 *
 * Top-level and therefore unable to reach a store, a repository or a scope: the reducer's purity
 * is structural rather than a promise. Its tests call it directly, with no coroutines involved.
 */
fun reduceGrades(state: GradesState, msg: GradesMsg): GradesState = when (msg) {
    is GradesMsg.LoadStarted -> state.copy(
        isLoading = !msg.isRefresh,
        isRefreshing = msg.isRefresh,
        error = null,
        requiresLogin = false
    )

    is GradesMsg.GradesLoaded -> state.copy(
        grades = msg.grades,
        overallGpa = msg.average,
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

    GradesMsg.LoadFinished -> state.copy(isLoading = false, isRefreshing = false)
}
