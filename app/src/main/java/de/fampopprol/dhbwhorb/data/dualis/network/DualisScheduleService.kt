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

    private val maxRateLimitRetries = 3

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

        getWeeklyScheduleWithRetry(targetDate, callback, retryCount = 0, rateLimitRetry = 0)
    }

    private fun getWeeklyScheduleWithRetry(
        targetDate: LocalDate,
        callback: (List<TimetableDay>?) -> Unit,
        retryCount: Int,
        rateLimitRetry: Int
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
                // Rate limit detection BEFORE token invalid logic
                if (htmlParser.isRateLimitResponse(responseBody)) {
                    if (rateLimitRetry < maxRateLimitRetries) {
                        val nextAttempt = rateLimitRetry + 1
                        android.util.Log.w("DualisScheduleService", "Rate limit detected for weekly schedule. Retrying in 2s (attempt $nextAttempt/$maxRateLimitRetries)")
                        RateLimitTracker.updateRateLimit(nextAttempt, maxRateLimitRetries)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            getWeeklyScheduleWithRetry(targetDate, callback, retryCount, nextAttempt)
                        }, 2000)
                    } else {
                        android.util.Log.e("DualisScheduleService", "Rate limit persists after $maxRateLimitRetries attempts")
                        RateLimitTracker.finalFailure(maxRateLimitRetries)
                        callback(null)
                    }
                    return@makeRequest
                }

                // Check if the response indicates an invalid token
                if (htmlParser.isTokenInvalidResponse(responseBody)) {
                    Log.w("DualisScheduleService", "Token appears to be invalid, attempting re-authentication")
                    if (retryCount < 1) { // Only retry once
                        authService.reAuthenticateIfNeeded { success ->
                            if (success) {
                                // Clear rate limit state if any after successful auth
                                RateLimitTracker.clear()
                                Log.d("DualisScheduleService", "Re-authentication successful, retrying weekly schedule fetch")
                                getWeeklyScheduleWithRetry(targetDate, callback, retryCount + 1, rateLimitRetry)
                            } else {
                                callback(null)
                            }
                        }
                    } else {
                        callback(null)
                    }
                    return@makeRequest
                }

                try {
                    val timetableDays = htmlParser.parseSchedule(responseBody)
                    // If timetableDays empty and not rate limited we proceed (maybe legitimately no events)
                    RateLimitTracker.clear()
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
                    android.util.Log.e("DualisScheduleService", "Error parsing weekly schedule", e)
                    callback(null)
                }
            } else {
                // Null body could also be due to transient network/rate limit without body
                if (rateLimitRetry < maxRateLimitRetries) {
                    val nextAttempt = rateLimitRetry + 1
                    android.util.Log.w("DualisScheduleService", "Null response body (possible rate limit). Retrying in 2s (attempt $nextAttempt/$maxRateLimitRetries)")
                    RateLimitTracker.updateRateLimit(nextAttempt, maxRateLimitRetries)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        getWeeklyScheduleWithRetry(targetDate, callback, retryCount, nextAttempt)
                    }, 2000)
                } else {
                    android.util.Log.e("DualisScheduleService", "Null response body persists after retries")
                    RateLimitTracker.finalFailure(maxRateLimitRetries)
                    callback(null)
                }
            }
        }
    }
}
