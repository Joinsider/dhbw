/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.notification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_preferences")

class NotificationPreferencesManager(private val context: Context) {

    companion object {
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val TIMETABLE_NOTIFICATIONS_KEY = booleanPreferencesKey("timetable_notifications")
        private val GRADE_NOTIFICATIONS_KEY = booleanPreferencesKey("grade_notifications")
        private val CLASS_REMINDER_NOTIFICATIONS_KEY = booleanPreferencesKey("class_reminder_notifications")
        private val CLASS_REMINDER_TIME_KEY = intPreferencesKey("class_reminder_time_minutes")
    }

    val notificationsEnabled: Flow<Boolean> = context.notificationPreferencesDataStore.data
        .map { preferences -> preferences[NOTIFICATIONS_ENABLED_KEY] ?: true }

    val timetableNotificationsEnabled: Flow<Boolean> = context.notificationPreferencesDataStore.data
        .map { preferences -> preferences[TIMETABLE_NOTIFICATIONS_KEY] ?: true }

    val gradeNotificationsEnabled: Flow<Boolean> = context.notificationPreferencesDataStore.data
        .map { preferences -> preferences[GRADE_NOTIFICATIONS_KEY] ?: true }

    val classReminderNotificationsEnabled: Flow<Boolean> = context.notificationPreferencesDataStore.data
        .map { preferences -> preferences[CLASS_REMINDER_NOTIFICATIONS_KEY] ?: false } // Default disabled

    val classReminderTimeMinutes: Flow<Int> = context.notificationPreferencesDataStore.data
        .map { preferences -> preferences[CLASS_REMINDER_TIME_KEY] ?: 30 } // Default 30 minutes

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.notificationPreferencesDataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setTimetableNotificationsEnabled(enabled: Boolean) {
        context.notificationPreferencesDataStore.edit { preferences ->
            preferences[TIMETABLE_NOTIFICATIONS_KEY] = enabled
        }
    }

    suspend fun setGradeNotificationsEnabled(enabled: Boolean) {
        context.notificationPreferencesDataStore.edit { preferences ->
            preferences[GRADE_NOTIFICATIONS_KEY] = enabled
        }
    }

    suspend fun setClassReminderNotificationsEnabled(enabled: Boolean) {
        context.notificationPreferencesDataStore.edit { preferences ->
            preferences[CLASS_REMINDER_NOTIFICATIONS_KEY] = enabled
        }
    }

    suspend fun setClassReminderTimeMinutes(minutes: Int) {
        context.notificationPreferencesDataStore.edit { preferences ->
            preferences[CLASS_REMINDER_TIME_KEY] = minutes
        }
    }

    suspend fun getNotificationsEnabledBlocking(): Boolean {
        return notificationsEnabled.first()
    }

    suspend fun getTimetableNotificationsEnabledBlocking(): Boolean {
        return timetableNotificationsEnabled.first()
    }

    suspend fun getGradeNotificationsEnabledBlocking(): Boolean {
        return gradeNotificationsEnabled.first()
    }

    suspend fun getClassReminderNotificationsEnabledBlocking(): Boolean {
        return classReminderNotificationsEnabled.first()
    }

    suspend fun getClassReminderTimeMinutesBlocking(): Int {
        return classReminderTimeMinutes.first()
    }
}
