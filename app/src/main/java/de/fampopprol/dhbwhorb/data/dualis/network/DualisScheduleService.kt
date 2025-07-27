/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.annotation.SuppressLint
import android.util.Log
import de.fampopprol.dhbwhorb.data.demo.DemoDataProvider
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Handles schedule/timetable operations for Dualis
 */
class DualisScheduleService(
    private val networkClient: DualisNetworkClient,
    private val urlManager: DualisUrlManager,
    private val htmlParser: DualisHtmlParser,
    private val authService: DualisAuthenticationService
) {

    // Create event enhancer instance
    private val eventEnhancer = DualisEventEnhancer(networkClient, authService, urlManager)

    @SuppressLint("DefaultLocale")
    fun getMonthlySchedule(year: Int, month: Int, callback: (List<TimetableDay>?) -> Unit) {
        // Return demo data if in demo mode
        if (authService.isDemoMode) {
            Log.d("DualisScheduleService", "Demo mode: returning demo timetable data for month $month/$year")
            val firstDayOfMonth = LocalDate.of(year, month, 1)
            val firstMonday = firstDayOfMonth.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val demoData = DemoDataProvider.getDemoTimetableForWeek(firstMonday)
            callback(demoData)
            return
        }

        if (urlManager.dualisUrls.monthlyScheduleUrl == null || !urlManager.hasValidToken()) {
            Log.e("DualisScheduleService", "Monthly schedule URL or Auth Token is null. Cannot fetch timetable.")
            callback(null)
            return
        }

        val baseUrl = urlManager.dualisUrls.monthlyScheduleUrl!!
        val formattedDate = String.format("%02d.%02d.%d", 1, month, year)
        val url = urlManager.buildScheduleUrl(baseUrl, formattedDate)

        Log.d("DualisScheduleService", "Constructed Monthly Schedule URL: $url")

        val request = networkClient.createGetRequest(url)
        networkClient.makeRequest(request, "Monthly Schedule") { _, responseBody ->
            if (responseBody != null) {
                try {
                    val timetableDays = htmlParser.parseSchedule(responseBody)
                    callback(timetableDays)
                } catch (e: Exception) {
                    Log.e("DualisScheduleService", "Error parsing monthly schedule", e)
                    callback(null)
                }
            } else {
                callback(null)
            }
        }
    }

    fun getWeeklySchedule(targetDate: LocalDate, callback: (List<TimetableDay>?) -> Unit) {
        // Return demo data if in demo mode
        if (authService.isDemoMode) {
            Log.d("DualisScheduleService", "Demo mode: returning demo timetable data for week starting $targetDate")
            val demoData = DemoDataProvider.getDemoTimetableForWeek(targetDate)
            callback(demoData)
            return
        }

        getWeeklyScheduleWithRetry(targetDate, callback, retryCount = 0)
    }

    private fun getWeeklyScheduleWithRetry(
        targetDate: LocalDate,
        callback: (List<TimetableDay>?) -> Unit,
        retryCount: Int
    ) {
        if (urlManager.dualisUrls.monthlyScheduleUrl == null || !urlManager.hasValidToken()) {
            Log.e("DualisScheduleService", "Monthly schedule URL or Auth Token is null. Cannot fetch weekly timetable.")
            callback(null)
            return
        }

        val baseUrl = urlManager.dualisUrls.monthlyScheduleUrl!!
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val formattedDate = targetDate.format(dateFormatter)
        val url = urlManager.buildScheduleUrl(baseUrl, formattedDate)

        Log.d("DualisScheduleService", "Constructed Weekly Schedule URL for $targetDate: $url")

        val request = networkClient.createGetRequest(url)
        networkClient.makeRequest(request, "Weekly Schedule") { _, responseBody ->
            if (responseBody != null) {
                // Check if the response indicates an invalid token
                if (htmlParser.isTokenInvalidResponse(responseBody)) {
                    Log.w("DualisScheduleService", "Token appears to be invalid, attempting re-authentication")
                    if (retryCount < 1) { // Only retry once
                        authService.reAuthenticateIfNeeded { success ->
                            if (success) {
                                Log.d("DualisScheduleService", "Re-authentication successful, retrying weekly schedule fetch")
                                getWeeklyScheduleWithRetry(targetDate, callback, retryCount + 1)
                            } else {
                                Log.e("DualisScheduleService", "Re-authentication failed")
                                callback(null)
                            }
                        }
                    } else {
                        Log.e("DualisScheduleService", "Already retried once, giving up")
                        callback(null)
                    }
                    return@makeRequest
                }

                try {
                    val timetableDays = htmlParser.parseSchedule(responseBody)
                    Log.d("DualisScheduleService", "Parsed weekly schedule for $targetDate: ${timetableDays.size} days")

                    // Enhance timetable with detailed information from individual event pages
                    eventEnhancer.enhanceTimetableWithDetails(timetableDays) { enhancedTimetableDays ->
                        if (enhancedTimetableDays != null) {
                            Log.d("DualisScheduleService", "Enhanced weekly schedule for $targetDate with detailed information")
                            callback(enhancedTimetableDays)
                        } else {
                            Log.w("DualisScheduleService", "Failed to enhance timetable with details, returning basic timetable")
                            callback(timetableDays)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DualisScheduleService", "Error parsing weekly schedule", e)
                    callback(null)
                }
            } else {
                callback(null)
            }
        }
    }
}
