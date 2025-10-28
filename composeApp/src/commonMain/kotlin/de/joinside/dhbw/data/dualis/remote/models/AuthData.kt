package de.joinside.dhbw.data.dualis.remote.models

/**
 * Contains authentication data received from Dualis after successful login.
 * @property sessionId The session identifier from cookies
 * @property authToken The authentication token extracted from redirect URLs
 */
data class AuthData(
    val sessionId: String = "",
    val authToken: String = ""
)
