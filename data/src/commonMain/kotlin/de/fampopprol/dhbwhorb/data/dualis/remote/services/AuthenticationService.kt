/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.AuthParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.error.httpStatusToAppError
import de.fampopprol.dhbwhorb.data.error.toAppError
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.net.ClearableCookiesStorage
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess

/**
 * Logs in to Dualis: submits the form, follows the redirect chain to the main page, and stores
 * the resulting session.
 *
 * The [client] is shared with [de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient] so the
 * session cookie survives into every later request.
 *
 * Re-authentication is not here: it belongs to
 * [de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator], which makes concurrent
 * callers share a single attempt.
 */
open class AuthenticationService(
    val sessionManager: SessionManager,
    private val client: HttpClient,
    private val cookiesStorage: ClearableCookiesStorage? = null,
    private val authParser: AuthParser = AuthParser(),
    private val htmlParser: HtmlParser = HtmlParser()
) {

    companion object {
        private const val TAG = "AuthenticationService"
        private const val LOGIN_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val MAX_REDIRECT_DEPTH = 10
        private const val SOURCE = "login"
    }

    /**
     * Log in with [username] and [password] and store the session on success.
     *
     * The demo account never leaves the device: it short-circuits before the first request.
     */
    open suspend fun login(username: String, password: String): Outcome<Session> {
        Napier.d("Starting login for $username", tag = TAG)

        if (sessionManager.isDemoUser(username, password)) {
            Napier.d("Demo user detected, enabling demo mode", tag = TAG)
            sessionManager.setDemoMode(true)
            sessionManager.storeCredentials(username, password)
            return Outcome.Ok(Session(userFullName = null, isDemo = true))
        }

        sessionManager.setDemoMode(false)

        val response: HttpResponse = try {
            client.submitForm(
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
        } catch (e: Exception) {
            Napier.e("Login request failed: ${e.message}", e, tag = TAG)
            return Outcome.Err(e.toAppError(SOURCE))
        }

        val responseBody = try {
            response.bodyAsText()
        } catch (e: Exception) {
            return Outcome.Err(e.toAppError(SOURCE))
        }

        if (!response.status.isSuccess()) {
            Napier.e("Login request failed with status: ${response.status}", tag = TAG)
            return Outcome.Err(httpStatusToAppError(response.status.value))
        }

        // Dualis answers a rejected login with 200 and the login form again, so the credentials
        // verdict has to be read out of the body.
        val rejected = responseBody.contains("LOGINCHECK", ignoreCase = true) ||
            responseBody.contains("Anmeldung fehlgeschlagen", ignoreCase = true)
        if (rejected) {
            Napier.e("Login rejected - invalid credentials", tag = TAG)
            return Outcome.Err(AppError.InvalidCredentials)
        }

        // The Set-Cookie header is read by hand: Dualis' cookie format is loose enough that the
        // HttpCookies plugin does not always keep it.
        val cookieHeader = response.headers["set-cookie"]

        val redirectHeader = response.headers["refresh"]
            ?: return parseFailure("no refresh header on the login response")

        val redirectUrl = authParser.extractRedirectUrlFromHeader(redirectHeader)
            ?: return parseFailure("refresh header carried no URL: $redirectHeader")

        val authToken = authParser.extractAuthToken(redirectUrl)
        if (authToken == null) Napier.w("Could not extract auth token from redirect URL", tag = TAG)

        val mainPage = when (val result = followRedirects(redirectUrl)) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> return result
        }

        val userFullName = htmlParser.extractUserFullName(mainPage)
        if (userFullName == null) {
            Napier.w("Main page reached but the user's name was not in it", tag = TAG)
        }

        // For Dualis the auth token doubles as the session identifier; the cookie is only a
        // fallback for the rare responses that do set JSESSIONID.
        val sessionId = authToken ?: extractSessionId() ?: ""
        if (sessionId.isEmpty()) {
            return parseFailure("neither an auth token nor a session cookie was returned")
        }

        val authData = AuthData(
            sessionId = sessionId,
            authToken = authToken ?: "",
            userFullName = userFullName,
            cookie = cookieHeader
        )
        sessionManager.storeAuthData(authData)
        sessionManager.storeCredentials(username, password)

        Napier.d("Login completed successfully", tag = TAG)
        return Outcome.Ok(Session(userFullName = userFullName, isDemo = false))
    }

    /** Follow the chain of interstitial redirect pages until the main page appears. */
    private suspend fun followRedirects(startUrl: String, depth: Int = 0): Outcome<String> {
        if (depth >= MAX_REDIRECT_DEPTH) {
            return parseFailure("more than $MAX_REDIRECT_DEPTH redirects after login")
        }

        val responseBody = try {
            client.get(startUrl).bodyAsText()
        } catch (e: Exception) {
            Napier.e("Error following redirect: ${e.message}", e, tag = TAG)
            return Outcome.Err(e.toAppError(SOURCE))
        }

        return when {
            htmlParser.isMainPage(responseBody) -> Outcome.Ok(responseBody)

            htmlParser.isRedirectPage(responseBody) -> {
                val nextUrl = authParser.extractRedirectUrlFromHtml(responseBody, startUrl)
                    ?: return parseFailure("a redirect page carried no follow-up URL")
                followRedirects(nextUrl, depth + 1)
            }

            // Not the main page and not a redirect: with valid credentials this is what a
            // rejected session looks like, so it is reported as such rather than as a parse
            // failure the user cannot act on.
            else -> {
                val title = htmlParser.extractTitle(responseBody)
                Napier.e("Unexpected page after login, title: $title", tag = TAG)
                Outcome.Err(AppError.SessionExpired)
            }
        }
    }

    /** The session cookie, if Dualis set one. Usually it does not. */
    private suspend fun extractSessionId(): String? {
        val cookies = client.cookies("https://dualis.dhbw.de")
        return cookies.find { it.name == "JSESSIONID" || it.name == "cnsc" }?.value
    }

    open fun isAuthenticated(): Boolean = sessionManager.isAuthenticated()

    open suspend fun logout() {
        Napier.d("Logging out", tag = TAG)
        sessionManager.logout()

        // The session cookie is held by the client, not by the session manager. Leaving it there
        // meant the next login started by presenting the previous account's cookie.
        cookiesStorage?.clear()
    }

    fun close() {
        client.close()
    }

    private fun parseFailure(hint: String): Outcome<Nothing> {
        Napier.e("Login could not be completed: $hint", tag = TAG)
        return Outcome.Err(AppError.Parse(source = SOURCE, hint = hint))
    }
}
