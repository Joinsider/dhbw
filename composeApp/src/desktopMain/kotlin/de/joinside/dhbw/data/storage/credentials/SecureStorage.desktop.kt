package de.joinside.dhbw.data.storage.credentials

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import java.io.File
import java.util.prefs.Preferences

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SecureStorage {
    private val keyring: Keyring? = try {
        Keyring.create()
    } catch (e: BackendNotSupportedException) {
        System.err.println("Warning: No keyring backend available. Falling back to Preferences storage.")
        System.err.println("Credentials will be stored less securely in user preferences.")
        null
    }
    
    private val prefs: Preferences by lazy {
        Preferences.userNodeForPackage(SecureStorage::class.java)
    }
    
    private val serviceName = "DualisApp"
    private val keysKey = "_stored_keys"

    actual fun setString(key: String, value: String) {
        if (keyring != null) {
            try {
                // Windows Credential Manager doesn't accept empty strings
                // If value is empty, remove the key instead
                if (value.isEmpty()) {
                    remove(key)
                    return
                }

                keyring.setPassword(serviceName, key, value)
                addKeyToTracking(key)
            } catch (e: PasswordAccessException) {
                e.printStackTrace()
            }
        } else {
            // Fallback to Preferences
            prefs.put(key, value)
        }
    }

    actual fun getString(key: String, defaultValue: String): String {
        return if (keyring != null) {
            try {
                keyring.getPassword(serviceName, key) ?: defaultValue
            } catch (e: PasswordAccessException) {
                e.printStackTrace()
                defaultValue
            }
        } else {
            // Fallback to Preferences
            prefs.get(key, defaultValue)
        }
    }

    actual fun remove(key: String) {
        if (keyring != null) {
            try {
                keyring.deletePassword(serviceName, key)
                removeKeyFromTracking(key)
            } catch (e: PasswordAccessException) {
                e.printStackTrace()
            }
        } else {
            // Fallback to Preferences
            prefs.remove(key)
        }
    }

    actual fun clear() {
        if (keyring != null) {
            val keys = getTrackedKeys()
            keys.forEach { key ->
                try {
                    keyring.deletePassword(serviceName, key)
                } catch (e: PasswordAccessException) {
                    e.printStackTrace()
                }
            }
            try {
                keyring.deletePassword(serviceName, keysKey)
            } catch (e: PasswordAccessException) {
                e.printStackTrace()
            }
        } else {
            // Fallback to Preferences
            prefs.clear()
        }
    }

    private fun getTrackedKeys(): Set<String> {
        if (keyring == null) return emptySet()
        return try {
            val keysString = keyring.getPassword(serviceName, keysKey) ?: ""
            if (keysString.isEmpty()) emptySet() else keysString.split(",").toSet()
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
            emptySet()
        }
    }

    private fun addKeyToTracking(key: String) {
        if (keyring == null) return
        val keys = getTrackedKeys().toMutableSet()
        keys.add(key)
        try {
            val keysString = keys.joinToString(",")
            // Only store if we have actual keys to track
            if (keysString.isNotEmpty()) {
                keyring.setPassword(serviceName, keysKey, keysString)
            }
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
        }
    }

    private fun removeKeyFromTracking(key: String) {
        if (keyring == null) return
        val keys = getTrackedKeys().toMutableSet()
        keys.remove(key)
        try {
            val keysString = keys.joinToString(",")
            // If no keys left, remove the tracking key entirely instead of storing empty string
            if (keysString.isEmpty()) {
                keyring.deletePassword(serviceName, keysKey)
            } else {
                keyring.setPassword(serviceName, keysKey, keysString)
            }
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
        }
    }
}
