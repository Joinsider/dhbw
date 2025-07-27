/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import de.fampopprol.dhbwhorb.data.dualis.models.Module
import de.fampopprol.dhbwhorb.data.dualis.models.ExamState
import de.fampopprol.dhbwhorb.data.dualis.parser.StudyGradesParser

/**
 * Handles grades and semester operations for Dualis
 */
class DualisGradesService(
    private val networkClient: DualisNetworkClient,
    private val urlManager: DualisUrlManager,
    private val htmlParser: DualisHtmlParser,
    private val authService: DualisAuthenticationService
) {

    /**
     * Fetches available semesters from Dualis
     */
    fun getAvailableSemesters(callback: (List<Semester>?) -> Unit) {
        Log.d("DualisGradesService", "=== FETCHING AVAILABLE SEMESTERS ===")

        // Return demo data if in demo mode
        if (authService.isDemoMode) {
            Log.d("DualisGradesService", "Demo mode: returning demo semesters")
            callback(Semester.getDefaultSemesters())
            return
        }

        if (!urlManager.hasValidToken()) {
            Log.e("DualisGradesService", "Auth Token is null. Authentication required.")
            callback(null)
            return
        }

        val baseUrl = urlManager.buildSemesterUrl()
        if (baseUrl == null) {
            Log.e("DualisGradesService", "Could not build semester URL")
            callback(null)
            return
        }

        val request = networkClient.createGetRequest(baseUrl)
        Log.d("DualisGradesService", "Fetching semesters from: $baseUrl")

        networkClient.makeRequest(request, "Available Semesters") { _, responseBody ->
            if (responseBody != null) {
                try {
                    val semesters = htmlParser.parseSemestersFromHtml(responseBody)
                    Log.d("DualisGradesService", "Parsed ${semesters.size} semesters")
                    callback(if (semesters.isNotEmpty()) semesters else Semester.getDefaultSemesters())
                } catch (e: Exception) {
                    Log.e("DualisGradesService", "Error parsing semesters", e)
                    callback(Semester.getDefaultSemesters())
                }
            } else {
                callback(Semester.getDefaultSemesters())
            }
        }
    }

    /**
     * Fetches the study grades for a specific semester
     */
    fun getStudyGradesForSemester(semester: Semester, callback: (StudyGrades?) -> Unit) {
        val semesterArgument = Semester.formatSemesterArgument(semester.value)
        Log.d("DualisGradesService", "Fetching grades for semester: ${semester.displayName} with argument: $semesterArgument")
        getStudyGrades(semesterArgument, callback)
    }

    /**
     * Fetches the study grades (GPA and credits information) from Dualis for a specific semester
     */
    fun getStudyGrades(semesterArgument: String = "", callback: (StudyGrades?) -> Unit) {
        Log.d("DualisGradesService", "=== STARTING STUDY GRADES FETCH ===")
        Log.d("DualisGradesService", "Semester argument: $semesterArgument")

        // Return demo data if in demo mode
        if (authService.isDemoMode) {
            Log.d("DualisGradesService", "Demo mode: returning demo study grades data")
            callback(createDemoStudyGrades(semesterArgument))
            return
        }

        // Check authentication status
        if (!urlManager.hasValidToken()) {
            Log.e("DualisGradesService", "Auth Token is null. Authentication required.")
            callback(null)
            return
        }

        val baseUrl = urlManager.buildStudyGradesUrl(semesterArgument)
        if (baseUrl == null) {
            Log.e("DualisGradesService", "Could not build study grades URL")
            callback(null)
            return
        }

        Log.d("DualisGradesService", "Semester-specific URL: $baseUrl")

        val request = networkClient.createGetRequest(baseUrl)
        networkClient.makeRequest(request, "Study Grades") { _, responseBody ->
            if (responseBody != null) {
                // Check if the response indicates an invalid token
                if (htmlParser.isTokenInvalidResponse(responseBody)) {
                    Log.w("DualisGradesService", "Token appears to be invalid when fetching grades, attempting re-authentication")
                    authService.reAuthenticateIfNeeded { success ->
                        if (success) {
                            Log.d("DualisGradesService", "Re-authentication successful, retrying grades fetch")
                            getStudyGrades(semesterArgument, callback) // Retry after re-authentication
                        } else {
                            Log.e("DualisGradesService", "Re-authentication failed")
                            callback(null)
                        }
                    }
                    return@makeRequest
                }

                try {
                    Log.d("DualisGradesService", "=== STARTING HTML PARSING ===")
                    val parser = StudyGradesParser()
                    val studyGrades = parser.extractStudyGrades(responseBody, semesterArgument)

                    if (studyGrades != null) {
                        Log.d("DualisGradesService", "=== STUDY GRADES PARSING SUCCESSFUL ===")
                        Log.d("DualisGradesService", "GPA Total: ${studyGrades.gpaTotal}")
                        Log.d("DualisGradesService", "GPA Main Modules: ${studyGrades.gpaMainModules}")
                        Log.d("DualisGradesService", "Credits Total: ${studyGrades.creditsTotal}")
                        Log.d("DualisGradesService", "Credits Gained: ${studyGrades.creditsGained}")
                        Log.d("DualisGradesService", "Number of modules: ${studyGrades.modules.size}")
                        callback(studyGrades)
                    } else {
                        Log.e("DualisGradesService", "Failed to parse study grades")
                        callback(null)
                    }
                } catch (e: Exception) {
                    Log.e("DualisGradesService", "=== STUDY GRADES PARSING FAILED ===", e)
                    callback(null)
                }
            } else {
                Log.e("DualisGradesService", "Response body is null")
                callback(null)
            }
        }
    }

    private fun createDemoStudyGrades(semesterArgument: String): StudyGrades {
        return StudyGrades(
            gpaTotal = 1.7,
            gpaMainModules = 1.6,
            creditsTotal = 210.0,
            creditsGained = 180.0,
            modules = listOf(
                Module(
                    id = "T4_1000",
                    name = "Praxisprojekt I",
                    credits = "20.0",
                    grade = "1.3",
                    state = ExamState.PASSED
                ),
                Module(
                    id = "T4INF1003",
                    name = "Theoretische Informatik II",
                    credits = "5.0",
                    grade = "noch nicht gesetzt",
                    state = ExamState.PENDING
                )
            ),
            semester = if (semesterArgument.isEmpty()) "current" else "previous"
        )
    }
}
