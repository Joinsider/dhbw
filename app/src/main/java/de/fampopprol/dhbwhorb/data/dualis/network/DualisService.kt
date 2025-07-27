/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import java.time.LocalDate

/**
 * Main service class that coordinates between specialized Dualis services
 * This class now acts as a facade, delegating responsibilities to focused service classes
 */
class DualisService {

    // Core service components
    private val networkClient = DualisNetworkClient()
    private val urlManager = DualisUrlManager()
    private val htmlParser = DualisHtmlParser()

    // Specialized services
    private val authService = DualisAuthenticationService(networkClient, urlManager, htmlParser)
    private val scheduleService = DualisScheduleService(networkClient, urlManager, htmlParser, authService)
    private val gradesService = DualisGradesService(networkClient, urlManager, htmlParser, authService)

    /**
     * Logs into Dualis with user credentials
     */
    fun login(user: String, pass: String, callback: (String?) -> Unit) {
        authService.login(user, pass, callback)
    }

    /**
     * Gets monthly schedule for specified year and month
     */
    fun getMonthlySchedule(year: Int, month: Int, callback: (List<TimetableDay>?) -> Unit) {
        scheduleService.getMonthlySchedule(year, month, callback)
    }

    /**
     * Gets weekly schedule for specified date
     */
    fun getWeeklySchedule(targetDate: LocalDate, callback: (List<TimetableDay>?) -> Unit) {
        scheduleService.getWeeklySchedule(targetDate, callback)
    }

    /**
     * Gets available semesters
     */
    fun getAvailableSemesters(callback: (List<Semester>?) -> Unit) {
        gradesService.getAvailableSemesters(callback)
    }

    /**
     * Gets study grades for a specific semester
     */
    fun getStudyGradesForSemester(semester: Semester, callback: (StudyGrades?) -> Unit) {
        gradesService.getStudyGradesForSemester(semester, callback)
    }

    /**
     * Gets study grades with optional semester argument
     */
    fun getStudyGrades(semesterArgument: String = "", callback: (StudyGrades?) -> Unit) {
        gradesService.getStudyGrades(semesterArgument, callback)
    }

    /**
     * Checks if user is currently authenticated
     */
    fun isAuthenticated(): Boolean {
        return authService.isAuthenticated()
    }

    /**
     * Checks if the service is running in demo mode
     */
    fun isDemoMode(): Boolean {
        return authService.isDemoMode
    }

    /**
     * Logs out and clears all stored data
     */
    fun logout() {
        authService.logout()
    }

    /**
     * Re-authenticates if needed (mainly for internal use by other services)
     */
    fun reAuthenticateIfNeeded(callback: (Boolean) -> Unit) {
        authService.reAuthenticateIfNeeded(callback)
    }
}
