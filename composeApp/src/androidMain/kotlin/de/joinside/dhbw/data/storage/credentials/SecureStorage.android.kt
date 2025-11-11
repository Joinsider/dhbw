@file:Suppress("DEPRECATION")

package de.joinside.dhbw.data.storage.credentials

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SecureStorage {
    private val sharedPreferences: SharedPreferences by lazy {
        val appContext = getApplicationContext()
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        try {
            EncryptedSharedPreferences.create(
                appContext,
                "dualis_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Handle corrupted keystore - delete and recreate
            android.util.Log.e("SecureStorage", "Encrypted storage corrupted, recreating...", e)

            // Delete the corrupted preferences file
            appContext.deleteSharedPreferences("dualis_secure_prefs")

            // Recreate the encrypted preferences
            EncryptedSharedPreferences.create(
                appContext,
                "dualis_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private val keysKey = "_stored_keys"

    actual fun setString(key: String, value: String) {
        sharedPreferences.edit { putString(key, value) }
        addKeyToTracking(key)
    }

    actual fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    actual fun remove(key: String) {
        sharedPreferences.edit { remove(key) }
        removeKeyFromTracking(key)
    }

    actual fun clear() {
        val keys = getTrackedKeys()
        sharedPreferences.edit {
            keys.forEach { key ->
                remove(key)
            }
            remove(keysKey)
        }
    }

    private fun getTrackedKeys(): Set<String> {
        val keysString = sharedPreferences.getString(keysKey, "") ?: ""
        return if (keysString.isEmpty()) emptySet() else keysString.split(",").toSet()
    }

    private fun addKeyToTracking(key: String) {
        val keys = getTrackedKeys().toMutableSet()
        keys.add(key)
        sharedPreferences.edit { putString(keysKey, keys.joinToString(",")) }
    }

    private fun removeKeyFromTracking(key: String) {
        val keys = getTrackedKeys().toMutableSet()
        keys.remove(key)
        sharedPreferences.edit { putString(keysKey, keys.joinToString(",")) }
    }

    private fun getApplicationContext(): Context {
        return de.joinside.dhbw.DualisApplication.appContext
    }
}
