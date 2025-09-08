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
import kotlinx.coroutines.launch

/**
 * Manages the UI state and coordinates data operations for the grades screen with enhanced persistence
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

    private var hasLoadedFromCache = false

    /**
     * Initializes the screen with enhanced cache loading and background refresh
     */
    fun initialize(
        failedToLoadGradesMessage: String,
        authenticationFailedMessage: String,
        noCredentialsFoundMessage: String,
        pleaseLoginMessage: String,
        onAuthFailed: (GradesAuthManager.AuthResult) -> Unit
    ) {
        scope.launch {
            // First, try to load any available cached data immediately
            val hasLoadedCache = loadBestAvailableCache()

            if (hasLoadedCache) {
                isLoading = false
                hasLoadedFromCache = true
                android.util.Log.d("GradesStateManager", "App started with cached data available")
            }

            // Clear expired cache
            dataManager.clearExpiredCache()

            // Update access time
            dataManager.updateAccessTime()

            // Then ensure authentication and fetch fresh data if needed
            val needsRefresh = !dataManager.hasValidCachedData() || !hasLoadedCache

            authManager.ensureAuthentication { authResult ->
                when (authResult) {
                    GradesAuthManager.AuthResult.SUCCESS -> {
                        if (needsRefresh) {
                            loadSemesters(forceRefresh = true, failedToLoadGradesMessage)
                        } else {
                            // We have valid cached data, just update in background
                            android.util.Log.d("GradesStateManager", "Valid cache available, refreshing in background")
                            loadSemestersInBackground(failedToLoadGradesMessage)
                        }
                    }
                    GradesAuthManager.AuthResult.FAILED -> {
                        handleAuthFailure(authenticationFailedMessage)
                    }
                    GradesAuthManager.AuthResult.NO_CREDENTIALS -> {
                        handleAuthFailure(noCredentialsFoundMessage)
                    }
                    GradesAuthManager.AuthResult.NO_STORED_CREDENTIALS -> {
                        handleAuthFailure(pleaseLoginMessage)
                    }
                }
            }
        }
    }

    /**
     * Load the best available cached data on app startup
     */
    private suspend fun loadBestAvailableCache(): Boolean {
        return try {
            val cachedData = dataManager.loadBestAvailableCache()
            if (cachedData != null) {
                val (grades, semester, semesters) = cachedData

                // Load available semesters if cached
                if (semesters != null && semesters.isNotEmpty()) {
                    availableSemesters = semesters
                    isLoadingSemesters = false
                    android.util.Log.d("GradesStateManager", "Loaded ${semesters.size} cached semesters")
                }

                // Load grades and selected semester if cached
                if (grades != null && semester != null) {
                    studyGrades = grades
                    selectedSemester = semester
                    android.util.Log.d("GradesStateManager", "Loaded cached grades for semester: ${semester.displayName}")
                } else if (semesters != null && semesters.isNotEmpty()) {
                    // If we have semesters but no grades, select the best default semester
                    val defaultSemester = dataManager.getDefaultSemester(semesters)
                    if (defaultSemester != null) {
                        selectedSemester = defaultSemester
                        android.util.Log.d("GradesStateManager", "Selected default semester: ${defaultSemester.displayName}")
                    }
                }

                errorMessage = null
                return true
            } else {
                android.util.Log.d("GradesStateManager", "No cached data available")
                return false
            }
        } catch (e: Exception) {
            android.util.Log.e("GradesStateManager", "Error loading cached data", e)
            false
        }
    }

    /**
     * Loads available semesters and selects the default one (with loading UI)
     */
    private fun loadSemesters(forceRefresh: Boolean, failedToLoadGradesMessage: String) {
        if (!hasLoadedFromCache) {
            isLoading = true
        }
        isLoadingSemesters = true
        errorMessage = null

        dataManager.fetchAvailableSemesters(forceRefresh) { semesters ->
            availableSemesters = semesters
            isLoadingSemesters = false

            // Select default semester if none is selected
            scope.launch {
                if (selectedSemester == null || !semesters.any { it.value == selectedSemester?.value }) {
                    val defaultSemester = dataManager.getDefaultSemester(semesters)
                    defaultSemester?.let { semester ->
                        selectedSemester = semester
                        loadGradesForSemester(semester, updateLoadingState = true, forceRefresh, failedToLoadGradesMessage)
                    }
                } else {
                    // Refresh grades for currently selected semester
                    selectedSemester?.let { semester ->
                        loadGradesForSemester(semester, updateLoadingState = true, forceRefresh, failedToLoadGradesMessage)
                    }
                }
            }
        }
    }

    /**
     * Loads semesters in background without showing loading UI
     */
    private fun loadSemestersInBackground(failedToLoadGradesMessage: String) {
        dataManager.fetchAvailableSemesters(forceRefresh = false) { semesters ->
            availableSemesters = semesters

            // Refresh grades for currently selected semester in background
            selectedSemester?.let { semester ->
                loadGradesForSemester(semester, updateLoadingState = false, forceRefresh = false, failedToLoadGradesMessage)
            }
        }
    }

    /**
     * Loads grades for a specific semester
     */
    private fun loadGradesForSemester(
        semester: Semester,
        updateLoadingState: Boolean,
        forceRefresh: Boolean,
        failedToLoadGradesMessage: String
    ) {
        if (updateLoadingState) {
            isLoading = true
        }
        errorMessage = null

        dataManager.fetchGradesForSemester(semester, forceRefresh) { grades ->
            if (updateLoadingState) {
                isLoading = false
            }

            if (grades != null) {
                studyGrades = grades
                selectedSemester = semester
                errorMessage = null
                android.util.Log.d("GradesStateManager", "Loaded grades for semester: ${semester.displayName}")
            } else {
                if (studyGrades == null) {
                    errorMessage = failedToLoadGradesMessage
                }
                android.util.Log.e("GradesStateManager", "Failed to load grades for semester: ${semester.displayName}")
            }
        }
    }

    /**
     * Handle authentication failures with cached data fallback
     */
    private fun handleAuthFailure(message: String) {
        if (hasLoadedFromCache && (studyGrades != null || availableSemesters.isNotEmpty())) {
            // We have cached data, so show it with a warning
            errorMessage = "$message - showing cached data"
            isLoading = false
            android.util.Log.d("GradesStateManager", "Auth failed but showing cached data")
        } else {
            // No cached data available
            isLoading = false
            errorMessage = message
            android.util.Log.d("GradesStateManager", "Auth failed with no cached data")
        }
    }

    /**
     * Handles semester selection changes
     */
    fun onSemesterSelected(
        semester: Semester,
        failedToLoadGradesMessage: String
    ) {
        if (selectedSemester?.value != semester.value) {
            selectedSemester = semester
            loadGradesForSemester(semester, updateLoadingState = false, forceRefresh = false, failedToLoadGradesMessage)
        }
    }

    /**
     * Handles pull-to-refresh
     */
    fun onRefresh(
        failedToLoadGradesMessage: String,
        authenticationFailedMessage: String,
        noCredentialsFoundMessage: String,
        pleaseLoginMessage: String
    ) {
        isRefreshing = true
        errorMessage = null

        authManager.ensureAuthentication { authResult ->
            when (authResult) {
                GradesAuthManager.AuthResult.SUCCESS -> {
                    // Force refresh both semesters and grades
                    dataManager.fetchAvailableSemesters(forceRefresh = true) { semesters ->
                        availableSemesters = semesters

                        selectedSemester?.let { semester ->
                            dataManager.fetchGradesForSemester(semester, forceRefresh = true) { grades ->
                                isRefreshing = false
                                if (grades != null) {
                                    studyGrades = grades
                                    errorMessage = null
                                } else {
                                    errorMessage = failedToLoadGradesMessage
                                }
                            }
                        } ?: run {
                            isRefreshing = false
                        }
                    }
                }
                GradesAuthManager.AuthResult.FAILED -> {
                    isRefreshing = false
                    if (studyGrades == null) {
                        errorMessage = authenticationFailedMessage
                    } else {
                        errorMessage = "$authenticationFailedMessage - showing cached data"
                    }
                }
                GradesAuthManager.AuthResult.NO_CREDENTIALS -> {
                    isRefreshing = false
                    if (studyGrades == null) {
                        errorMessage = noCredentialsFoundMessage
                    } else {
                        errorMessage = "$noCredentialsFoundMessage - showing cached data"
                    }
                }
                GradesAuthManager.AuthResult.NO_STORED_CREDENTIALS -> {
                    isRefreshing = false
                    if (studyGrades == null) {
                        errorMessage = pleaseLoginMessage
                    } else {
                        errorMessage = "$pleaseLoginMessage - showing cached data"
                    }
                }
            }
        }
    }

    /**
     * Handles retry attempts
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
        ) { }
    }
}
