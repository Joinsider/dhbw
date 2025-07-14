/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.calendar

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import de.fampopprol.dhbwhorb.data.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CalendarSyncPreferencesManager(private val context: Context) {

    companion object {
        private val CALENDAR_SYNC_ENABLED = booleanPreferencesKey("calendar_sync_enabled")
        private val SELECTED_CALENDAR_ID = longPreferencesKey("selected_calendar_id")
    }

    val calendarSyncEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CALENDAR_SYNC_ENABLED] ?: false
    }

    val selectedCalendarId: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_CALENDAR_ID] ?: -1L
    }

    suspend fun setCalendarSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CALENDAR_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSelectedCalendarId(calendarId: Long) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_CALENDAR_ID] = calendarId
        }
    }
}
