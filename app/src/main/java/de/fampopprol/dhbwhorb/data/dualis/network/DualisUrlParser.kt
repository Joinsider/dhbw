/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.DualisUrl
import org.jsoup.Jsoup

/**
 * Parses the main page content to extract Dualis URLs
 */
class DualisUrlParser {

    companion object {
        private const val TAG = "DualisUrlParser"
        private const val DUALIS_BASE_URL = "https://dualis.dhbw.de"
    }

    fun parseMainPage(html: String, authToken: String): DualisUrl {
        Log.d(TAG, "Parsing main page for URLs")

        val document = Jsoup.parse(html)
        val dualisUrls = DualisUrl()

        // Log all links for debugging
        logAllLinks(document)

        // Extract URLs
        dualisUrls.studentResultsUrl = buildStudentResultsUrl(authToken)
        dualisUrls.courseResultUrl = extractCourseResultUrl(document)
        dualisUrls.monthlyScheduleUrl = extractScheduleUrl(document)
        dualisUrls.logoutUrl = extractLogoutUrl(document)

        logExtractedUrls(dualisUrls)

        return dualisUrls
    }

    private fun logAllLinks(document: org.jsoup.nodes.Document) {
        val allLinks = document.select("a")
        Log.d(TAG, "Found ${allLinks.size} total links in main page")

        allLinks.forEachIndexed { index, link ->
            val href = link.attr("href")
            val text = link.text().trim()
            Log.d(TAG, "Link $index: text='$text', href='$href'")
        }
    }

    private fun buildStudentResultsUrl(authToken: String): String {
        return "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N$authToken,-N000307,"
    }

    private fun extractCourseResultUrl(document: org.jsoup.nodes.Document): String? {
        Log.d(TAG, "Searching for course results URL")

        val courseResultElement = document.select("a:contains(Prüfungsergebnisse)").first()

        if (courseResultElement != null) {
            val rawHref = courseResultElement.attr("href")
            val url = if (rawHref.startsWith("/")) {
                DUALIS_BASE_URL + rawHref
            } else {
                rawHref
            }
            Log.d(TAG, "Found course results URL: $url")
            return url
        } else {
            Log.w(TAG, "No course results URL found")
            return null
        }
    }

    private fun extractScheduleUrl(document: org.jsoup.nodes.Document): String? {
        Log.d(TAG, "Searching for schedule URL")

        val scheduleElement = document.select("a:contains(diese Woche)").first()

        if (scheduleElement != null) {
            val rawHref = scheduleElement.attr("href")
            val url = if (rawHref.startsWith("/")) {
                DUALIS_BASE_URL + rawHref
            } else {
                rawHref
            }
            Log.d(TAG, "Found schedule URL: $url")
            return url
        } else {
            Log.w(TAG, "No schedule URL found, trying alternatives")
            return tryAlternativeScheduleSearch(document)
        }
    }

    private fun tryAlternativeScheduleSearch(document: org.jsoup.nodes.Document): String? {
        val scheduleSearchTerms = listOf("Stundenplan", "Woche", "Schedule", "Kalender")

        return scheduleSearchTerms.firstNotNullOfOrNull { searchTerm ->
            val element = document.select("a:contains($searchTerm)").firstOrNull()
            Log.d(TAG, "Alternative search for '$searchTerm' found ${element != null}")

            element?.attr("href")?.takeIf { it.isNotEmpty() }?.let { rawHref ->
                val url = if (rawHref.startsWith("/")) DUALIS_BASE_URL + rawHref else rawHref
                Log.d(TAG, "Found alternative schedule URL: $url")
                url
            }
        }
    }

    private fun extractLogoutUrl(document: org.jsoup.nodes.Document): String? {
        Log.d(TAG, "Searching for logout URL")

        val logoutElement = document.select("a:contains(Abmelden)").first()

        if (logoutElement != null) {
            val rawHref = logoutElement.attr("href")
            val url = if (rawHref.startsWith("/")) {
                DUALIS_BASE_URL + rawHref
            } else {
                rawHref
            }
            Log.d(TAG, "Found logout URL: $url")
            return url
        } else {
            Log.w(TAG, "No logout URL found")
            return null
        }
    }

    private fun logExtractedUrls(dualisUrls: DualisUrl) {
        Log.d(TAG, "=== EXTRACTED DUALIS URLS ===")
        Log.d(TAG, "Student Results URL: ${dualisUrls.studentResultsUrl}")
        Log.d(TAG, "Course Results URL: ${dualisUrls.courseResultUrl}")
        Log.d(TAG, "Monthly Schedule URL: ${dualisUrls.monthlyScheduleUrl}")
        Log.d(TAG, "Logout URL: ${dualisUrls.logoutUrl}")
    }
}
