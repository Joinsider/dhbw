/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DEPRECATION")

package de.fampopprol.dhbwhorb.data.security

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import de.fampopprol.dhbwhorb.data.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * CredentialManager handles secure storage of user credentials using encrypted storage.
 *
 * Note: This class uses EncryptedSharedPreferences and MasterKey which are currently deprecated
 * but remain the recommended approach until a stable replacement is available.
 */
class CredentialManager(context: Context) {

    private val dataStore = context.dataStore

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedSharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_PASSWORD = "password"
        private const val TAG = "CredentialManager"

        private val KEY_USERNAME_DATASTORE = stringPreferencesKey("username")
        private val KEY_IS_LOGGED_IN_DATASTORE = booleanPreferencesKey("is_logged_in")
    }

    /**
     * Save credentials persistently (when user checks "Remember me")
     */
    suspend fun saveCredentials(username: String, password: String) {
        try {
            encryptedSharedPreferences.edit {
                putString(KEY_PASSWORD, password)
            }
            dataStore.edit { prefs ->
                prefs[KEY_USERNAME_DATASTORE] = username
                prefs[KEY_IS_LOGGED_IN_DATASTORE] = true
            }
            Log.d(TAG, "Credentials saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving credentials", e)
        }
    }

    val usernameFlow: Flow<String?> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            prefs[KEY_USERNAME_DATASTORE]
        }

    fun getUsernameBlocking(): String? {
        return runBlocking {
            try {
                dataStore.data.first()[KEY_USERNAME_DATASTORE]
            } catch(_: Exception) {
                null
            }
        }
    }

    fun getPassword(): String? {
        return try {
            encryptedSharedPreferences.getString(KEY_PASSWORD, null)
        } catch (_: Exception) {
            null
        }
    }

    fun isLoggedInBlocking(): Boolean {
        return runBlocking {
            try {
                dataStore.data.first()[KEY_IS_LOGGED_IN_DATASTORE] ?: false
            } catch(_: Exception) {
                false
            }
        }
    }

    suspend fun logout() {
        try {
            dataStore.edit { prefs ->
                prefs.remove(KEY_USERNAME_DATASTORE)
                prefs[KEY_IS_LOGGED_IN_DATASTORE] = false
            }
            encryptedSharedPreferences.edit {
                remove(KEY_PASSWORD)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error logging out", e)
        }
    }

    suspend fun clearAllCredentials() {
        try {
            dataStore.edit { prefs -> prefs.clear() }
            encryptedSharedPreferences.edit { clear() }
            Log.d(TAG, "All credentials cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing credentials", e)
        }
    }

    suspend fun hasStoredCredentialsBlocking(): Boolean {
        val username = usernameFlow.first()
        val password = getPassword()
        return username != null && password != null
    }
}
