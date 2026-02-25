package de.fampopprol.dhbwhorb.data.credentials

/**
 * Test-only implementation of SecureStorage for Android unit tests.
 * Uses in-memory storage since Android Context is not available in unit tests.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
class SecureStorage {
    private val storage = mutableMapOf<String, String>()
    private val keysKey = "_stored_keys"

    fun setString(key: String, value: String) {
        storage[key] = value
        addKeyToTracking(key)
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return storage[key] ?: defaultValue
    }

    fun remove(key: String) {
        storage.remove(key)
        removeKeyFromTracking(key)
    }

    fun clear() {
        val keys = getTrackedKeys()
        keys.forEach { key ->
            storage.remove(key)
        }
        storage.remove(keysKey)
    }

    private fun getTrackedKeys(): Set<String> {
        val keysString = storage[keysKey] ?: ""
        return if (keysString.isEmpty()) emptySet() else keysString.split(",").toSet()
    }

    private fun addKeyToTracking(key: String) {
        val keys = getTrackedKeys().toMutableSet()
        keys.add(key)
        storage[keysKey] = keys.joinToString(",")
    }

    private fun removeKeyFromTracking(key: String) {
        val keys = getTrackedKeys().toMutableSet()
        keys.remove(key)
        storage[keysKey] = keys.joinToString(",")
    }
}

