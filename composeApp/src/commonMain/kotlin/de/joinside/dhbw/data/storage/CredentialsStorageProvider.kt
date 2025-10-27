package de.joinside.dhbw.data.storage.credentials

/**
 * Provider for managing user credentials using SecureStorage.
 * This is a prerequisite for future Dualis API implementation.
 *
 * Usage:
 * - Store credentials when user logs in
 * - Retrieve credentials when authenticating with Dualis API
 * - Check if user is logged in (has stored credentials)
 * - Clear credentials on logout
 */
class CredentialsStorageProvider(private val secureStorage: SecureStorage) {

    companion object {
        private const val KEY_DUALIS_USERNAME = "dualis_username"
        private const val KEY_DUALIS_PASSWORD = "dualis_password"
    }

    /**
     * Store Dualis credentials securely.
     * @param username The Dualis username
     * @param password The Dualis password
     */
    fun storeCredentials(username: String, password: String) {
        secureStorage.setString(KEY_DUALIS_USERNAME, username)
        secureStorage.setString(KEY_DUALIS_PASSWORD, password)
    }

    /**
     * Retrieve stored Dualis username.
     * @return The username or empty string if not stored
     */
    fun getUsername(): String {
        return secureStorage.getString(KEY_DUALIS_USERNAME, "")
    }

    /**
     * Retrieve stored Dualis password.
     * @return The password or empty string if not stored
     */
    fun getPassword(): String {
        return secureStorage.getString(KEY_DUALIS_PASSWORD, "")
    }

    /**
     * Check if credentials are stored.
     * @return true if both username and password are stored, false otherwise
     */
    fun hasStoredCredentials(): Boolean {
        val username = getUsername()
        val password = getPassword()
        return username.isNotEmpty() && password.isNotEmpty()
    }

    /**
     * Get stored credentials as a pair.
     * @return Pair of (username, password) or null if credentials are not complete
     */
    fun getCredentials(): Pair<String, String>? {
        return if (hasStoredCredentials()) {
            Pair(getUsername(), getPassword())
        } else {
            null
        }
    }

    /**
     * Clear only Dualis credentials from secure storage.
     * Use this for logout functionality.
     */
    fun clearCredentials() {
        secureStorage.remove(KEY_DUALIS_USERNAME)
        secureStorage.remove(KEY_DUALIS_PASSWORD)
    }

    /**
     * Clear all secure storage data.
     * Future implementation will also clear Room database.
     *
     * TODO: Add Room database clearing when Dualis API is implemented
     */
    fun clearAllData() {
        // Clear all secure storage
        secureStorage.clear()

        // TODO: Clear Room database
        // This will be implemented when the Dualis API scraper is added
        // Example: appDatabase.clearAllTables()
    }
}