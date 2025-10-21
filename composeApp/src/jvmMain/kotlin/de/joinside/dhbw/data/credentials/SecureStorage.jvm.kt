package de.joinside.dhbw.data.credentials

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SecureStorage {
    private val keyring: Keyring = Keyring.create()
    private val serviceName = "DualisApp"
    private val keysKey = "_stored_keys"

    actual fun setString(key: String, value: String) {
        try {
            keyring.setPassword(serviceName, key, value)
            addKeyToTracking(key)
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
        }
    }

    actual fun getString(key: String, defaultValue: String): String {
        return try {
            keyring.getPassword(serviceName, key) ?: defaultValue
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
            defaultValue
        }
    }

    actual fun remove(key: String) {
        try {
            keyring.deletePassword(serviceName, key)
            removeKeyFromTracking(key)
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
        }
    }

    actual fun clear() {
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
    }

    private fun getTrackedKeys(): Set<String> {
        return try {
            val keysString = keyring.getPassword(serviceName, keysKey) ?: ""
            if (keysString.isEmpty()) emptySet() else keysString.split(",").toSet()
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
            emptySet()
        }
    }

    private fun addKeyToTracking(key: String) {
        val keys = getTrackedKeys().toMutableSet()
        keys.add(key)
        try {
            keyring.setPassword(serviceName, keysKey, keys.joinToString(","))
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
        }
    }

    private fun removeKeyFromTracking(key: String) {
        val keys = getTrackedKeys().toMutableSet()
        keys.remove(key)
        try {
            keyring.setPassword(serviceName, keysKey, keys.joinToString(","))
        } catch (e: PasswordAccessException) {
            e.printStackTrace()
        }
    }
}
