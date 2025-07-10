package de.fampopprol.dhbwhorb.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TimetableCacheManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("TimetableCache", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun getCacheKey(weekStart: LocalDate): String {
        return "timetable_${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    }

    fun saveTimetable(weekStart: LocalDate, timetable: List<TimetableDay>) {
        val json = gson.toJson(timetable)
        sharedPreferences.edit().putString(getCacheKey(weekStart), json).apply()
        Log.d("TimetableCacheManager", "Saved timetable for week: ${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
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
        sharedPreferences.edit().clear().apply()
        Log.d("TimetableCacheManager", "Cache cleared.")
    }
}
