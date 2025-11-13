package de.joinside.dhbw.data.dualis.remote.session

import de.joinside.dhbw.data.dualis.remote.models.AuthData
import de.joinside.dhbw.data.storage.credentials.SecureStorageInterface
import io.github.aakira.napier.Napier

/**
 * Manages the authentication session for Dualis.
 * Handles storing and retrieving session data including credentials and auth tokens.
 */
class SessionManager(private val secureStorage: SecureStorageInterface) {

    companion object {
        private const val TAG = "SessionManager"
        private const val KEY_USERNAME = "dualis_username"
        private const val KEY_PASSWORD = "dualis_password"
        private const val KEY_SESSION_ID = "dualis_session_id"
        private const val KEY_AUTH_TOKEN = "dualis_auth_token"
        private const val KEY_USER_FULL_NAME = "dualis_user_full_name"
        private const val KEY_IS_DEMO_MODE = "dualis_is_demo_mode"

        const val DEMO_EMAIL = "demo@hb.dhbw-stuttgart.de"
        const val DEMO_PASSWORD = "demo123"
    }

    private var currentAuthData: AuthData? = null
    private var isReAuthenticating = false

    /**
     * Store login credentials securely.
     */
    fun storeCredentials(username: String, password: String) {
        Napier.d("Storing credentials for user: $username", tag = TAG)
        secureStorage.setString(KEY_USERNAME, username)
        secureStorage.setString(KEY_PASSWORD, password)
    }

    /**
     * Get stored credentials.
     * @return Pair of (username, password) or null if not stored
     */
    fun getStoredCredentials(): Pair<String, String>? {
        val username = secureStorage.getString(KEY_USERNAME, "")
        val password = secureStorage.getString(KEY_PASSWORD, "")

        return if (username.isNotEmpty() && password.isNotEmpty()) {
            Pair(username, password)
        } else {
            null
        }
    }

    /**
     * Check if credentials are stored.
     */
    fun hasStoredCredentials(): Boolean {
        return getStoredCredentials() != null
    }

    /**
     * Store authentication data (session ID and auth token).
     */
    fun storeAuthData(authData: AuthData) {
        Napier.d("Storing auth data - SessionID: ${authData.sessionId.take(10)}..., AuthToken: ${authData.authToken.take(10)}..., FullName: ${authData.userFullName}", tag = TAG)
        currentAuthData = authData
        secureStorage.setString(KEY_SESSION_ID, authData.sessionId)
        secureStorage.setString(KEY_AUTH_TOKEN, authData.authToken)

        // Store user's full name if available
        if (authData.userFullName != null) {
            Napier.d("Storing user full name: '${authData.userFullName}'", tag = TAG)
            secureStorage.setString(KEY_USER_FULL_NAME, authData.userFullName)
        } else {
            Napier.w("User full name is null, removing stored value", tag = TAG)
            secureStorage.remove(KEY_USER_FULL_NAME)
        }
    }

    /**
     * Get stored authentication data.
     */
    fun getAuthData(): AuthData? {
        Napier.d("Getting auth data from SessionManager", tag = TAG)

        if (currentAuthData != null) {
            Napier.d("Returning cached auth data: $currentAuthData", tag = TAG)
            return currentAuthData
        }

        val sessionId = secureStorage.getString(KEY_SESSION_ID, "")
        val authToken = secureStorage.getString(KEY_AUTH_TOKEN, "")
        val userFullName = secureStorage.getString(KEY_USER_FULL_NAME, "").takeIf { it.isNotEmpty() }

        Napier.d("Retrieved from storage - SessionID: ${sessionId.take(10)}..., AuthToken: ${authToken.take(10)}..., FullName: $userFullName", tag = TAG)

        return if (sessionId.isNotEmpty() || authToken.isNotEmpty()) {
            AuthData(
                sessionId = sessionId,
                authToken = authToken,
                userFullName = userFullName
            ).also {
                currentAuthData = it
                Napier.d("Created and cached AuthData: $it", tag = TAG)
            }
        } else {
            Napier.d("No auth data found in storage", tag = TAG)
            null
        }
    }

    /**
     * Check if user is authenticated (has valid session).
     */
    fun isAuthenticated(): Boolean {
        return getAuthData() != null || isDemoMode()
    }

    /**
     * Check if this is a demo user.
     */
    fun isDemoUser(username: String, password: String): Boolean {
        return username == DEMO_EMAIL && password == DEMO_PASSWORD
    }

    /**
     * Set demo mode.
     */
    fun setDemoMode(enabled: Boolean) {
        Napier.d("Setting demo mode: $enabled", tag = TAG)
        secureStorage.setString(KEY_IS_DEMO_MODE, enabled.toString())
    }

    /**
     * Check if demo mode is active.
     */
    fun isDemoMode(): Boolean {
        return secureStorage.getString(KEY_IS_DEMO_MODE, "false").toBoolean()
    }

    /**
     * Check if re-authentication is in progress.
     */
    fun isReAuthenticating(): Boolean = isReAuthenticating

    /**
     * Set re-authentication state.
     */
    fun setReAuthenticating(value: Boolean) {
        isReAuthenticating = value
    }

    /**
     * Clear all session data and logout.
     */
    fun logout() {
        Napier.d("Logging out and clearing session data", tag = TAG)
        currentAuthData = null
        isReAuthenticating = false
        secureStorage.remove(KEY_USERNAME)
        secureStorage.remove(KEY_PASSWORD)
        secureStorage.remove(KEY_SESSION_ID)
        secureStorage.remove(KEY_AUTH_TOKEN)
        secureStorage.remove(KEY_USER_FULL_NAME)
        secureStorage.remove(KEY_IS_DEMO_MODE)
    }

    /**
     * Clear only authentication data (keep credentials for re-login).
     */
    fun clearAuthData() {
        Napier.d("Clearing auth data only", tag = TAG)
        currentAuthData = null
        secureStorage.remove(KEY_SESSION_ID)
        secureStorage.remove(KEY_AUTH_TOKEN)
        secureStorage.remove(KEY_USER_FULL_NAME)
    }
}