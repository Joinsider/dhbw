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

    private val maxRateLimitRetries = 3

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
            // Return default semesters as fallback instead of null
            Log.w("DualisGradesService", "Falling back to default semesters due to authentication issue")
            callback(Semester.getDefaultSemesters())
            return
        }

        val baseUrl = urlManager.buildSemesterUrl()
        if (baseUrl == null) {
            Log.e("DualisGradesService", "Could not build semester URL")
            // Return default semesters as fallback instead of null
            Log.w("DualisGradesService", "Falling back to default semesters due to URL building issue")
            callback(Semester.getDefaultSemesters())
            return
        }

        val request = networkClient.createGetRequest(baseUrl)
        Log.d("DualisGradesService", "Fetching dynamic semesters from: $baseUrl")

        networkClient.makeRequest(request, "Available Semesters") { _, responseBody ->
            if (responseBody != null) {
                try {
                    // Check if response indicates invalid token first
                    if (htmlParser.isTokenInvalidResponse(responseBody)) {
                        Log.w("DualisGradesService", "Token invalid when fetching semesters, attempting re-authentication")
                        authService.reAuthenticateIfNeeded { success ->
                            if (success) {
                                Log.d("DualisGradesService", "Re-authentication successful, retrying semester fetch")
                                // Retry the semester fetch after successful re-authentication
                                getAvailableSemesters(callback)
                            } else {
                                Log.e("DualisGradesService", "Re-authentication failed, falling back to default semesters")
                                callback(Semester.getDefaultSemesters())
                            }
                        }
                        return@makeRequest
                    }

                    val semesters = htmlParser.parseSemestersFromHtml(responseBody)
                    Log.d("DualisGradesService", "Successfully parsed ${semesters.size} dynamic semesters from Dualis")

                    // Log the semesters for debugging
                    semesters.forEach { semester ->
                        Log.d("DualisGradesService", "  Semester: ${semester.displayName} (${semester.value}) [selected: ${semester.isSelected}]")
                    }

                    // Always return at least the default semesters if parsing returns empty
                    if (semesters.isNotEmpty()) {
                        callback(semesters)
                    } else {
                        Log.w("DualisGradesService", "No semesters parsed, falling back to defaults")
                        callback(Semester.getDefaultSemesters())
                    }
                } catch (e: Exception) {
                    Log.e("DualisGradesService", "Error parsing semesters from Dualis response", e)
                    // Fallback to default semesters if parsing fails
                    Log.w("DualisGradesService", "Falling back to default semesters due to parsing error")
                    callback(Semester.getDefaultSemesters())
                }
            } else {
                Log.e("DualisGradesService", "No response body received for semesters")
                // Fallback to default semesters if request fails
                Log.w("DualisGradesService", "Falling back to default semesters due to network error")
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

        if (authService.isDemoMode) {
            Log.d("DualisGradesService", "Demo mode: returning demo study grades data")
            callback(createDemoStudyGrades(semesterArgument))
            return
        }
        if (!urlManager.hasValidToken()) {
            Log.e("DualisGradesService", "Auth Token is null. Authentication required.")
            callback(null)
            return
        }
        performStudyGradesRequest(semesterArgument, callback, rateLimitRetry = 0, tokenRetry = 0)
    }

    private fun performStudyGradesRequest(
        semesterArgument: String,
        callback: (StudyGrades?) -> Unit,
        rateLimitRetry: Int,
        tokenRetry: Int
    ) {
        val baseUrl = urlManager.buildStudyGradesUrl(semesterArgument)
        if (baseUrl == null) {
            Log.e("DualisGradesService", "Could not build study grades URL")
            callback(null)
            return
        }
        Log.d("DualisGradesService", "Semester-specific URL: $baseUrl (rateLimitRetry=$rateLimitRetry tokenRetry=$tokenRetry)")
        val request = networkClient.createGetRequest(baseUrl)
        networkClient.makeRequest(request, "Study Grades") { _, responseBody ->
            if (responseBody != null) {
                if (htmlParser.isRateLimitResponse(responseBody)) {
                    if (rateLimitRetry < maxRateLimitRetries) {
                        val nextAttempt = rateLimitRetry + 1
                        Log.w("DualisGradesService", "Rate limit detected for grades. Retrying in 2s (attempt $nextAttempt/$maxRateLimitRetries)")
                        RateLimitTracker.updateRateLimit(nextAttempt, maxRateLimitRetries)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            performStudyGradesRequest(semesterArgument, callback, nextAttempt, tokenRetry)
                        }, 2000)
                    } else {
                        Log.e("DualisGradesService", "Rate limit persists after $maxRateLimitRetries attempts for grades")
                        RateLimitTracker.finalFailure(maxRateLimitRetries)
                        callback(null)
                    }
                    return@makeRequest
                }
                if (htmlParser.isTokenInvalidResponse(responseBody)) {
                    if (tokenRetry < 1) {
                        authService.reAuthenticateIfNeeded { success ->
                            if (success) {
                                RateLimitTracker.clear()
                                performStudyGradesRequest(semesterArgument, callback, rateLimitRetry, tokenRetry + 1)
                            } else callback(null)
                        }
                    } else callback(null)
                    return@makeRequest
                }
                try {
                    val parser = StudyGradesParser()
                    val studyGrades = parser.extractStudyGrades(responseBody, semesterArgument)
                    RateLimitTracker.clear()
                    callback(studyGrades)
                } catch (e: Exception) {
                    Log.e("DualisGradesService", "Study grades parsing failed", e)
                    callback(null)
                }
            } else {
                if (rateLimitRetry < maxRateLimitRetries) {
                    val nextAttempt = rateLimitRetry + 1
                    Log.w("DualisGradesService", "Null response body (possible rate limit) for grades. Retrying in 2s (attempt $nextAttempt/$maxRateLimitRetries)")
                    RateLimitTracker.updateRateLimit(nextAttempt, maxRateLimitRetries)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        performStudyGradesRequest(semesterArgument, callback, nextAttempt, tokenRetry)
                    }, 2000)
                } else {
                    Log.e("DualisGradesService", "Null response persists after retries for grades")
                    RateLimitTracker.finalFailure(maxRateLimitRetries)
                    callback(null)
                }
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
