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
     * Load the best available cached data on app startup
     */
    suspend fun loadBestAvailableCache(): Triple<StudyGrades?, Semester?, List<Semester>?>? {
        return gradesCacheManager?.loadBestAvailableCache()
    }

    /**
     * Fetches grades for a specific semester with enhanced cache logic
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
            // Check for valid cache first if not forcing refresh
            if (!forceRefresh && gradesCacheManager != null) {
                val hasValidCache = gradesCacheManager.hasValidCachedGrades(semester)
                if (hasValidCache) {
                    val cachedData = gradesCacheManager.getValidCachedGrades()
                    if (cachedData != null && cachedData.second.value == semester.value) {
                        onResult(cachedData.first)
                        Log.d("GradesDataManager", "Using valid cached grades for semester: ${semester.displayName}")
                        return@launch
                    }
                }

                // If no valid cache but not forcing refresh, try fallback cache for offline usage
                if (!forceRefresh) {
                    val fallbackData = getCachedGrades(semester)
                    if (fallbackData != null) {
                        onResult(fallbackData)
                        Log.d("GradesDataManager", "Using fallback cached grades for semester: ${semester.displayName}")
                        // Continue to fetch fresh data in background
                    }
                }
            }

            // Fetch from network
            Log.d("GradesDataManager", "Fetching grades from API for semester: ${semester.displayName} (forced: $forceRefresh)")
            dualisService.getStudyGradesForSemester(semester) { result ->
                if (result != null) {
                    // Cache the result
                    scope.launch {
                        gradesCacheManager?.cacheGrades(result, semester)
                    }
                    Log.d("GradesDataManager", "Fetched and cached grades for semester ${semester.displayName}")
                    onResult(result)
                } else {
                    Log.e("GradesDataManager", "Failed to fetch grades for semester ${semester.displayName}")

                    // If API failed and we don't have cached data yet, try any available cache as fallback
                    scope.launch {
                        val fallbackData = getCachedGrades(semester)
                        if (fallbackData != null) {
                            Log.d("GradesDataManager", "API failed, using any available cached data for semester: ${semester.displayName}")
                            onResult(fallbackData)
                        } else {
                            onResult(null)
                        }
                    }
                }
            }
        }
    }

    /**
     * Fetches available semesters from cache or network with enhanced logic
     * @param forceRefresh Whether to bypass cache and fetch fresh data
     * @param onResult Callback with the list of semesters
     */
    fun fetchAvailableSemesters(
        forceRefresh: Boolean = false,
        onResult: (List<Semester>) -> Unit
    ) {
        scope.launch {
            // Check for valid cache first if not forcing refresh
            if (!forceRefresh && gradesCacheManager != null) {
                val validCachedSemesters = gradesCacheManager.getValidCachedSemesters()
                if (validCachedSemesters != null) {
                    onResult(validCachedSemesters)
                    Log.d("GradesDataManager", "Using valid cached semesters: ${validCachedSemesters.size} semesters")
                    return@launch
                }

                // If no valid cache but not forcing refresh, try fallback cache
                if (!forceRefresh) {
                    val fallbackSemesters = gradesCacheManager.getCachedSemesters()
                    if (fallbackSemesters != null) {
                        onResult(fallbackSemesters)
                        Log.d("GradesDataManager", "Using fallback cached semesters: ${fallbackSemesters.size} semesters")
                        // Continue to fetch fresh data in background
                    }
                }
            }

            // Fetch from network
            Log.d("GradesDataManager", "Fetching semesters from API (forced: $forceRefresh)")
            dualisService.getAvailableSemesters { semesters ->
                if (semesters != null && semesters.isNotEmpty()) {
                    // Cache the semesters
                    scope.launch {
                        gradesCacheManager?.cacheSemesters(semesters)
                    }
                    onResult(semesters)
                    Log.d("GradesDataManager", "Fetched and cached ${semesters.size} semesters")
                } else {
                    Log.w("GradesDataManager", "Failed to fetch semesters from API")

                    // Try to use any cached semesters as fallback
                    scope.launch {
                        val fallbackSemesters = gradesCacheManager?.getCachedSemesters()
                        if (fallbackSemesters != null && fallbackSemesters.isNotEmpty()) {
                            onResult(fallbackSemesters)
                            Log.d("GradesDataManager", "API failed, using cached semesters: ${fallbackSemesters.size} semesters")
                        } else {
                            // Final fallback to default semester selection if all else fails
                            val defaultSemesters = Semester.getDefaultSemesters()
                            onResult(defaultSemesters)
                            Log.w("GradesDataManager", "No cached semesters available, using defaults")
                        }
                    }
                }
            }
        }
    }

    /**
     * Gets the default selected semester from a list of semesters, with preference for last viewed
     */
    suspend fun getDefaultSemester(semesters: List<Semester>): Semester? {
        // First, try to get the last viewed semester
        val lastViewedSemester = gradesCacheManager?.getLastViewedSemester()
        if (lastViewedSemester != null) {
            val matchingSemester = semesters.find { it.value == lastViewedSemester.value }
            if (matchingSemester != null) {
                Log.d("GradesDataManager", "Using last viewed semester: ${matchingSemester.displayName}")
                return matchingSemester
            }
        }

        // Fallback to the selected semester or first available
        return semesters.find { it.isSelected } ?: semesters.firstOrNull()
    }

    /**
     * Update access time when user visits grades screen
     */
    suspend fun updateAccessTime() {
        gradesCacheManager?.updateLastAccessTime()
    }

    /**
     * Retrieves cached grades for a specific semester (any cache, even expired)
     */
    private suspend fun getCachedGrades(semester: Semester): StudyGrades? {
        return try {
            val cachedData = gradesCacheManager?.getCachedGrades()
            if (cachedData != null && cachedData.second.value == semester.value) {
                cachedData.first
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("GradesDataManager", "Error retrieving cached grades", e)
            null
        }
    }

    /**
     * Clear expired cache entries
     */
    suspend fun clearExpiredCache() {
        gradesCacheManager?.clearExpiredCache()
    }

    /**
     * Check if we have valid cached data for faster loading
     */
    suspend fun hasValidCachedData(): Boolean {
        return gradesCacheManager?.isCacheValid() == true || gradesCacheManager?.isSemestersCacheValid() == true
    }

    /**
     * Get cache update time for UI display
     */
    suspend fun getLastCacheUpdateTime(): java.time.LocalDateTime? {
        return gradesCacheManager?.getLastCacheUpdateTime()
    }
}
