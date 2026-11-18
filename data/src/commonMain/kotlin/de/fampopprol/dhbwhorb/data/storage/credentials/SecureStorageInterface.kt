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

    /**
     * True when the platform store exists but cannot be read right now, which is the case on iOS
     * before the first unlock after a reboot and while the device is locked. A `""` from
     * [getString] then means "unknown", not "absent".
     *
     * Only the Apple implementation overrides this; everywhere else the store is always readable.
     */
    val isLocked: Boolean get() = false
}

