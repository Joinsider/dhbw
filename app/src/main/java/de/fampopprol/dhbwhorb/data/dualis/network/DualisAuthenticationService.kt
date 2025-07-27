/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.demo.DemoDataProvider
import okhttp3.FormBody
import okhttp3.Response

/**
 * Handles authentication operations for Dualis
 */
class DualisAuthenticationService(
    private val networkClient: DualisNetworkClient,
    private val urlManager: DualisUrlManager,
    private val htmlParser: DualisHtmlParser
) {

    private var lastLoginCredentials: Pair<String, String>? = null
    private var isReAuthenticating = false
    var isDemoMode = false
        private set

    /**
     * Performs login to Dualis
     */
    fun login(user: String, pass: String, callback: (String?) -> Unit) {
        Log.d("DualisAuthenticationService", "=== STARTING LOGIN PROCESS ===")
        Log.d("DualisAuthenticationService", "Username: $user")
        Log.d("DualisAuthenticationService", "Password length: ${pass.length}")

        // Check if this is a demo user
        if (DemoDataProvider.isDemoUser(user) && pass == DemoDataProvider.DEMO_PASSWORD) {
            Log.d("DualisAuthenticationService", "Demo user detected, enabling demo mode")
            isDemoMode = true
            lastLoginCredentials = Pair(user, pass)
            callback("Demo login successful")
            return
        }

        // Reset demo mode for regular users
        isDemoMode = false

        // Store credentials for potential re-authentication
        lastLoginCredentials = Pair(user, pass)

        val formBody = FormBody.Builder()
            .add("usrname", user)
            .add("pass", pass)
            .add("APPNAME", "CampusNet")
            .add("PRGNAME", "LOGINCHECK")
            .add("ARGUMENTS", "clino,usrname,pass,menuno,menu_type,browser,platform")
            .add("clino", "000000000000001")
            .add("menuno", "000324")
            .add("menu_type", "classic")
            .add("browser", "")
            .add("platform", "")
            .build()

        val request = networkClient.createPostRequest("https://dualis.dhbw.de/scripts/mgrqispi.dll", formBody)

        networkClient.makeRequest(request, "Login") { response, responseBody ->
            if (response != null && responseBody != null) {
                handleLoginResponse(response, responseBody, callback)
            } else {
                callback(null)
            }
        }
    }

    /**
     * Handles the login response and follows redirects
     */
    private fun handleLoginResponse(response: Response, responseBody: String, callback: (String?) -> Unit) {
        Log.d("DualisAuthenticationService", "=== LOGIN RESPONSE RECEIVED ===")
        Log.d("DualisAuthenticationService", "Response code: ${response.code}")

        val redirectUrlHeader = response.header("refresh")
        if (redirectUrlHeader != null) {
            val dualisEndpoint = "https://dualis.dhbw.de"
            val redirectUrlPart = if (redirectUrlHeader.contains("URL=")) {
                redirectUrlHeader.substring(redirectUrlHeader.indexOf("URL=") + "URL=".length)
            } else {
                redirectUrlHeader
            }

            val absoluteRedirectUrl = networkClient.makeAbsoluteUrl(dualisEndpoint, redirectUrlPart)
            urlManager.updateAuthToken(absoluteRedirectUrl)

            followRedirects(absoluteRedirectUrl) { realMainPageContent ->
                if (realMainPageContent != null) {
                    try {
                        val parsedUrls = htmlParser.parseMainPage(realMainPageContent, urlManager.getAuthToken())
                        // Update the URL manager with parsed URLs
                        urlManager.dualisUrls.studentResultsUrl = parsedUrls.studentResultsUrl
                        urlManager.dualisUrls.courseResultUrl = parsedUrls.courseResultUrl
                        urlManager.dualisUrls.monthlyScheduleUrl = parsedUrls.monthlyScheduleUrl
                        urlManager.dualisUrls.logoutUrl = parsedUrls.logoutUrl

                        Log.d("DualisAuthenticationService", "Login process completed successfully")
                        callback("Login successful")
                    } catch (e: Exception) {
                        Log.e("DualisAuthenticationService", "Error parsing real main page", e)
                        callback(null)
                    }
                } else {
                    Log.e("DualisAuthenticationService", "Real main page content is null after following redirects")
                    callback(null)
                }
            }
        } else {
            Log.e("DualisAuthenticationService", "Redirect URL is null")
            callback(null)
        }
    }

    /**
     * Follows redirect chain until reaching the main page
     */
    private fun followRedirects(url: String, callback: (String?) -> Unit) {
        val request = networkClient.createGetRequest(url)

        networkClient.makeRequest(request, "Follow Redirects") { response, responseBody ->
            if (response != null && responseBody != null) {
                val isRedirectPage = htmlParser.isRedirectPage(responseBody)
                val isMainPage = htmlParser.isMainPage(responseBody)

                when {
                    isRedirectPage -> {
                        val nextRedirectUrl = htmlParser.extractRedirectUrl(responseBody, url)
                        if (nextRedirectUrl != null) {
                            Log.d("DualisAuthenticationService", "Following redirect to: $nextRedirectUrl")
                            followRedirects(nextRedirectUrl, callback) // Recursive call
                        } else {
                            Log.e("DualisAuthenticationService", "Could not find next redirect URL in redirect page")
                            callback(null)
                        }
                    }
                    isMainPage -> {
                        callback(responseBody)
                    }
                    else -> {
                        Log.e("DualisAuthenticationService", "Unexpected page content, not a main page or known redirect page")
                        callback(null)
                    }
                }
            } else {
                callback(null)
            }
        }
    }

    /**
     * Re-authenticates using stored credentials if needed
     */
    fun reAuthenticateIfNeeded(callback: (Boolean) -> Unit) {
        if (isReAuthenticating) {
            Log.w("DualisAuthenticationService", "Re-authentication already in progress")
            callback(false)
            return
        }

        val credentials = lastLoginCredentials
        if (credentials == null) {
            Log.e("DualisAuthenticationService", "No stored credentials for re-authentication")
            callback(false)
            return
        }

        Log.d("DualisAuthenticationService", "Starting re-authentication process")
        isReAuthenticating = true

        login(credentials.first, credentials.second) { result ->
            isReAuthenticating = false
            val success = result != null
            Log.d("DualisAuthenticationService", "Re-authentication ${if (success) "successful" else "failed"}")
            callback(success)
        }
    }

    /**
     * Checks if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return urlManager.hasValidToken() || isDemoMode
    }

    /**
     * Logs out and clears all stored data
     */
    fun logout() {
        lastLoginCredentials = null
        isDemoMode = false
        isReAuthenticating = false
        urlManager.clear()
    }
}
