/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.cache

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val Context.gradesDataStore: DataStore<Preferences> by preferencesDataStore(name = "grades_cache")

class GradesCacheManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        private const val TAG = "GradesCacheManager"
        private const val CACHE_EXPIRY_HOURS = 24 // Cache expires after 24 hours

        private val GRADES_DATA_KEY = stringPreferencesKey("grades_data")
        private val GRADES_TIMESTAMP_KEY = longPreferencesKey("grades_timestamp")
        private val SELECTED_SEMESTER_KEY = stringPreferencesKey("selected_semester")
        private val AVAILABLE_SEMESTERS_KEY = stringPreferencesKey("available_semesters")
        private val SEMESTERS_TIMESTAMP_KEY = longPreferencesKey("semesters_timestamp")
        private val LAST_VIEWED_SEMESTER_KEY = stringPreferencesKey("last_viewed_semester")
        private val LAST_ACCESS_TIME_KEY = longPreferencesKey("last_access_time")
    }

    /**
     * Cache study grades for a specific semester with enhanced metadata
     */
    suspend fun cacheGrades(studyGrades: StudyGrades, semester: Semester) {
        try {
            val gradesJson = gson.toJson(studyGrades)
            val semesterJson = gson.toJson(semester)
            val currentTime = System.currentTimeMillis()

            context.gradesDataStore.edit { preferences ->
                preferences[GRADES_DATA_KEY] = gradesJson
                preferences[GRADES_TIMESTAMP_KEY] = currentTime
                preferences[SELECTED_SEMESTER_KEY] = semesterJson
                preferences[LAST_VIEWED_SEMESTER_KEY] = semesterJson
                preferences[LAST_ACCESS_TIME_KEY] = currentTime
            }

            Log.d(TAG, "Cached grades for semester: ${semester.displayName} at ${LocalDateTime.now()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching grades", e)
        }
    }

    /**
     * Cache available semesters with enhanced metadata
     */
    suspend fun cacheSemesters(semesters: List<Semester>) {
        try {
            val semestersJson = gson.toJson(semesters)
            val currentTime = System.currentTimeMillis()

            context.gradesDataStore.edit { preferences ->
                preferences[AVAILABLE_SEMESTERS_KEY] = semestersJson
                preferences[SEMESTERS_TIMESTAMP_KEY] = currentTime
            }

            Log.d(TAG, "Cached ${semesters.size} semesters at ${LocalDateTime.now()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching semesters", e)
        }
    }

    /**
     * Get cached grades if they exist and are not expired
     */
    suspend fun getCachedGrades(): Pair<StudyGrades, Semester>? {
        try {
            val preferences = context.gradesDataStore.data.first()
            val gradesJson = preferences[GRADES_DATA_KEY]
            val timestamp = preferences[GRADES_TIMESTAMP_KEY] ?: 0
            val semesterJson = preferences[SELECTED_SEMESTER_KEY]

            if (gradesJson == null || semesterJson == null) {
                Log.d(TAG, "No cached grades found")
                return null
            }

            val studyGrades = gson.fromJson(gradesJson, StudyGrades::class.java)
            val semester = gson.fromJson(semesterJson, Semester::class.java)

            Log.d(TAG, "Retrieved cached grades for semester: ${semester.displayName}")
            return Pair(studyGrades, semester)

        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached grades", e)
            return null
        }
    }

    /**
     * Get valid (non-expired) cached grades
     */
    suspend fun getValidCachedGrades(): Pair<StudyGrades, Semester>? {
        if (!isCacheValid()) {
            Log.d(TAG, "Cached grades are expired")
            return null
        }
        return getCachedGrades()
    }

    /**
     * Get cached semesters if they exist and are not expired
     */
    suspend fun getCachedSemesters(): List<Semester>? {
        try {
            val preferences = context.gradesDataStore.data.first()
            val semestersJson = preferences[AVAILABLE_SEMESTERS_KEY]
            val timestamp = preferences[SEMESTERS_TIMESTAMP_KEY] ?: 0

            if (semestersJson == null) {
                Log.d(TAG, "No cached semesters found")
                return null
            }

            val type = object : TypeToken<List<Semester>>() {}.type
            val semesters = gson.fromJson<List<Semester>>(semestersJson, type)

            Log.d(TAG, "Retrieved ${semesters.size} cached semesters")
            return semesters

        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving cached semesters", e)
            return null
        }
    }

    /**
     * Get valid (non-expired) cached semesters
     */
    suspend fun getValidCachedSemesters(): List<Semester>? {
        if (!isSemestersCacheValid()) {
            Log.d(TAG, "Cached semesters are expired")
            return null
        }
        return getCachedSemesters()
    }

    /**
     * Check if cached grades are valid (not expired)
     */
    suspend fun isCacheValid(): Boolean {
        return try {
            val preferences = context.gradesDataStore.data.first()
            val timestamp = preferences[GRADES_TIMESTAMP_KEY] ?: 0
            val currentTime = System.currentTimeMillis()
            val cacheAge = currentTime - timestamp
            val cacheExpiryMillis = CACHE_EXPIRY_HOURS * 60 * 60 * 1000

            val isValid = cacheAge <= cacheExpiryMillis
            if (!isValid) {
                val hoursOld = cacheAge / (1000 * 60 * 60)
                Log.d(TAG, "Cache is expired (${hoursOld}h old)")
            }
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error checking cache validity", e)
            false
        }
    }

    /**
     * Check if cached semesters are valid (not expired)
     */
    suspend fun isSemestersCacheValid(): Boolean {
        return try {
            val preferences = context.gradesDataStore.data.first()
            val timestamp = preferences[SEMESTERS_TIMESTAMP_KEY] ?: 0
            val currentTime = System.currentTimeMillis()
            val cacheAge = currentTime - timestamp
            val cacheExpiryMillis = CACHE_EXPIRY_HOURS * 60 * 60 * 1000

            val isValid = cacheAge <= cacheExpiryMillis
            if (!isValid) {
                val hoursOld = cacheAge / (1000 * 60 * 60)
                Log.d(TAG, "Semesters cache is expired (${hoursOld}h old)")
            }
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error checking semesters cache validity", e)
            false
        }
    }

    /**
     * Check if cached grades exist for the given semester and are not expired
     */
    suspend fun hasValidCachedGrades(semester: Semester): Boolean {
        try {
            val preferences = context.gradesDataStore.data.first()
            val semesterJson = preferences[SELECTED_SEMESTER_KEY]
            val timestamp = preferences[GRADES_TIMESTAMP_KEY] ?: 0

            if (semesterJson == null) return false

            val cachedSemester = gson.fromJson(semesterJson, Semester::class.java)
            if (cachedSemester.value != semester.value) {
                Log.d(TAG, "Cached grades are for different semester")
                return false
            }

            // Check if cache is expired
            val currentTime = System.currentTimeMillis()
            val cacheAge = currentTime - timestamp
            val cacheExpiryMillis = CACHE_EXPIRY_HOURS * 60 * 60 * 1000

            return cacheAge <= cacheExpiryMillis

        } catch (e: Exception) {
            Log.e(TAG, "Error checking cached grades validity", e)
            return false
        }
    }

    /**
     * Get the last viewed semester for app startup
     */
    suspend fun getLastViewedSemester(): Semester? {
        return try {
            val preferences = context.gradesDataStore.data.first()
            val semesterJson = preferences[LAST_VIEWED_SEMESTER_KEY]

            if (semesterJson != null) {
                val semester = gson.fromJson(semesterJson, Semester::class.java)
                Log.d(TAG, "Last viewed semester: ${semester.displayName}")
                semester
            } else {
                Log.d(TAG, "No last viewed semester found")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving last viewed semester", e)
            null
        }
    }

    /**
     * Update last access time for tracking usage
     */
    suspend fun updateLastAccessTime() {
        try {
            context.gradesDataStore.edit { preferences ->
                preferences[LAST_ACCESS_TIME_KEY] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating last access time", e)
        }
    }

    /**
     * Load the best available cached data on app startup
     */
    suspend fun loadBestAvailableCache(): Triple<StudyGrades?, Semester?, List<Semester>?>? {
        try {
            Log.d(TAG, "Loading best available cache on app startup")

            // Try to get valid cached data first
            val validGrades = getValidCachedGrades()
            val validSemesters = getValidCachedSemesters()

            if (validGrades != null && validSemesters != null) {
                Log.d(TAG, "Found valid cached grades and semesters")
                return Triple(validGrades.first, validGrades.second, validSemesters)
            }

            // If no valid cache, try to load any cached data as fallback
            val anyGrades = getCachedGrades()
            val anySemesters = getCachedSemesters()

            if (anyGrades != null || anySemesters != null) {
                Log.d(TAG, "Found fallback cached data (may be expired)")
                return Triple(anyGrades?.first, anyGrades?.second, anySemesters)
            }

            Log.d(TAG, "No cached data available")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading best available cache", e)
            return null
        }
    }

    /**
     * Get cached grades as a Flow for reactive updates
     */
    fun getCachedGradesFlow(): Flow<Pair<StudyGrades, Semester>?> {
        return context.gradesDataStore.data.map { preferences ->
            try {
                val gradesJson = preferences[GRADES_DATA_KEY]
                val semesterJson = preferences[SELECTED_SEMESTER_KEY]
                val timestamp = preferences[GRADES_TIMESTAMP_KEY] ?: 0

                if (gradesJson == null || semesterJson == null) return@map null

                val studyGrades = gson.fromJson(gradesJson, StudyGrades::class.java)
                val semester = gson.fromJson(semesterJson, Semester::class.java)

                Pair(studyGrades, semester)
            } catch (e: Exception) {
                Log.e(TAG, "Error in cached grades flow", e)
                null
            }
        }
    }

    /**
     * Get cache timestamp for debugging
     */
    suspend fun getCacheTimestamp(): Long {
        return try {
            val preferences = context.gradesDataStore.data.first()
            preferences[GRADES_TIMESTAMP_KEY] ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cache timestamp", e)
            0
        }
    }

    /**
     * Get last cache update time as LocalDateTime
     */
    suspend fun getLastCacheUpdateTime(): LocalDateTime? {
        return try {
            val timestamp = getCacheTimestamp()
            if (timestamp > 0) {
                val instant = java.time.Instant.ofEpochMilli(timestamp)
                LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cache update time", e)
            null
        }
    }

    /**
     * Clear expired cache entries
     */
    suspend fun clearExpiredCache() {
        try {
            val preferences = context.gradesDataStore.data.first()
            val currentTime = System.currentTimeMillis()
            val cacheExpiryMillis = CACHE_EXPIRY_HOURS * 60 * 60 * 1000

            var clearedCount = 0

            // Check grades cache
            val gradesTimestamp = preferences[GRADES_TIMESTAMP_KEY] ?: 0
            if (gradesTimestamp > 0 && (currentTime - gradesTimestamp) > cacheExpiryMillis) {
                context.gradesDataStore.edit { prefs ->
                    prefs.remove(GRADES_DATA_KEY)
                    prefs.remove(GRADES_TIMESTAMP_KEY)
                    prefs.remove(SELECTED_SEMESTER_KEY)
                }
                clearedCount++
                Log.d(TAG, "Cleared expired grades cache")
            }

            // Check semesters cache
            val semestersTimestamp = preferences[SEMESTERS_TIMESTAMP_KEY] ?: 0
            if (semestersTimestamp > 0 && (currentTime - semestersTimestamp) > cacheExpiryMillis) {
                context.gradesDataStore.edit { prefs ->
                    prefs.remove(AVAILABLE_SEMESTERS_KEY)
                    prefs.remove(SEMESTERS_TIMESTAMP_KEY)
                }
                clearedCount++
                Log.d(TAG, "Cleared expired semesters cache")
            }

            if (clearedCount > 0) {
                Log.d(TAG, "Cleared $clearedCount expired cache entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing expired cache", e)
        }
    }

    /**
     * Clear all cached grades data
     */
    suspend fun clearCache() {
        try {
            context.gradesDataStore.edit { preferences ->
                preferences.remove(GRADES_DATA_KEY)
                preferences.remove(GRADES_TIMESTAMP_KEY)
                preferences.remove(SELECTED_SEMESTER_KEY)
                preferences.remove(AVAILABLE_SEMESTERS_KEY)
                preferences.remove(SEMESTERS_TIMESTAMP_KEY)
                preferences.remove(LAST_VIEWED_SEMESTER_KEY)
                preferences.remove(LAST_ACCESS_TIME_KEY)
            }
            Log.d(TAG, "Cleared all grades cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing grades cache", e)
        }
    }

    /**
     * Clear only grades data, keep semesters cache
     */
    suspend fun clearGradesCache() {
        try {
            context.gradesDataStore.edit { preferences ->
                preferences.remove(GRADES_DATA_KEY)
                preferences.remove(GRADES_TIMESTAMP_KEY)
                preferences.remove(SELECTED_SEMESTER_KEY)
            }
            Log.d(TAG, "Cleared grades data cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing grades data cache", e)
        }
    }

    /**
     * Clear only semesters cache
     */
    suspend fun clearSemestersCache() {
        try {
            context.gradesDataStore.edit { preferences ->
                preferences.remove(AVAILABLE_SEMESTERS_KEY)
                preferences.remove(SEMESTERS_TIMESTAMP_KEY)
            }
            Log.d(TAG, "Cleared semesters cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing semesters cache", e)
        }
    }

    /**
     * Debug method to check cache contents
     */
    suspend fun debugCacheContents() {
        try {
            val preferences = context.gradesDataStore.data.first()

            Log.d(TAG, "=== DEBUG GRADES CACHE CONTENTS ===")

            val gradesTimestamp = preferences[GRADES_TIMESTAMP_KEY] ?: 0
            val semestersTimestamp = preferences[SEMESTERS_TIMESTAMP_KEY] ?: 0
            val lastAccessTime = preferences[LAST_ACCESS_TIME_KEY] ?: 0

            if (gradesTimestamp > 0) {
                val gradesAge = (System.currentTimeMillis() - gradesTimestamp) / (1000 * 60 * 60)
                val isValidGrades = isCacheValid()
                Log.d(TAG, "Grades cache: ${if (isValidGrades) "VALID" else "EXPIRED"} (${gradesAge}h old)")

                val selectedSemesterJson = preferences[SELECTED_SEMESTER_KEY]
                if (selectedSemesterJson != null) {
                    val semester = gson.fromJson(selectedSemesterJson, Semester::class.java)
                    Log.d(TAG, "Cached semester: ${semester.displayName}")
                }
            } else {
                Log.d(TAG, "No grades cache found")
            }

            if (semestersTimestamp > 0) {
                val semestersAge = (System.currentTimeMillis() - semestersTimestamp) / (1000 * 60 * 60)
                val isValidSemesters = isSemestersCacheValid()
                Log.d(TAG, "Semesters cache: ${if (isValidSemesters) "VALID" else "EXPIRED"} (${semestersAge}h old)")

                val semesters = getCachedSemesters()
                Log.d(TAG, "Cached semesters count: ${semesters?.size ?: 0}")
            } else {
                Log.d(TAG, "No semesters cache found")
            }

            if (lastAccessTime > 0) {
                val lastAccessAge = (System.currentTimeMillis() - lastAccessTime) / (1000 * 60)
                Log.d(TAG, "Last access: $lastAccessAge minutes ago")
            }

            Log.d(TAG, "=== END DEBUG GRADES CACHE CONTENTS ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error in debug cache contents", e)
        }
    }
}
