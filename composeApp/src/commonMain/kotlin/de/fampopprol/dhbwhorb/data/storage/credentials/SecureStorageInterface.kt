package de.fampopprol.dhbwhorb.data.storage.credentials

/**
 * Interface for secure storage operations.
 * This interface allows for dependency injection and testability.
 */
interface SecureStorageInterface {
    fun setString(key: String, value: String)
    fun getString(key: String, defaultValue: String = ""): String
    fun remove(key: String)
    fun clear()
}

