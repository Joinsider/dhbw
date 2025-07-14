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

class TimetableCacheManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("TimetableCache", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun getCacheKey(weekStart: LocalDate): String {
        return "timetable_${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    }

    fun saveTimetable(weekStart: LocalDate, timetable: List<TimetableDay>) {
        val json = gson.toJson(timetable)
        sharedPreferences.edit { putString(getCacheKey(weekStart), json) }
        Log.d("TimetableCacheManager", "Saved timetable for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")

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
                null
            }
        } else {
            Log.d("TimetableCacheManager", "No cached timetable found for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            null
        }
    }

    fun clearCache() {
        sharedPreferences.edit { clear() }
        Log.d("TimetableCacheManager", "Cache cleared.")
    }

    // Debug method to check what's currently cached
    fun debugCacheContents() {
        val today = LocalDate.now()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

        Log.d("TimetableCacheManager", "=== DEBUG CACHE CONTENTS ===")
        Log.d("TimetableCacheManager", "Today: $today")
        Log.d("TimetableCacheManager", "Week start: $weekStart")
        Log.d("TimetableCacheManager", "Cache key: ${getCacheKey(weekStart)}")

        val timetable = loadTimetable(weekStart)
        if (timetable != null) {
            Log.d("TimetableCacheManager", "Found cached data with ${timetable.size} days")
            timetable.forEach { day ->
                Log.d("TimetableCacheManager", "Day ${day.date}: ${day.events.size} events")
                day.events.forEach { event ->
                    Log.d("TimetableCacheManager", "  - ${event.title} (${event.startTime}-${event.endTime}) in ${event.room}")
                }
            }
        } else {
            Log.d("TimetableCacheManager", "No cached data found!")
        }
        Log.d("TimetableCacheManager", "=== END DEBUG CACHE CONTENTS ===")
    }
}
