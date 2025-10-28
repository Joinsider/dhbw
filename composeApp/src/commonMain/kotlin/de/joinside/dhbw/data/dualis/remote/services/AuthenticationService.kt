package de.joinside.dhbw.data.dualis.remote.services

import de.joinside.dhbw.data.dualis.remote.models.AuthData
import de.joinside.dhbw.data.dualis.remote.parser.AuthParser
import de.joinside.dhbw.data.dualis.remote.parser.HtmlParser
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess

/**
 * Service for authenticating with Dualis.
 * Handles login, redirect following, session management, and re-authentication.
 */
class AuthenticationService(
    val sessionManager: SessionManager,
    private val authParser: AuthParser = AuthParser(),
    private val htmlParser: HtmlParser = HtmlParser(),
    private val client: HttpClient = HttpClient {
        expectSuccess = false
        install(HttpCookies)
    }
) {

    companion object {
        private const val TAG = "AuthenticationService"
        private const val LOGIN_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val MAX_REDIRECT_DEPTH = 10
    }

    /**
     * Perform login with username and password.
     * @return LoginResult indicating success with auth data or failure
     */
    suspend fun login(username: String, password: String): LoginResult {
        Napier.d("=== STARTING LOGIN PROCESS ===", tag = TAG)
        Napier.d("Username: $username", tag = TAG)
        Napier.d("Password length: ${password.length}", tag = TAG)

        // Check for demo user
        if (sessionManager.isDemoUser(username, password)) {
            Napier.d("Demo user detected, enabling demo mode", tag = TAG)
            sessionManager.setDemoMode(true)
            sessionManager.storeCredentials(username, password)
            return LoginResult.Success(AuthData())
        }

        sessionManager.setDemoMode(false)

        return try {
            // Submit login form
            val response: HttpResponse = client.submitForm(
                url = LOGIN_URL,
                formParameters = Parameters.build {
                    append("usrname", username)
                    append("pass", password)
                    append("APPNAME", "CampusNet")
                    append("PRGNAME", "LOGINCHECK")
                    append("ARGUMENTS", "clino,usrname,pass,menuno,menu_type,browser,platform")
                    append("clino", "000000000000001")
                    append("menuno", "000324")
                    append("menu_type", "classic")
                    append("browser", "")
                    append("platform", "")
                }
            )

            val responseBody = response.bodyAsText()
            Napier.d("Response status: ${response.status}", tag = TAG)

            if (!response.status.isSuccess()) {
                Napier.e("Login request failed with status: ${response.status}", tag = TAG)
                return LoginResult.Failure("Login request failed")
            }

            // Check for login errors
            if (responseBody.contains("LOGINCHECK", ignoreCase = true) ||
                responseBody.contains("Anmeldung fehlgeschlagen", ignoreCase = true)) {
                Napier.e("Login failed - invalid credentials", tag = TAG)
                return LoginResult.Failure("Invalid username or password")
            }

            // Extract redirect URL from refresh header
            val redirectHeader = response.headers["refresh"]
            if (redirectHeader == null) {
                Napier.e("No redirect header found in login response", tag = TAG)
                return LoginResult.Failure("No redirect URL found")
            }

            val redirectUrl = authParser.extractRedirectUrlFromHeader(redirectHeader)
            if (redirectUrl == null) {
                Napier.e("Could not extract redirect URL from header", tag = TAG)
                return LoginResult.Failure("Invalid redirect URL")
            }

            // Extract auth token from redirect URL
            val authToken = authParser.extractAuthToken(redirectUrl)
            if (authToken == null) {
                Napier.w("Could not extract auth token from redirect URL", tag = TAG)
            }

            // Follow redirects to reach main page
            val mainPageResult = followRedirects(redirectUrl)
            if (mainPageResult !is RedirectResult.Success) {
                Napier.e("Failed to follow redirects to main page", tag = TAG)
                return LoginResult.Failure("Failed to reach main page")
            }

            // Extract session ID from cookies
            val sessionId = extractSessionId()
            Napier.d("Session ID: ${sessionId?.take(10)}...", tag = TAG)

            // Create and store auth data
            val authData = AuthData(
                sessionId = sessionId ?: "",
                authToken = authToken ?: ""
            )

            sessionManager.storeAuthData(authData)
            sessionManager.storeCredentials(username, password)

            Napier.d("Login completed successfully", tag = TAG)
            LoginResult.Success(authData)

        } catch (e: Exception) {
            Napier.e("Login failed with exception: ${e.message}", e, tag = TAG)
            LoginResult.Failure("Login failed: ${e.message}")
        }
    }

    /**
     * Follow redirect chain until reaching the main page.
     */
    private suspend fun followRedirects(startUrl: String, depth: Int = 0): RedirectResult {
        if (depth >= MAX_REDIRECT_DEPTH) {
            Napier.e("Maximum redirect depth reached", tag = TAG)
            return RedirectResult.Failure("Too many redirects")
        }

        Napier.d("Following redirect [$depth]: $startUrl", tag = TAG)

        return try {
            val response: HttpResponse = client.get(startUrl)
            val responseBody = response.bodyAsText()

            when {
                htmlParser.isMainPage(responseBody) -> {
                    Napier.d("Reached main page", tag = TAG)
                    RedirectResult.Success(responseBody)
                }
                htmlParser.isRedirectPage(responseBody) -> {
                    val nextUrl = authParser.extractRedirectUrlFromHtml(responseBody, startUrl)
                    if (nextUrl == null) {
                        Napier.e("Could not extract next redirect URL", tag = TAG)
                        RedirectResult.Failure("Invalid redirect page")
                    } else {
                        followRedirects(nextUrl, depth + 1)
                    }
                }
                else -> {
                    Napier.e("Unexpected page content", tag = TAG)
                    val title = htmlParser.extractTitle(responseBody)
                    Napier.d("Page title: $title", tag = TAG)
                    RedirectResult.Failure("Unexpected page content")
                }
            }
        } catch (e: Exception) {
            Napier.e("Error following redirect: ${e.message}", e, tag = TAG)
            RedirectResult.Failure("Redirect failed: ${e.message}")
        }
    }

    /**
     * Extract session ID from response cookies.
     */
    private suspend fun extractSessionId(): String? {
        val cookies = client.cookies("https://dualis.dhbw.de")
        val sessionCookie = cookies.find { it.name == "JSESSIONID" || it.name == "cnsc" }
        return sessionCookie?.value
    }

    /**
     * Re-authenticate using stored credentials if needed.
     */
    suspend fun reAuthenticateIfNeeded(): Boolean {
        if (sessionManager.isReAuthenticating()) {
            Napier.w("Re-authentication already in progress", tag = TAG)
            return false
        }

        val credentials = sessionManager.getStoredCredentials()
        if (credentials == null) {
            Napier.e("No stored credentials for re-authentication", tag = TAG)
            return false
        }

        Napier.d("Starting re-authentication process", tag = TAG)
        sessionManager.setReAuthenticating(true)

        val result = login(credentials.first, credentials.second)
        sessionManager.setReAuthenticating(false)

        val success = result is LoginResult.Success
        Napier.d("Re-authentication ${if (success) "successful" else "failed"}", tag = TAG)
        return success
    }

    /**
     * Check if user is authenticated.
     */
    fun isAuthenticated(): Boolean {
        return sessionManager.isAuthenticated()
    }

    /**
     * Logout and clear all session data.
     */
    fun logout() {
        Napier.d("Logging out", tag = TAG)
        sessionManager.logout()
    }

    /**
     * Close the HTTP client.
     */
    fun close() {
        client.close()
    }
}

/**
 * Result of a login attempt.
 */
sealed class LoginResult {
    data class Success(val authData: AuthData) : LoginResult()
    data class Failure(val message: String) : LoginResult()
}

/**
 * Result of following redirects.
 */
private sealed class RedirectResult {
    data class Success(val htmlContent: String) : RedirectResult()
    data class Failure(val message: String) : RedirectResult()
}
