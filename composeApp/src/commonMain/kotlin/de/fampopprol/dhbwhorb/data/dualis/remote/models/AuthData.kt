package de.fampopprol.dhbwhorb.data.dualis.remote.models

/**
 * Contains authentication data received from Dualis after successful login.
 * @property sessionId The session identifier (usually same as authToken)
 * @property authToken The authentication token extracted from redirect URLs
 * @property userFullName The user's full name extracted from the main page
 * @property cookie The raw cookie string (e.g. "cnsc=...") needed for requests
 */
data class AuthData(
    val sessionId: String = "",
    val authToken: String = "",
    val userFullName: String? = null,
    val cookie: String? = null
)