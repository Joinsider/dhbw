package de.joinside.dhbw.data.storage.credentials

/**
 * Wrapper class for SecureStorage that implements SecureStorageInterface.
 * This allows for dependency injection and testability.
 */
class SecureStorageWrapper(private val secureStorage: SecureStorage) : SecureStorageInterface {
    override fun setString(key: String, value: String) {
        secureStorage.setString(key, value)
    }

    override fun getString(key: String, defaultValue: String): String {
        return secureStorage.getString(key, defaultValue)
    }

    override fun remove(key: String) {
        secureStorage.remove(key)
    }

    override fun clear() {
        secureStorage.clear()
    }
}

