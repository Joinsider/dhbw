package de.fampopprol.dhbwhorb.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

class CredentialManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val TAG = "CredentialManager"
    }

    fun saveCredentials(username: String, password: String) {
        try {
            sharedPreferences.edit {
                putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password)
                    .putBoolean(KEY_IS_LOGGED_IN, true)
            }
            Log.d(TAG, "Credentials saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving credentials", e)
        }
    }

    fun getUsername(): String? {
        return try {
            sharedPreferences.getString(KEY_USERNAME, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving username", e)
            null
        }
    }

    fun getPassword(): String? {
        return try {
            sharedPreferences.getString(KEY_PASSWORD, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving password", e)
            null
        }
    }

    fun isLoggedIn(): Boolean {
        return try {
            sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking login status", e)
            false
        }
    }

    fun logout() {
        try {
            sharedPreferences.edit {
                remove(KEY_USERNAME)
                    .remove(KEY_PASSWORD)
                    .putBoolean(KEY_IS_LOGGED_IN, false)
            }
            Log.d(TAG, "Logged out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging out", e)
        }
    }

    fun clearAllCredentials() {
        try {
            sharedPreferences.edit { clear() }
            Log.d(TAG, "All credentials cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing credentials", e)
        }
    }

    fun hasStoredCredentials(): Boolean {
        return getUsername() != null && getPassword() != null
    }
}
