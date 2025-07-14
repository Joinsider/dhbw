/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.updatePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "update_preferences")

class UpdatePreferencesManager(private val context: Context) {

    companion object {
        private val UPDATE_CHECK_ENABLED_KEY = booleanPreferencesKey("update_check_enabled")
        private val LAST_NOTIFIED_VERSION_KEY = stringPreferencesKey("last_notified_version")
        private val LAST_CHECK_TIME_KEY = stringPreferencesKey("last_check_time")
    }

    val isUpdateCheckEnabled: Flow<Boolean> = context.updatePreferencesDataStore.data
        .map { preferences -> preferences[UPDATE_CHECK_ENABLED_KEY] ?: true } // Default enabled

    val lastNotifiedVersion: Flow<String> = context.updatePreferencesDataStore.data
        .map { preferences -> preferences[LAST_NOTIFIED_VERSION_KEY] ?: "" }

    val lastCheckTime: Flow<String> = context.updatePreferencesDataStore.data
        .map { preferences -> preferences[LAST_CHECK_TIME_KEY] ?: "" }

    suspend fun setUpdateCheckEnabled(enabled: Boolean) {
        context.updatePreferencesDataStore.edit { preferences ->
            preferences[UPDATE_CHECK_ENABLED_KEY] = enabled
        }
    }

    suspend fun setLastNotifiedVersion(version: String) {
        context.updatePreferencesDataStore.edit { preferences ->
            preferences[LAST_NOTIFIED_VERSION_KEY] = version
        }
    }

    suspend fun setLastCheckTime(time: String) {
        context.updatePreferencesDataStore.edit { preferences ->
            preferences[LAST_CHECK_TIME_KEY] = time
        }
    }

    // Blocking versions for use in workers
    suspend fun isUpdateCheckEnabledBlocking(): Boolean {
        return isUpdateCheckEnabled.first()
    }

    suspend fun getLastNotifiedVersionBlocking(): String {
        return lastNotifiedVersion.first()
    }

    suspend fun getLastCheckTimeBlocking(): String {
        return lastCheckTime.first()
    }

    fun setLastNotifiedVersionBlocking(version: String) {
        kotlinx.coroutines.runBlocking {
            setLastNotifiedVersion(version)
        }
    }

    fun setLastCheckTimeBlocking(time: String) {
        kotlinx.coroutines.runBlocking {
            setLastCheckTime(time)
        }
    }
}
