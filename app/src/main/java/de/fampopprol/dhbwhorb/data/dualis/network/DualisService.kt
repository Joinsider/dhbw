/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.demo.DemoDataProvider
import de.fampopprol.dhbwhorb.data.dualis.models.DualisUrl
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.Module
import de.fampopprol.dhbwhorb.data.dualis.models.ExamState
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import okhttp3.OkHttpClient
import okhttp3.JavaNetCookieJar
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.LocalDate

/**
 * Main service class for interacting with the Dualis system.
 * This class coordinates between different specialized services and handles demo mode.
 */
class DualisService {

    companion object {
        private const val TAG = "DualisService"
        private const val MAX_RETRY_COUNT = 1
    }

    // HTTP client with cookie management
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .build()

    // Specialized service components
    private val authenticator = DualisAuthenticator(client)
    private val urlParser = DualisUrlParser()
    private val scheduleService = DualisScheduleService(client)
    private val gradesService = DualisGradesService(client)

    // State management
    private var authToken: String? = null
    private var dualisUrls: DualisUrl = DualisUrl()
    private var lastLoginCredentials: Pair<String, String>? = null
    private var isReAuthenticating = false
    private var isDemoMode = false

    /**
     * Authenticates with the Dualis system
     */
    fun login(user: String, pass: String, callback: (String?) -> Unit) {
        Log.d(TAG, "Starting login process for user: $user")

        // Handle demo mode
        if (DemoDataProvider.isDemoUser(user) && pass == DemoDataProvider.DEMO_PASSWORD) {
            Log.d(TAG, "Demo user detected, enabling demo mode")
            isDemoMode = true
            lastLoginCredentials = Pair(user, pass)
            callback("Demo login successful")
            return
        }

        // Reset demo mode for regular users
        isDemoMode = false
        lastLoginCredentials = Pair(user, pass)

        authenticator.login(user, pass) { result ->
            when (result) {
                is DualisAuthenticator.AuthResult.Success -> {
                    authToken = result.authToken
                    dualisUrls = urlParser.parseMainPage(result.mainPageContent, result.authToken)
                    Log.d(TAG, "Login successful")
                    callback("Login successful")
                }
                is DualisAuthenticator.AuthResult.Error -> {
                    Log.e(TAG, "Login failed: ${result.message}")
                    callback(null)
                }
            }
        }
    }

    /**
     * Fetches weekly schedule
     */
    fun getWeeklySchedule(targetDate: LocalDate, callback: (List<TimetableDay>?) -> Unit) {
        if (isDemoMode) {
            Log.d(TAG, "Demo mode: returning demo timetable data for week starting $targetDate")
            val demoData = DemoDataProvider.getDemoTimetableForWeek(targetDate)
            callback(demoData)
            return
        }

        getWeeklyScheduleWithRetry(targetDate, callback, 0)
    }

    /**
     * Fetches available semesters
     */
    fun getAvailableSemesters(callback: (List<Semester>?) -> Unit) {
        if (isDemoMode) {
            Log.d(TAG, "Demo mode: returning demo semesters")
            callback(Semester.getDefaultSemesters())
            return
        }

        if (!DualisUtils.isValidAuthToken(authToken)) {
            Log.e(TAG, "Invalid auth token for semester request")
            callback(null)
            return
        }

        gradesService.getAvailableSemesters(authToken!!, callback)
    }

    /**
     * Fetches study grades for a specific semester
     */
    fun getStudyGradesForSemester(semester: Semester, callback: (StudyGrades?) -> Unit) {
        val semesterArgument = Semester.formatSemesterArgument(semester.value)
        Log.d(TAG, "Fetching grades for semester: ${semester.displayName}")
        getStudyGrades(semesterArgument, callback)
    }

    /**
     * Fetches study grades
     */
    fun getStudyGrades(semesterArgument: String = "", callback: (StudyGrades?) -> Unit) {
        when {
            isDemoMode -> {
                Log.d(TAG, "Demo mode: returning demo study grades data")
                callback(createDemoStudyGrades(semesterArgument))
            }
            !DualisUtils.isValidAuthToken(authToken) -> {
                Log.e(TAG, "Invalid auth token for grades request")
                callback(null)
            }
            else -> {
                gradesService.getStudyGrades(authToken!!, semesterArgument) { grades ->
                    grades?.let { callback(it) } ?: reAuthenticateIfNeeded { success ->
                        if (success) gradesService.getStudyGrades(authToken!!, semesterArgument, callback)
                        else callback(null)
                    }
                }
            }
        }
    }

    /**
     * Checks if the service is properly authenticated
     */
    fun isAuthenticated(): Boolean {
        return DualisUtils.isValidAuthToken(authToken)
    }

    /**
     * Checks if the service is in demo mode
     */
    fun isDemoMode(): Boolean {
        return isDemoMode
    }

    // Private helper methods

    private fun getWeeklyScheduleWithRetry(
        targetDate: LocalDate,
        callback: (List<TimetableDay>?) -> Unit,
        retryCount: Int
    ) {
        if (!isValidForScheduleRequest()) {
            callback(null)
            return
        }

        scheduleService.getWeeklySchedule(
            dualisUrls.monthlyScheduleUrl!!,
            authToken!!,
            targetDate
        ) { result ->
            when {
                result != null -> callback(result)
                retryCount < MAX_RETRY_COUNT -> {
                    Log.w(TAG, "Schedule request failed, attempting re-authentication")
                    reAuthenticateIfNeeded { success ->
                        if (success) getWeeklyScheduleWithRetry(targetDate, callback, retryCount + 1)
                        else callback(null)
                    }
                }
                else -> callback(null)
            }
        }
    }

    private fun isValidForScheduleRequest(): Boolean {
        if (dualisUrls.monthlyScheduleUrl == null) {
            Log.e(TAG, "Monthly schedule URL is null")
            return false
        }
        if (!DualisUtils.isValidAuthToken(authToken)) {
            Log.e(TAG, "Invalid auth token for schedule request")
            return false
        }
        return true
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

    private fun reAuthenticateIfNeeded(onComplete: (Boolean) -> Unit) {
        if (isReAuthenticating) {
            Log.d(TAG, "Re-authentication already in progress")
            return
        }

        val credentials = lastLoginCredentials
        if (credentials == null) {
            Log.e(TAG, "No stored credentials for re-authentication")
            onComplete(false)
            return
        }

        isReAuthenticating = true
        Log.d(TAG, "Re-authenticating due to invalid token")

        login(credentials.first, credentials.second) { result ->
            isReAuthenticating = false
            onComplete(result != null)
        }
    }
}
