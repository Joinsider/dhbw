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
import com.google.gson.reflect.TypeToken
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import kotlinx.coroutines.flow.first

private val Context.gradesDataStore: DataStore<Preferences> by preferencesDataStore(name = "grades_cache")

class GradesCacheManager(context: Context) : BaseCacheManager(context) {

    override val tag = "GradesCacheManager"
    private val cacheExpiryHours = 24 // Cache expires after 24 hours

    companion object {
        private val GRADES_DATA_KEY = stringPreferencesKey("grades_data")
        private val GRADES_TIMESTAMP_KEY = longPreferencesKey("grades_timestamp")
        private val SELECTED_SEMESTER_KEY = stringPreferencesKey("selected_semester")
        private val AVAILABLE_SEMESTERS_KEY = stringPreferencesKey("available_semesters")
        private val SEMESTERS_TIMESTAMP_KEY = longPreferencesKey("semesters_timestamp")
    }

    /**
     * Cache study grades for a specific semester
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
            }

            Log.d(tag, "Cached grades for semester: ${semester.displayName}")
        } catch (e: Exception) {
            Log.e(tag, "Error caching grades", e)
        }
    }

    /**
     * Cache available semesters
     */
    suspend fun cacheSemesters(semesters: List<Semester>) {
        try {
            val semestersJson = gson.toJson(semesters)
            val currentTime = System.currentTimeMillis()

            context.gradesDataStore.edit { preferences ->
                preferences[AVAILABLE_SEMESTERS_KEY] = semestersJson
                preferences[SEMESTERS_TIMESTAMP_KEY] = currentTime
            }

            Log.d(tag, "Cached ${semesters.size} semesters")
        } catch (e: Exception) {
            Log.e(tag, "Error caching semesters", e)
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
                Log.d(tag, "No cached grades found")
                return null
            }

            // Check if cache is expired
            if (!isCacheValid(timestamp, cacheExpiryHours)) {
                return null
            }

            val studyGrades = gson.fromJson(gradesJson, StudyGrades::class.java)
            val semester = gson.fromJson(semesterJson, Semester::class.java)

            Log.d(tag, "Retrieved cached grades for semester: ${semester.displayName}")
            return Pair(studyGrades, semester)

        } catch (e: Exception) {
            Log.e(tag, "Error retrieving cached grades", e)
            return null
        }
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
                Log.d(tag, "No cached semesters found")
                return null
            }

            // Check if cache is expired
            if (!isCacheValid(timestamp, cacheExpiryHours)) {
                return null
            }

            val type = object : TypeToken<List<Semester>>() {}.type
            val semesters = gson.fromJson<List<Semester>>(semestersJson, type)

            Log.d(tag, "Retrieved ${semesters.size} cached semesters")
            return semesters

        } catch (e: Exception) {
            Log.e(tag, "Error retrieving cached semesters", e)
            return null
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
                Log.d(tag, "Cached grades are for different semester")
                return false
            }

            // Check if cache is expired
            return isCacheValid(timestamp, cacheExpiryHours)

        } catch (e: Exception) {
            Log.e(tag, "Error checking cached grades validity", e)
            return false
        }
    }

    /**
     * Clear all cached grades data
     */
    override fun clearCache() {
        try {
            kotlinx.coroutines.runBlocking {
                context.gradesDataStore.edit { preferences ->
                    preferences.remove(GRADES_DATA_KEY)
                    preferences.remove(GRADES_TIMESTAMP_KEY)
                    preferences.remove(SELECTED_SEMESTER_KEY)
                    preferences.remove(AVAILABLE_SEMESTERS_KEY)
                    preferences.remove(SEMESTERS_TIMESTAMP_KEY)
                }
            }
            Log.d(tag, "Cleared grades cache")
        } catch (e: Exception) {
            Log.e(tag, "Error clearing grades cache", e)
        }
    }
}
