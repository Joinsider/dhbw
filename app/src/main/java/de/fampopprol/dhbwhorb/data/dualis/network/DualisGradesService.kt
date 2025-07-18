/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import de.fampopprol.dhbwhorb.data.dualis.parser.StudyGradesParser
import okhttp3.*
import org.jsoup.Jsoup
import java.io.IOException

/**
 * Handles grade and semester related operations
 */
class DualisGradesService(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "DualisGradesService"
    }

    fun getAvailableSemesters(authToken: String, callback: (List<Semester>?) -> Unit) {
        Log.d(TAG, "Fetching available semesters")

        val baseUrl = "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N$authToken,-N000307,"
        val request = Request.Builder().url(baseUrl).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Get available semesters request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body.string()
                Log.d(TAG, "Semesters response received: ${response.code}")

                if (response.isSuccessful) {
                    try {
                        val semesters = parseSemestersFromHtml(responseBody)
                        callback(semesters)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing semesters", e)
                        callback(Semester.getDefaultSemesters())
                    }
                } else {
                    Log.e(TAG, "Get semesters failed with code: ${response.code}")
                    callback(Semester.getDefaultSemesters())
                }
            }
        })
    }

    fun getStudyGrades(
        authToken: String,
        semesterArgument: String = "",
        callback: (StudyGrades?) -> Unit
    ) {
        Log.d(TAG, "Fetching study grades for semester: $semesterArgument")

        val baseUrl = "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N$authToken,-N000307$semesterArgument"
        val request = Request.Builder().url(baseUrl).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Get study grades request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body.string()
                Log.d(TAG, "Study grades response received: ${response.code}")

                if (response.isSuccessful) {
                    try {
                        val parser = StudyGradesParser()
                        val studyGrades = parser.extractStudyGrades(responseBody, semesterArgument)
                        callback(studyGrades)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing study grades", e)
                        callback(null)
                    }
                } else {
                    Log.e(TAG, "Get study grades failed with code: ${response.code}")
                    callback(null)
                }
            }
        })
    }

    private fun parseSemestersFromHtml(html: String): List<Semester> {
        Log.d(TAG, "Parsing semesters from HTML")

        val document = Jsoup.parse(html)
        val semesters = mutableListOf<Semester>()

        val semesterSelect = document.select("select#semester").first()

        if (semesterSelect != null) {
            val options = semesterSelect.select("option")

            options.forEach { option ->
                val value = option.attr("value")
                val displayName = option.text().trim()
                val isSelected = option.hasAttr("selected")

                if (value.isNotEmpty() && displayName.isNotEmpty()) {
                    semesters.add(Semester(value, displayName, isSelected))
                }
            }
        }

        return if (semesters.isEmpty()) {
            Log.w(TAG, "No semesters found, using defaults")
            Semester.getDefaultSemesters()
        } else {
            semesters
        }
    }
}
