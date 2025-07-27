/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import org.jsoup.Jsoup

/**
 * Handles enhancement of timetable events with detailed information
 */
class DualisEventEnhancer(
    private val networkClient: DualisNetworkClient,
    private val authService: DualisAuthenticationService,
    private val urlManager: DualisUrlManager
) {

    /**
     * Enhances timetable events with detailed information from individual event pages
     */
    fun enhanceTimetableWithDetails(
        timetableDays: List<TimetableDay>,
        callback: (List<TimetableDay>?) -> Unit
    ) {
        if (authService.isDemoMode || timetableDays.isEmpty()) {
            // Return original data for demo mode or empty lists
            callback(timetableDays)
            return
        }

        Log.d("DualisEventEnhancer", "=== ENHANCING TIMETABLE WITH DETAILED INFORMATION ===")

        // Collect all events that have detail URLs
        val eventsToEnhance = mutableListOf<Triple<TimetableEvent, TimetableDay, Int>>()

        timetableDays.forEach { day ->
            day.events.forEachIndexed { index, event ->
                if (event.detailUrl != null) {
                    eventsToEnhance.add(Triple(event, day, index))
                }
            }
        }

        if (eventsToEnhance.isEmpty()) {
            Log.d("DualisEventEnhancer", "No events with detail URLs found, returning original data")
            callback(timetableDays)
            return
        }

        Log.d("DualisEventEnhancer", "Found ${eventsToEnhance.size} events to enhance with detailed information")

        // Create enhanced versions of the timetable days with mutable event lists
        val enhancedDays = timetableDays.map { day ->
            TimetableDay(day.date, day.events.toMutableList())
        }

        var completedRequests = 0
        val totalRequests = eventsToEnhance.size

        // Fetch details for each event
        eventsToEnhance.forEach { (event, originalDay, eventIndex) ->
            fetchEventDetails(event.detailUrl!!) { eventDetails ->
                synchronized(enhancedDays) {
                    completedRequests++

                    // Find the corresponding day in enhanced days
                    val enhancedDay = enhancedDays.find { it.date == originalDay.date }

                    if (enhancedDay != null && eventIndex < enhancedDay.events.size) {
                        val enhancedEvent = if (eventDetails != null) {
                            event.copy(
                                fullTitle = eventDetails.fullTitle,
                                courseCode = eventDetails.courseCode,
                                lecturer = eventDetails.lecturer,
                                room = eventDetails.room.ifEmpty { event.room }
                            )
                        } else {
                            // Keep original event if details couldn't be fetched
                            event
                        }

                        // Replace the event in the enhanced day (now using MutableList)
                        (enhancedDay.events as MutableList)[eventIndex] = enhancedEvent

                        Log.d("DualisEventEnhancer", "Enhanced event ${event.title} -> ${enhancedEvent.fullTitle ?: event.title}")
                        if (eventDetails != null) {
                            Log.d("DualisEventEnhancer", "  Course code: ${eventDetails.courseCode}")
                            Log.d("DualisEventEnhancer", "  Lecturer: ${eventDetails.lecturer}")
                            Log.d("DualisEventEnhancer", "  Room: ${eventDetails.room}")
                        }
                    }

                    // Check if all requests are completed
                    if (completedRequests >= totalRequests) {
                        Log.d("DualisEventEnhancer", "All event details fetched, calling callback with enhanced data")
                        callback(enhancedDays)
                    }

                    Log.d("DualisEventEnhancer", "Enhancement progress: $completedRequests/$totalRequests")
                }
            }
        }
    }

    /**
     * Fetches detailed information for a specific event from its detail page
     */
    private fun fetchEventDetails(eventUrl: String, callback: (EventDetails?) -> Unit) {
        if (authService.isDemoMode) {
            // Return null for demo mode - we'll use basic info only
            callback(null)
            return
        }

        val request = networkClient.createGetRequest(eventUrl)
        networkClient.makeRequest(request, "Event Details") { _, responseBody ->
            if (responseBody != null) {
                try {
                    val eventDetails = parseEventDetails(responseBody)
                    callback(eventDetails)
                } catch (e: Exception) {
                    Log.e("DualisEventEnhancer", "Error parsing event details", e)
                    callback(null)
                }
            } else {
                callback(null)
            }
        }
    }

    /**
     * Parses event details from the HTML of an individual event page
     */
    private fun parseEventDetails(html: String): EventDetails? {
        val document = Jsoup.parse(html)

        // Extract the full title from h1 tag
        val h1Element = document.select("h1").first()
        val fullTitle = h1Element?.text()?.trim()

        if (fullTitle.isNullOrEmpty()) {
            Log.w("DualisEventEnhancer", "Could not extract full title from event details page")
            return null
        }

        // Extract course code from the full title (e.g., "T4INF1003.1" from "T4INF1003.1 Algorithmen und Komplexität HOR-TINF2024")
        val courseCodePattern = Regex("^([A-Z0-9.]+)")
        val courseCodeMatch = courseCodePattern.find(fullTitle)
        val courseCode = courseCodeMatch?.groupValues?.get(1)

        // Extract lecturer information
        val instructorElement = document.select("td[name=instructorName]").first()
        val lecturer = instructorElement?.text()?.trim() ?: ""

        // Extract room information
        val roomElement = document.select("span[name=appoinmentRooms]").first()
        val room = roomElement?.text()?.trim() ?: ""

        Log.d("DualisEventEnhancer", "Parsed event details: fullTitle='$fullTitle', courseCode='$courseCode', lecturer='$lecturer', room='$room'")

        return EventDetails(
            fullTitle = fullTitle,
            courseCode = courseCode,
            lecturer = lecturer,
            room = room
        )
    }

    /**
     * Data class to hold detailed event information
     */
    private data class EventDetails(
        val fullTitle: String,
        val courseCode: String?,
        val lecturer: String,
        val room: String
    )
}
