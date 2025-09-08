/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.widget.WidgetUpdateManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.core.content.edit
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class TimetableCacheManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("TimetableCache", Context.MODE_PRIVATE)
    private val metadataPreferences: SharedPreferences =
        context.getSharedPreferences("TimetableCacheMetadata", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val CACHE_VALIDITY_HOURS = 24 // Cache is valid for 24 hours
        private const val LAST_CACHE_UPDATE_KEY = "last_cache_update"
        private const val CACHED_WEEKS_KEY = "cached_weeks"
    }

    private fun getCacheKey(weekStart: LocalDate): String {
        return "timetable_${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    }

    private fun getMetadataKey(weekStart: LocalDate): String {
        return "metadata_${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    }

    fun saveTimetable(weekStart: LocalDate, timetable: List<TimetableDay>) {
        val json = gson.toJson(timetable)
        val timestamp = LocalDateTime.now().toString()

        sharedPreferences.edit {
            putString(getCacheKey(weekStart), json)
        }

        // Save metadata
        metadataPreferences.edit {
            putString(getMetadataKey(weekStart), timestamp)
            putString(LAST_CACHE_UPDATE_KEY, timestamp)
        }

        // Update the list of cached weeks
        updateCachedWeeksList(weekStart)

        Log.d("TimetableCacheManager", "Saved timetable for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)} at $timestamp")

        // Debug: Show what data we're saving
        timetable.forEach { day ->
            Log.d("TimetableCacheManager", "Saving day ${day.date} with ${day.events.size} events")
            day.events.forEach { event ->
                Log.d("TimetableCacheManager", "  Event: ${event.title} at ${event.startTime}-${event.endTime} in ${event.room}")
            }
        }

        // Update widgets when new timetable data is saved
        Log.d("TimetableCacheManager", "Triggering widget updates after saving timetable")
        WidgetUpdateManager.updateAllWidgets(context)
    }

    fun loadTimetable(weekStart: LocalDate): List<TimetableDay>? {
        val json = sharedPreferences.getString(getCacheKey(weekStart), null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<TimetableDay>>() {}.type
                val timetable = gson.fromJson<List<TimetableDay>>(json, type)
                Log.d("TimetableCacheManager", "Loaded timetable for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
                timetable
            } catch (e: Exception) {
                Log.e("TimetableCacheManager", "Error loading timetable from cache for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}", e)
                // Remove corrupted cache entry
                removeTimetable(weekStart)
                null
            }
        } else {
            Log.d("TimetableCacheManager", "No cached timetable found for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            null
        }
    }

    fun isTimetableCached(weekStart: LocalDate): Boolean {
        return sharedPreferences.contains(getCacheKey(weekStart))
    }

    fun isCacheValid(weekStart: LocalDate): Boolean {
        val timestampString = metadataPreferences.getString(getMetadataKey(weekStart), null)
        return if (timestampString != null) {
            try {
                val cacheTime = LocalDateTime.parse(timestampString)
                val now = LocalDateTime.now()
                val hoursSinceCache = ChronoUnit.HOURS.between(cacheTime, now)
                val isValid = hoursSinceCache < CACHE_VALIDITY_HOURS
                Log.d("TimetableCacheManager", "Cache for week $weekStart is ${if (isValid) "valid" else "expired"} (${hoursSinceCache}h old)")
                isValid
            } catch (e: Exception) {
                Log.e("TimetableCacheManager", "Error parsing cache timestamp for week $weekStart", e)
                false
            }
        } else {
            false
        }
    }

    fun getValidCachedTimetable(weekStart: LocalDate): List<TimetableDay>? {
        return if (isCacheValid(weekStart)) {
            loadTimetable(weekStart)
        } else {
            Log.d("TimetableCacheManager", "Cache for week $weekStart is expired or invalid")
            null
        }
    }

    fun getAllCachedWeeks(): List<LocalDate> {
        val cachedWeeksJson = metadataPreferences.getString(CACHED_WEEKS_KEY, null)
        return if (cachedWeeksJson != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val weekStrings = gson.fromJson<List<String>>(cachedWeeksJson, type)
                weekStrings.map { LocalDate.parse(it) }.sorted()
            } catch (e: Exception) {
                Log.e("TimetableCacheManager", "Error parsing cached weeks list", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun updateCachedWeeksList(weekStart: LocalDate) {
        val currentWeeks = getAllCachedWeeks().toMutableList()
        if (!currentWeeks.contains(weekStart)) {
            currentWeeks.add(weekStart)
            currentWeeks.sort()

            val weekStrings = currentWeeks.map { it.toString() }
            val json = gson.toJson(weekStrings)
            metadataPreferences.edit {
                putString(CACHED_WEEKS_KEY, json)
            }
            Log.d("TimetableCacheManager", "Updated cached weeks list, now contains ${currentWeeks.size} weeks")
        }
    }

    private fun removeTimetable(weekStart: LocalDate) {
        sharedPreferences.edit {
            remove(getCacheKey(weekStart))
        }
        metadataPreferences.edit {
            remove(getMetadataKey(weekStart))
        }

        // Update cached weeks list
        val currentWeeks = getAllCachedWeeks().toMutableList()
        if (currentWeeks.remove(weekStart)) {
            val weekStrings = currentWeeks.map { it.toString() }
            val json = gson.toJson(weekStrings)
            metadataPreferences.edit {
                putString(CACHED_WEEKS_KEY, json)
            }
        }

        Log.d("TimetableCacheManager", "Removed corrupted timetable cache for week: $weekStart")
    }

    fun getLastCacheUpdateTime(): LocalDateTime? {
        val timestampString = metadataPreferences.getString(LAST_CACHE_UPDATE_KEY, null)
        return if (timestampString != null) {
            try {
                LocalDateTime.parse(timestampString)
            } catch (e: Exception) {
                Log.e("TimetableCacheManager", "Error parsing last cache update timestamp", e)
                null
            }
        } else {
            null
        }
    }

    fun clearExpiredCache() {
        val cachedWeeks = getAllCachedWeeks()
        var removedCount = 0

        cachedWeeks.forEach { weekStart ->
            if (!isCacheValid(weekStart)) {
                removeTimetable(weekStart)
                removedCount++
            }
        }

        if (removedCount > 0) {
            Log.d("TimetableCacheManager", "Cleared $removedCount expired cache entries")
        }
    }

    fun clearCache() {
        sharedPreferences.edit { clear() }
        metadataPreferences.edit { clear() }
        Log.d("TimetableCacheManager", "All cache cleared.")
    }

    // Enhanced debug method to check what's currently cached
    fun debugCacheContents() {
        val today = LocalDate.now()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

        Log.d("TimetableCacheManager", "=== DEBUG CACHE CONTENTS ===")
        Log.d("TimetableCacheManager", "Today: $today")
        Log.d("TimetableCacheManager", "Current week start: $weekStart")

        val allCachedWeeks = getAllCachedWeeks()
        Log.d("TimetableCacheManager", "Total cached weeks: ${allCachedWeeks.size}")

        allCachedWeeks.forEach { cachedWeek ->
            val isValid = isCacheValid(cachedWeek)
            val timetable = loadTimetable(cachedWeek)
            Log.d("TimetableCacheManager", "Week $cachedWeek: ${if (isValid) "VALID" else "EXPIRED"}, ${timetable?.size ?: 0} days")

            if (cachedWeek == weekStart && timetable != null) {
                timetable.forEach { day ->
                    Log.d("TimetableCacheManager", "  Day ${day.date}: ${day.events.size} events")
                    day.events.forEach { event ->
                        Log.d("TimetableCacheManager", "    - ${event.title} (${event.startTime}-${event.endTime}) in ${event.room}")
                    }
                }
            }
        }

        val lastUpdate = getLastCacheUpdateTime()
        Log.d("TimetableCacheManager", "Last cache update: $lastUpdate")
        Log.d("TimetableCacheManager", "=== END DEBUG CACHE CONTENTS ===")
    }
}
