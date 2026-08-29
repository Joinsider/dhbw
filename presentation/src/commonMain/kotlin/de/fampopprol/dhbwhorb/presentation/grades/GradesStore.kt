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
import de.fampopprol.dhbwhorb.domain.usecase.GetModuleDetails
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import de.fampopprol.dhbwhorb.presentation.store.SessionScopedStore
import kotlinx.coroutines.CoroutineScope

class GradesStore(
    private val getAllGrades: GetAllGrades,
    private val getModuleDetails: GetModuleDetails,
    private val computeGpa: ComputeGpa,
    private val sessionRepository: SessionRepository,
    scope: CoroutineScope
) : BaseStore<GradesState, GradesIntent, GradesMsg, GradesEffect>(
    initialState = GradesState(),
    scope = scope
), SessionScopedStore {

    /**
     * One grade load at a time — but opening a module is its own piece of work.
     *
     * Sharing the key would mean a tap on a module gets dropped while a pull-to-refresh is still
     * running, which is exactly when the user is most likely to tap something.
     */
    override fun dedupeKey(intent: GradesIntent): Any = when (intent) {
        is GradesIntent.ModuleOpened, GradesIntent.ModuleClosed -> "module-details"
        else -> "grades"
    }

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

            is GradesIntent.ModuleOpened -> openModule(intent.entry)

            GradesIntent.ModuleClosed -> emit(GradesMsg.DetailsDismissed)
        }
    }

    /**
     * Opens the details sheet and fetches what Dualis records behind the module.
     *
     * The sheet opens first and fills in afterwards: the rows the app already has are shown while
     * the page loads, so a slow network delays the breakdown, not the reaction to the tap. A row
     * without a result id has no page to fetch — the sheet then shows only what is known locally.
     */
    private suspend fun EffectScope<GradesMsg, GradesEffect>.openModule(entry: GradeEntry) {
        emit(GradesMsg.DetailsRequested(entry))

        val resultId = entry.resultId ?: return

        when (val result = getModuleDetails(resultId)) {
            is Outcome.Ok -> emit(GradesMsg.DetailsLoaded(result.value))
            is Outcome.Err -> emit(GradesMsg.DetailsFailed(result.error))
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
            earnedCredits = gpa.earnedCredits,
            completedModules = gpa.completedModules
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
        modulesCompleted = msg.completedModules,
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

    is GradesMsg.DetailsRequested -> state.copy(
        selectedModule = msg.entry,
        moduleDetails = null,
        detailsError = null,
        // Nothing to wait for when the row carries no id, so the sheet must not spin forever.
        isLoadingDetails = msg.entry.resultId != null
    )

    is GradesMsg.DetailsLoaded -> state.copy(
        moduleDetails = msg.details,
        detailsError = null,
        isLoadingDetails = false
    )

    is GradesMsg.DetailsFailed -> state.copy(
        detailsError = msg.error,
        isLoadingDetails = false
    )

    GradesMsg.DetailsDismissed -> state.copy(
        selectedModule = null,
        moduleDetails = null,
        detailsError = null,
        isLoadingDetails = false
    )
}
