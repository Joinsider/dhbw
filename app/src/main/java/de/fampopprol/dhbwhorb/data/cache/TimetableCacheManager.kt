/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.reflect.TypeToken
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.widget.WidgetUpdateManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.core.content.edit

class TimetableCacheManager(context: Context) : BaseCacheManager(context) {

    override val tag = "TimetableCacheManager"
    private val cacheExpiryHours = 24

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("TimetableCache", Context.MODE_PRIVATE)

    private fun getCacheKey(weekStart: LocalDate): String {
        return "timetable_${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    }

    private fun getTimestampKey(weekStart: LocalDate): String {
        return "timestamp_${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    }

    fun saveTimetable(weekStart: LocalDate, timetable: List<TimetableDay>) {
        val json = gson.toJson(timetable)
        val currentTime = System.currentTimeMillis()

        sharedPreferences.edit {
            putString(getCacheKey(weekStart), json)
            putLong(getTimestampKey(weekStart), currentTime)
        }

        Log.d(tag, "Saved timetable for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")

        // Debug: Show what data we're saving
        timetable.forEach { day ->
            Log.d(tag, "Saving day ${day.date} with ${day.events.size} events")
            day.events.forEach { event ->
                Log.d(tag, "  Event: ${event.title} at ${event.startTime}-${event.endTime} in ${event.room}")
            }
        }

        // Update widgets when new timetable data is saved
        Log.d(tag, "Triggering widget updates after saving timetable")
        WidgetUpdateManager.updateAllWidgets(context)
    }

    fun loadTimetable(weekStart: LocalDate): List<TimetableDay>? {
        val json = sharedPreferences.getString(getCacheKey(weekStart), null)
        val timestamp = sharedPreferences.getLong(getTimestampKey(weekStart), 0)

        if (json == null) {
            Log.d(tag, "No cached timetable found for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            return null
        }

        // Check if cache is expired
        if (!isCacheValid(timestamp, cacheExpiryHours)) {
            Log.d(tag, "Cached timetable expired for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            return null
        }

        return try {
            val type = object : TypeToken<List<TimetableDay>>() {}.type
            val timetable = gson.fromJson<List<TimetableDay>>(json, type)
            Log.d(tag, "Loaded timetable for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            timetable
        } catch (e: Exception) {
            Log.e(tag, "Error loading timetable from cache for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}", e)
            null
        }
    }

    override fun clearCache() {
        sharedPreferences.edit { clear() }
        Log.d(tag, "Cache cleared.")
    }

    // Debug method to check what's currently cached
    fun debugCacheContents() {
        val today = LocalDate.now()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

        Log.d(tag, "=== DEBUG CACHE CONTENTS ===")
        Log.d(tag, "Today: $today")
        Log.d(tag, "Week start: $weekStart")
        Log.d(tag, "Cache key: ${getCacheKey(weekStart)}")

        val timetable = loadTimetable(weekStart)
        if (timetable != null) {
            Log.d(tag, "Found cached data with ${timetable.size} days")
            timetable.forEach { day ->
                Log.d(tag, "Day ${day.date}: ${day.events.size} events")
                day.events.forEach { event ->
                    Log.d(tag, "  - ${event.title} (${event.startTime}-${event.endTime}) in ${event.room}")
                }
            }
        } else {
            Log.d(tag, "No cached data found!")
        }
        Log.d(tag, "=== END DEBUG CACHE CONTENTS ===")
    }
}
