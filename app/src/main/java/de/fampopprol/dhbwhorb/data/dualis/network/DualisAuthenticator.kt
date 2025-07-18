/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import okhttp3.*
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URL

/**
 * Handles authentication with the Dualis system
 */
class DualisAuthenticator(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "DualisAuthenticator"
        private const val DUALIS_BASE_URL = "https://dualis.dhbw.de"
        private const val LOGIN_URL = "$DUALIS_BASE_URL/scripts/mgrqispi.dll"
    }

    private val tokenRegex = Regex("ARGUMENTS=-N([0-9]{15})")

    fun login(username: String, password: String, callback: (AuthResult) -> Unit) {
        Log.d(TAG, "Starting login process for user: $username")

        val formBody = createLoginFormBody(username, password)
        val request = Request.Builder()
            .url(LOGIN_URL)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Login request failed", e)
                callback(AuthResult.Error(e.message ?: "Login request failed"))
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Login response received: ${response.code}")

                if (response.isSuccessful) {
                    handleLoginResponse(response, callback)
                } else {
                    Log.e(TAG, "Login failed with code: ${response.code}")
                    callback(AuthResult.Error("Login failed with status: ${response.code}"))
                }
            }
        })
    }

    private fun createLoginFormBody(username: String, password: String): FormBody {
        return FormBody.Builder()
            .add("usrname", username)
            .add("pass", password)
            .add("APPNAME", "CampusNet")
            .add("PRGNAME", "LOGINCHECK")
            .add("ARGUMENTS", "clino,usrname,pass,menuno,menu_type,browser,platform")
            .add("clino", "000000000000001")
            .add("menuno", "000324")
            .add("menu_type", "classic")
            .add("browser", "")
            .add("platform", "")
            .build()
    }

    private fun handleLoginResponse(response: Response, callback: (AuthResult) -> Unit) {
        val redirectUrlHeader = response.header("refresh")
        if (redirectUrlHeader == null) {
            callback(AuthResult.Error("No redirect URL found in login response"))
            return
        }

        val redirectUrl = extractRedirectUrl(redirectUrlHeader)
        val authToken = extractAuthToken(redirectUrl)
        if (authToken == null) {
            callback(AuthResult.Error("Could not extract auth token from redirect"))
            return
        }

        followRedirects(redirectUrl) { mainPageContent ->
            if (mainPageContent != null) {
                callback(AuthResult.Success(authToken, mainPageContent))
            } else {
                callback(AuthResult.Error("Failed to retrieve main page content"))
            }
        }
    }

    private fun extractRedirectUrl(redirectHeader: String): String {
        val redirectUrlPart = if (redirectHeader.contains("URL=")) {
            redirectHeader.substring(redirectHeader.indexOf("URL=") + "URL=".length)
        } else {
            redirectHeader
        }
        return makeAbsoluteUrl(DUALIS_BASE_URL, redirectUrlPart)
    }

    private fun extractAuthToken(url: String): String? {
        val tokenMatch = tokenRegex.find(url)
        return tokenMatch?.groupValues?.get(1)
    }

    private fun followRedirects(url: String, callback: (String?) -> Unit) {
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Follow redirects request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body.string()

                if (response.isSuccessful) {
                    handleRedirectResponse(url, responseBody, callback)
                } else {
                    Log.e(TAG, "Follow redirects failed with code: ${response.code}")
                    callback(null)
                }
            }
        })
    }

    private fun handleRedirectResponse(currentUrl: String, responseBody: String, callback: (String?) -> Unit) {
        val document = Jsoup.parse(responseBody)
        val isRedirectPage = document.select("div#sessionId").first() != null

        if (isRedirectPage) {
            val nextRedirectUrl = extractNextRedirectUrl(document, currentUrl)
            if (nextRedirectUrl != null) {
                followRedirects(nextRedirectUrl, callback)
            } else {
                callback(null)
            }
        } else if (isMainPage(responseBody)) {
            callback(responseBody)
        } else {
            Log.e(TAG, "Unexpected page content")
            callback(null)
        }
    }

    private fun extractNextRedirectUrl(document: org.jsoup.nodes.Document, currentUrl: String): String? {
        // Try to get from script first
        for (element in document.select("script")) {
            val content = element.html()
            if (content.contains("window.location.href")) {
                val regex = Regex("window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]")
                val match = regex.find(content)
                val relativeUrl = match?.groupValues?.get(1)
                if (relativeUrl != null) {
                    return makeAbsoluteUrl(currentUrl, relativeUrl)
                }
            }
        }

        // If not found in script, try from the <a> tag
        val anchorElement = document.select("h2 a[href]").first()
        val relativeUrl = anchorElement?.attr("href")
        if (relativeUrl != null) {
            return makeAbsoluteUrl(currentUrl, relativeUrl)
        }

        return null
    }

    private fun makeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = URL(baseUrl)
            URL(base, relativeUrl).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error making absolute URL", e)
            ""
        }
    }

    private fun isMainPage(html: String): Boolean {
        val document = Jsoup.parse(html)
        return document.select("a:contains(Studienleistungen)").first() != null ||
               document.select("a:contains(Prüfungsergebnisse)").first() != null ||
               document.select("a:contains(Stundenplan)").first() != null ||
               document.select("a:contains(Abmelden)").first() != null
    }

    sealed class AuthResult {
        data class Success(val authToken: String, val mainPageContent: String) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }
}
