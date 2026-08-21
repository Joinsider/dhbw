/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.grades.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.usecase.ComputeGpa
import de.fampopprol.dhbwhorb.domain.usecase.GetAllGrades
import de.fampopprol.dhbwhorb.domain.usecase.GetGradesForSemester
import de.fampopprol.dhbwhorb.domain.usecase.GetSemesters
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class GradesUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingSemesters: Boolean = false,
    val semesters: List<Semester> = emptyList(),
    val selectedSemester: Semester? = null,
    val grades: List<GradeEntry> = emptyList(),
    val semesterGpa: Double? = null,
    /** Average across every semester; set only while the combined view is selected. */
    val overallGpa: Double? = null,
    val totalCreditsEarned: Double = 0.0,
    /** The classified reason the grades could not be loaded, for the UI to phrase. */
    val error: AppError? = null,
    val requiresLogin: Boolean = false
)

class GradesViewModel(
    private val getSemesters: GetSemesters,
    private val getGradesForSemester: GetGradesForSemester,
    private val getAllGrades: GetAllGrades,
    private val computeGpa: ComputeGpa,
    private val sessionRepository: SessionRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "GradesViewModel"
    }

    var uiState by mutableStateOf(GradesUiState())
        private set

    init {
        loadSemesters()
    }

    fun cleanup() {
        Napier.d("Cleaning up GradesViewModel", tag = TAG)
        coroutineScope.cancel()
    }

    fun loadSemesters() {
        uiState = uiState.copy(
            isLoadingSemesters = true,
            isLoading = true,
            error = null,
            requiresLogin = false
        )

        coroutineScope.launch {
            if (requireLogin()) return@launch

            when (val result = getSemesters()) {
                is Outcome.Ok -> {
                    Napier.d("Loaded ${result.value.size} semesters", tag = TAG)
                    uiState = uiState.copy(
                        semesters = result.value,
                        selectedSemester = Semester.All,
                        isLoadingSemesters = false,
                        isLoading = true // loadAllGrades clears it
                    )
                    loadAllGrades(forceRefresh = false)
                }
                is Outcome.Err -> {
                    Napier.e("Failed to load semesters: ${result.error}", tag = TAG)
                    uiState = uiState.copy(
                        isLoadingSemesters = false,
                        isLoading = false,
                        error = result.error
                    )
                }
            }
        }
    }

    fun selectSemester(semester: Semester) {
        uiState = uiState.copy(selectedSemester = semester)

        if (Semester.isAll(semester)) {
            coroutineScope.launch { loadAllGrades(forceRefresh = false) }
        } else {
            coroutineScope.launch { loadSemester(semester, isRefresh = false) }
        }
    }

    fun refreshGrades() {
        val semester = uiState.selectedSemester ?: return
        Napier.d("Force refreshing grades", tag = TAG)

        coroutineScope.launch {
            if (Semester.isAll(semester)) {
                loadAllGrades(forceRefresh = true)
            } else {
                loadSemester(semester, isRefresh = true)
            }
        }
    }

    private suspend fun loadAllGrades(forceRefresh: Boolean) {
        uiState = uiState.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh)
        if (requireLogin()) return

        when (val result = getAllGrades(forceRefresh)) {
            is Outcome.Ok -> {
                val gpa = computeGpa(result.value)
                uiState = uiState.copy(
                    grades = result.value.sortedWith(
                        compareByDescending<GradeEntry> { it.semesterName }.thenBy { it.moduleName }
                    ),
                    overallGpa = gpa.average,
                    semesterGpa = null,
                    totalCreditsEarned = gpa.earnedCredits,
                    isLoading = false,
                    isRefreshing = false,
                    error = null
                )
            }
            is Outcome.Err -> failWith(result.error)
        }
    }

    private suspend fun loadSemester(semester: Semester, isRefresh: Boolean) {
        uiState = uiState.copy(isLoading = !isRefresh, isRefreshing = isRefresh)
        if (requireLogin()) return

        when (val result = getGradesForSemester(semester, forceRefresh = isRefresh)) {
            is Outcome.Ok -> {
                val gpa = computeGpa(result.value)
                uiState = uiState.copy(
                    grades = result.value,
                    semesterGpa = gpa.average,
                    overallGpa = null,
                    totalCreditsEarned = gpa.earnedCredits,
                    isLoading = false,
                    isRefreshing = false,
                    error = null
                )
            }
            is Outcome.Err -> failWith(result.error)
        }
    }

    /**
     * Stop and ask for a login when there is nothing to authenticate with.
     *
     * This is a state of its own rather than an error: the screen shows a login prompt, not a
     * failure message with a retry button that could not work.
     */
    private fun requireLogin(): Boolean {
        if (sessionRepository.canAuthenticate()) return false

        Napier.d("Grades need a login first", tag = TAG)
        uiState = uiState.copy(
            isLoadingSemesters = false,
            isLoading = false,
            isRefreshing = false,
            error = null,
            requiresLogin = true
        )
        return true
    }

    private fun failWith(error: AppError) {
        Napier.e("Failed to load grades: $error", tag = TAG)
        uiState = uiState.copy(
            isLoading = false,
            isRefreshing = false,
            error = error,
            // An expired session that could not be renewed means the credentials are gone or no
            // longer accepted, so the login screen is the honest next step.
            requiresLogin = error is AppError.NoCredentials || error is AppError.InvalidCredentials
        )
    }
}
