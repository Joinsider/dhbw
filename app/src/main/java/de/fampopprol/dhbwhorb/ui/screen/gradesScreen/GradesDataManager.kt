/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.gradesScreen

import android.util.Log
import de.fampopprol.dhbwhorb.data.cache.GradesCacheManager
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles all data operations for grades including fetching, caching, and semester management
 */
class GradesDataManager(
    private val dualisService: DualisService,
    private val gradesCacheManager: GradesCacheManager?,
    private val scope: CoroutineScope
) {

    /**
     * Fetches grades for a specific semester
     * @param semester The semester to fetch grades for
     * @param forceRefresh Whether to bypass cache and fetch fresh data
     * @param onResult Callback with the result (StudyGrades or null if failed)
     */
    fun fetchGradesForSemester(
        semester: Semester,
        forceRefresh: Boolean = false,
        onResult: (StudyGrades?) -> Unit
    ) {
        scope.launch {
            // Check cache first if not forcing refresh
            if (!forceRefresh && gradesCacheManager != null) {
                val cachedData = getCachedGrades(semester)
                if (cachedData != null) {
                    onResult(cachedData)
                    Log.d("GradesDataManager", "Using cached grades for semester: ${semester.displayName}")
                    return@launch
                }
            }

            // Fetch from network
            dualisService.getStudyGradesForSemester(semester) { result ->
                if (result != null) {
                    // Cache the result
                    scope.launch {
                        gradesCacheManager?.cacheGrades(result, semester)
                    }
                    Log.d("GradesDataManager", "Fetched grades for semester ${semester.displayName}")
                } else {
                    Log.e("GradesDataManager", "Failed to fetch grades for semester ${semester.displayName}")
                }
                onResult(result)
            }
        }
    }

    /**
     * Fetches available semesters from cache or network
     * @param forceRefresh Whether to bypass cache and fetch fresh data
     * @param onResult Callback with the list of semesters
     */
    fun fetchAvailableSemesters(
        forceRefresh: Boolean = false,
        onResult: (List<Semester>) -> Unit
    ) {
        scope.launch {
            // Check cache first if not forcing refresh
            if (!forceRefresh && gradesCacheManager != null) {
                val cachedSemesters = gradesCacheManager.getCachedSemesters()
                if (cachedSemesters != null) {
                    onResult(cachedSemesters)
                    Log.d("GradesDataManager", "Using cached semesters: ${cachedSemesters.size} semesters")
                    return@launch
                }
            }

            // Fetch from network
            dualisService.getAvailableSemesters { semesters ->
                if (semesters != null && semesters.isNotEmpty()) {
                    // Cache the semesters
                    scope.launch {
                        gradesCacheManager?.cacheSemesters(semesters)
                    }
                    onResult(semesters)
                    Log.d("GradesDataManager", "Fetched ${semesters.size} semesters")
                } else {
                    // Fallback to default semester selection if fetching fails
                    val defaultSemesters = Semester.getDefaultSemesters()
                    onResult(defaultSemesters)
                    Log.w("GradesDataManager", "Failed to fetch semesters, using defaults")
                }
            }
        }
    }

    /**
     * Gets the default selected semester from a list of semesters
     */
    fun getDefaultSemester(semesters: List<Semester>): Semester? {
        return semesters.find { it.isSelected } ?: semesters.firstOrNull()
    }

    /**
     * Retrieves cached grades for a specific semester
     */
    private suspend fun getCachedGrades(semester: Semester): StudyGrades? {
        return if (gradesCacheManager?.hasValidCachedGrades(semester) == true) {
            gradesCacheManager.getCachedGrades()?.first
        } else {
            null
        }
    }
}
