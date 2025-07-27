/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.DualisUrl

/**
 * Manages URL construction and token operations for Dualis
 */
class DualisUrlManager {

    private val tokenRegex = Regex("ARGUMENTS=-N([0-9]{15})")
    private var authToken: String? = null
    val dualisUrls: DualisUrl = DualisUrl()

    /**
     * Updates the auth token from a URL containing the token
     */
    fun updateAuthToken(urlWithNewToken: String) {
        val tokenMatch = tokenRegex.find(urlWithNewToken)
        if (tokenMatch != null) {
            authToken = tokenMatch.groupValues[1]
            Log.d("DualisUrlManager", "Updated Auth Token: $authToken")
        } else {
            Log.e("DualisUrlManager", "Auth token not found in URL: $urlWithNewToken")
        }
    }

    /**
     * Fills a URL with the current auth token
     */
    fun fillUrlWithAuthToken(url: String): String {
        val match = tokenRegex.find(url)
        return if (match != null && authToken != null) {
            val newUrl = url.replaceRange(match.range.first, match.range.last, "ARGUMENTS=-N$authToken")
            Log.d("DualisUrlManager", "Filled URL with Auth Token: $newUrl")
            newUrl
        } else {
            Log.w("DualisUrlManager", "Could not fill URL with auth token. URL: $url, AuthToken: $authToken")
            url
        }
    }

    /**
     * Gets the current auth token
     */
    fun getAuthToken(): String? = authToken

    /**
     * Checks if we have a valid auth token
     */
    fun hasValidToken(): Boolean = authToken != null

    /**
     * Constructs study grades URL with optional semester argument
     */
    fun buildStudyGradesUrl(semesterArgument: String = ""): String? {
        return if (authToken != null) {
            "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N$authToken,-N000307$semesterArgument"
        } else {
            null
        }
    }

    /**
     * Constructs schedule URL with date parameter
     */
    fun buildScheduleUrl(baseUrl: String, formattedDate: String): String {
        val argumentsRegex = Regex("ARGUMENTS=([^&]+)")
        val existingArgumentsMatch = argumentsRegex.find(baseUrl)
        val existingArguments = existingArgumentsMatch?.groupValues?.get(1) ?: ""

        val updatedArguments = existingArguments.replaceFirst("-A", "-A$formattedDate")

        return baseUrl.replace(existingArguments, updatedArguments).replace(
            "ARGUMENTS=-N${authToken ?: ""}", "ARGUMENTS=-N${authToken ?: ""}"
        )
    }

    /**
     * Constructs semester fetch URL
     */
    fun buildSemesterUrl(): String? {
        return if (authToken != null) {
            "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N$authToken,-N000307,"
        } else {
            null
        }
    }

    /**
     * Clears all stored data
     */
    fun clear() {
        authToken = null
        dualisUrls.mainPageUrl = null
        dualisUrls.logoutUrl = null
        dualisUrls.studentResultsUrl = null
        dualisUrls.courseResultUrl = null
        dualisUrls.monthlyScheduleUrl = null
        dualisUrls.semesterCourseResultUrls.clear()
    }
}
