package de.fampopprol.dhbwhorb.data.calendar

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class CalendarSyncPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "CalendarSyncPrefs"
        private const val KEY_SECTION_ENABLED = "calendar_sync_section_enabled"
        private const val KEY_SYNC_ACTIVE = "calendar_sync_active"
    }

    fun isSectionEnabled(): Boolean = prefs.getBoolean(KEY_SECTION_ENABLED, false)
    fun setSectionEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SECTION_ENABLED, enabled) }
    }

    fun isSyncActive(): Boolean = prefs.getBoolean(KEY_SYNC_ACTIVE, false)
    fun setSyncActive(active: Boolean) {
        prefs.edit { putBoolean(KEY_SYNC_ACTIVE, active) }
    }
}
