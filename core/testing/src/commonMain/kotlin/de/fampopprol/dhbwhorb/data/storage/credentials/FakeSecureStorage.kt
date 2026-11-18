package de.fampopprol.dhbwhorb.data.storage.credentials

/**
 * Fake implementation of SecureStorageInterface for testing purposes.
 * Stores values in a simple in-memory map.
 */
class FakeSecureStorage : SecureStorageInterface {
    private val storage = mutableMapOf<String, String>()

    /** Set to simulate a locked keychain; reads then still answer, but the caller sees [isLocked]. */
    override var isLocked: Boolean = false

    override fun setString(key: String, value: String) {
        storage[key] = value
    }

    override fun getString(key: String, defaultValue: String): String {
        return storage[key] ?: defaultValue
    }

    override fun remove(key: String) {
        storage.remove(key)
    }

    override fun clear() {
        storage.clear()
    }
}

