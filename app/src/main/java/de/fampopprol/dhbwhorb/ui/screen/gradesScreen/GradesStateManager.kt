/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.gradesScreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import kotlinx.coroutines.CoroutineScope

/**
 * Manages the UI state and coordinates data operations for the grades screen
 */
class GradesStateManager(
    private val dataManager: GradesDataManager,
    private val authManager: GradesAuthManager,
    private val scope: CoroutineScope
) {

    // UI State
    var isLoading by mutableStateOf(true)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingSemesters by mutableStateOf(true)
        private set

    var studyGrades by mutableStateOf<StudyGrades?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var availableSemesters by mutableStateOf<List<Semester>>(emptyList())
        private set

    var selectedSemester by mutableStateOf<Semester?>(null)
        private set

    /**
     * Initializes the screen by ensuring authentication and loading initial data
     */
    fun initialize(
        failedToLoadGradesMessage: String,
        authenticationFailedMessage: String,
        noCredentialsFoundMessage: String,
        pleaseLoginMessage: String,
        onAuthFailed: (GradesAuthManager.AuthResult) -> Unit
    ) {
        isLoading = true
        errorMessage = null

        authManager.ensureAuthentication { authResult ->
            when (authResult) {
                GradesAuthManager.AuthResult.SUCCESS -> {
                    loadSemesters(forceRefresh = false, failedToLoadGradesMessage)
                }
                GradesAuthManager.AuthResult.FAILED -> {
                    isLoading = false
                    errorMessage = authenticationFailedMessage
                }
                GradesAuthManager.AuthResult.NO_CREDENTIALS -> {
                    isLoading = false
                    errorMessage = noCredentialsFoundMessage
                }
                GradesAuthManager.AuthResult.NO_STORED_CREDENTIALS -> {
                    isLoading = false
                    errorMessage = pleaseLoginMessage
                }
            }
        }
    }

    /**
     * Loads available semesters and selects the default one
     */
    private fun loadSemesters(forceRefresh: Boolean, failedToLoadGradesMessage: String) {
        isLoadingSemesters = true

        dataManager.fetchAvailableSemesters(forceRefresh) { semesters ->
            availableSemesters = semesters
            isLoadingSemesters = false

            // Select default semester if none is selected
            if (selectedSemester == null) {
                val defaultSemester = dataManager.getDefaultSemester(semesters)
                defaultSemester?.let { semester ->
                    selectedSemester = semester
                    loadGradesForSemester(semester, updateLoadingState = false, forceRefresh, failedToLoadGradesMessage)
                }
            }
        }
    }

    /**
     * Loads grades for a specific semester
     */
    fun loadGradesForSemester(
        semester: Semester,
        updateLoadingState: Boolean = true,
        forceRefresh: Boolean = false,
        failedToLoadGradesMessage: String
    ) {
        if (updateLoadingState && !isRefreshing) {
            isLoading = true
        }
        errorMessage = null

        dataManager.fetchGradesForSemester(semester, forceRefresh) { grades ->
            studyGrades = grades
            selectedSemester = semester
            isLoading = false
            isRefreshing = false

            if (grades == null) {
                errorMessage = failedToLoadGradesMessage
            }
        }
    }

    /**
     * Handles semester selection change
     */
    fun onSemesterSelected(semester: Semester, failedToLoadGradesMessage: String) {
        if (semester != selectedSemester) {
            selectedSemester = semester
            loadGradesForSemester(semester, failedToLoadGradesMessage = failedToLoadGradesMessage)
        }
    }

    /**
     * Handles pull-to-refresh action
     */
    fun onRefresh(
        failedToLoadGradesMessage: String,
        authenticationFailedMessage: String,
        noCredentialsFoundMessage: String,
        pleaseLoginMessage: String
    ) {
        isRefreshing = true
        selectedSemester?.let { semester ->
            loadGradesForSemester(semester, updateLoadingState = false, forceRefresh = true, failedToLoadGradesMessage)
        } ?: run {
            // If no semester is selected, reinitialize everything
            initialize(
                failedToLoadGradesMessage,
                authenticationFailedMessage,
                noCredentialsFoundMessage,
                pleaseLoginMessage
            ) { authResult ->
                isRefreshing = false
            }
        }
    }

    /**
     * Handles retry action after an error
     */
    fun onRetry(
        failedToLoadGradesMessage: String,
        authenticationFailedMessage: String,
        noCredentialsFoundMessage: String,
        pleaseLoginMessage: String
    ) {
        initialize(
            failedToLoadGradesMessage,
            authenticationFailedMessage,
            noCredentialsFoundMessage,
            pleaseLoginMessage
        ) { authResult ->
            // Error handling is now done in initialize method
        }
    }
}
