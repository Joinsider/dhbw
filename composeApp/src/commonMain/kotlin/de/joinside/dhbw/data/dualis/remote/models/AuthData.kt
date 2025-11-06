package de.joinside.dhbw.data.dualis.remote.models

/**
 * Contains authentication data received from Dualis after successful login.
 * @property sessionId The session identifier from cookies
 * @property authToken The authentication token extracted from redirect URLs
 * @property userFullName The user's full name extracted from the main page
 */
data class AuthData(
    val sessionId: String = "",
    val authToken: String = "",
    val userFullName: String? = null
)
