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

            Log.d(TAG, "Cached grades for semester: ${semester.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching grades", e)
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

            Log.d(TAG, "Cached ${semesters.size} semesters")
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

            // Check if cache is expired
            val currentTime = System.currentTimeMillis()
            val cacheAge = currentTime - timestamp
            val cacheExpiryMillis = CACHE_EXPIRY_HOURS * 60 * 60 * 1000

            if (cacheAge > cacheExpiryMillis) {
                Log.d(TAG, "Cached grades expired (age: ${cacheAge / 1000 / 60} minutes)")
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

            // Check if cache is expired
            val currentTime = System.currentTimeMillis()
            val cacheAge = currentTime - timestamp
            val cacheExpiryMillis = CACHE_EXPIRY_HOURS * 60 * 60 * 1000

            if (cacheAge > cacheExpiryMillis) {
                Log.d(TAG, "Cached semesters expired (age: ${cacheAge / 1000 / 60} minutes)")
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
     * Get cached grades as a Flow for reactive updates
     */
    fun getCachedGradesFlow(): Flow<Pair<StudyGrades, Semester>?> {
        return context.gradesDataStore.data.map { preferences ->
            try {
                val gradesJson = preferences[GRADES_DATA_KEY]
                val semesterJson = preferences[SELECTED_SEMESTER_KEY]
                val timestamp = preferences[GRADES_TIMESTAMP_KEY] ?: 0

                if (gradesJson == null || semesterJson == null) return@map null

                // Check if cache is expired
                val currentTime = System.currentTimeMillis()
                val cacheAge = currentTime - timestamp
                val cacheExpiryMillis = CACHE_EXPIRY_HOURS * 60 * 60 * 1000

                if (cacheAge > cacheExpiryMillis) return@map null

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
            }
            Log.d(TAG, "Cleared grades cache")
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
}
