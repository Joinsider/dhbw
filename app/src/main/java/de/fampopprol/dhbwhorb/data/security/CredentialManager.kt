package de.fampopprol.dhbwhorb.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
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
import java.security.GeneralSecurityException

class CredentialManager(val context: Context) {

    private val dataStore = context.dataStore

    // Create MasterKey with Samsung-compatible settings
    private val masterKey = try {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(false) // Important for Samsung compatibility
            .build()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to create MasterKey with preferred settings, falling back", e)
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    // Primary encrypted storage with Samsung-specific error handling
    private val encryptedSharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: GeneralSecurityException) {
        Log.w(TAG, "Failed to create EncryptedSharedPreferences, using fallback", e)
        null
    } catch (e: IOException) {
        Log.w(TAG, "IOException creating EncryptedSharedPreferences, using fallback", e)
        null
    }

    // Fallback to regular SharedPreferences for Samsung devices with issues
    private val fallbackPreferences: SharedPreferences = context.getSharedPreferences(
        "credentials_fallback", Context.MODE_PRIVATE
    )

    // Check if device is Samsung
    private val isSamsungDevice = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    companion object {
        private const val KEY_PASSWORD = "password"
        private const val KEY_PASSWORD_FALLBACK = "password_fb"
        private const val TAG = "CredentialManager"

        private val KEY_USERNAME_DATASTORE = stringPreferencesKey("username")
        private val KEY_IS_LOGGED_IN_DATASTORE = booleanPreferencesKey("is_logged_in")
    }

    suspend fun saveCredentials(username: String, password: String) {
        try {
            // Try encrypted storage first
            var encryptedSaveSuccessful = false
            encryptedSharedPreferences?.let { encPrefs ->
                try {
                    encPrefs.edit {
                        putString(KEY_PASSWORD, password)
                    }
                    encryptedSaveSuccessful = true
                    Log.d(TAG, "Credentials saved to encrypted storage")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save to encrypted storage", e)
                }
            }

            // For Samsung devices or if encrypted storage failed, also use fallback
            if (isSamsungDevice || !encryptedSaveSuccessful) {
                try {
                    // Simple obfuscation for fallback (better than nothing)
                    val obfuscatedPassword = obfuscateString(password)
                    fallbackPreferences.edit {
                        putString(KEY_PASSWORD_FALLBACK, obfuscatedPassword)
                    }
                    Log.d(TAG, "Credentials saved to fallback storage")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save to fallback storage", e)
                }
            }

            // Save username and login state
            dataStore.edit { prefs ->
                prefs[KEY_USERNAME_DATASTORE] = username
                prefs[KEY_IS_LOGGED_IN_DATASTORE] = true
            }
            Log.d(TAG, "Credentials saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving credentials", e)
            throw e
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
            // Try encrypted storage first
            encryptedSharedPreferences?.getString(KEY_PASSWORD, null)?.let { password ->
                Log.d(TAG, "Retrieved password from encrypted storage")
                return password
            }

            // Fallback for Samsung devices or if encrypted storage failed
            fallbackPreferences.getString(KEY_PASSWORD_FALLBACK, null)?.let { obfuscatedPassword ->
                val password = deobfuscateString(obfuscatedPassword)
                Log.d(TAG, "Retrieved password from fallback storage")
                return password
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Error retrieving password", e)
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

            // Clear both encrypted and fallback storage
            encryptedSharedPreferences?.edit {
                remove(KEY_PASSWORD)
            }
            fallbackPreferences.edit {
                remove(KEY_PASSWORD_FALLBACK)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error logging out", e)
        }
    }

    suspend fun clearAllCredentials() {
        try {
            dataStore.edit { prefs -> prefs.clear() }

            // Clear both storage methods
            encryptedSharedPreferences?.edit { clear() }
            fallbackPreferences.edit { clear() }

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

    // Simple obfuscation method (not cryptographically secure, but better than plaintext)
    private fun obfuscateString(input: String): String {
        return android.util.Base64.encodeToString(
            input.toByteArray().map { (it + 42).toByte() }.toByteArray(),
            android.util.Base64.DEFAULT
        )
    }

    private fun deobfuscateString(obfuscated: String): String {
        return try {
            val decoded = android.util.Base64.decode(obfuscated, android.util.Base64.DEFAULT)
            String(decoded.map { (it - 42).toByte() }.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "Error deobfuscating string", e)
            ""
        }
    }
}
