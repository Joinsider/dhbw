/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.annotation.SuppressLint
import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import okhttp3.*
import org.jsoup.Jsoup
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Handles timetable/schedule related operations
 */
class DualisScheduleService(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "DualisScheduleService"
    }

    fun getWeeklySchedule(
        scheduleUrl: String,
        authToken: String,
        targetDate: LocalDate,
        callback: (List<TimetableDay>?) -> Unit
    ) {
        Log.d(TAG, "Fetching weekly schedule for $targetDate")

        val url = buildScheduleUrl(
            scheduleUrl,
            authToken,
            targetDate.year,
            targetDate.monthValue,
            targetDate.dayOfMonth
        )
        makeScheduleRequest(url, callback)
    }

    @SuppressLint("DefaultLocale")
    private fun buildScheduleUrl(
        baseUrl: String,
        authToken: String,
        year: Int,
        month: Int,
        day: Int
    ): String {
        val argumentsRegex = Regex("ARGUMENTS=([^&]+)")
        val existingArgumentsMatch = argumentsRegex.find(baseUrl)
        val existingArguments = existingArgumentsMatch?.groupValues?.get(1) ?: ""

        val formattedDate = String.format("%02d.%02d.%d", day, month, year)
        val updatedArguments = existingArguments.replaceFirst("-A", "-A$formattedDate")

        return baseUrl.replace(existingArguments, updatedArguments)
            .replace("ARGUMENTS=-N$authToken", "ARGUMENTS=-N$authToken")
    }

    private fun makeScheduleRequest(url: String, callback: (List<TimetableDay>?) -> Unit) {
        Log.d(TAG, "Making schedule request to: $url")

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Schedule request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body.string()
                Log.d(TAG, "Schedule response received: ${response.code}")

                if (response.isSuccessful) {
                    try {
                        val timetableDays = parseScheduleResponse(responseBody)
                        callback(timetableDays)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing schedule", e)
                        callback(null)
                    }
                } else {
                    Log.e(TAG, "Schedule request failed with code: ${response.code}")
                    callback(null)
                }
            }
        })
    }

    private fun parseScheduleResponse(html: String): List<TimetableDay> {
        Log.d(TAG, "Parsing schedule HTML")

        val document = Jsoup.parse(html)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        val table = document.select("table.nb").first() ?: return emptyList()
        val caption = table.select("caption").first()?.text()

        // Extract date range from caption
        val dateRangeRegex = Regex("Stundenplan vom (\\d{2}\\.\\d{2}\\.) bis (\\d{2}\\.\\d{2}\\.)")
        val matchResult = caption?.let { dateRangeRegex.find(it) }

        if (matchResult == null) {
            Log.w(TAG, "Could not extract date range from caption: $caption")
            return emptyList()
        }

        val currentYear = LocalDate.now().year
        val startDate = parseDate(matchResult.groupValues[1] + currentYear, dateFormatter)
        val endDate = parseDate(matchResult.groupValues[2] + currentYear, dateFormatter)

        if (startDate == null || endDate == null) {
            Log.w(TAG, "Could not parse dates from caption")
            return emptyList()
        }

        // Create day-to-date mapping
        val dayToDateMap = createDayToDateMapping(table, startDate, endDate, dateFormatter)

        // Parse events
        val eventsByDate = parseEvents(document, dayToDateMap)

        // Create timetable days
        return createTimetableDays(eventsByDate, dateFormatter)
    }

    private fun parseDate(dateString: String, formatter: DateTimeFormatter): LocalDate? {
        return try {
            LocalDate.parse(dateString, formatter)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date: $dateString", e)
            null
        }
    }

    private fun createDayToDateMapping(
        table: org.jsoup.nodes.Element,
        startDate: LocalDate,
        endDate: LocalDate,
        dateFormatter: DateTimeFormatter
    ): Map<String, LocalDate> {
        val dayToDateMap = mutableMapOf<String, LocalDate>()
        val headerRow = table.select("tr.tbsubhead").first()

        headerRow?.select("th.weekday")?.forEach { dayHeaderElement ->
            val link = dayHeaderElement.select("a").first()
            val headerText = link?.text()?.trim() ?: dayHeaderElement.text().trim()

            val headerPattern = Regex("(\\w+)\\s+(\\d{2}\\.\\d{2})\\.")
            val headerMatch = headerPattern.find(headerText)

            if (headerMatch != null) {
                val dayAbbreviation = headerMatch.groupValues[1]
                val dateString = headerMatch.groupValues[2] + ".${startDate.year}"

                val fullDayName = mapDayAbbreviation(dayAbbreviation)
                val parsedDate = parseDate(dateString, dateFormatter)

                if (fullDayName != null && parsedDate != null) {
                    dayToDateMap[fullDayName] = parsedDate
                }
            }
        }

        // Fallback: create mapping from date range if headers failed
        if (dayToDateMap.isEmpty()) {
            Log.w(TAG, "No dates found in headers, using fallback mapping")
            createFallbackDayMapping(dayToDateMap, startDate, endDate)
        }

        return dayToDateMap
    }

    private fun mapDayAbbreviation(abbreviation: String): String? {
        return when (abbreviation) {
            "Mo" -> "Montag"
            "Di" -> "Dienstag"
            "Mi" -> "Mittwoch"
            "Do" -> "Donnerstag"
            "Fr" -> "Freitag"
            "Sa" -> "Samstag"
            "So" -> "Sonntag"
            else -> {
                Log.w(TAG, "Unknown day abbreviation: $abbreviation")
                null
            }
        }
    }

    private fun createFallbackDayMapping(
        dayToDateMap: MutableMap<String, LocalDate>,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        val weekDays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            val dayOfWeek = currentDate.dayOfWeek.value
            val dayName = weekDays[dayOfWeek - 1]
            dayToDateMap[dayName] = currentDate
            currentDate = currentDate.plusDays(1)
        }
    }

    private fun parseEvents(
        document: org.jsoup.nodes.Document,
        dayToDateMap: Map<String, LocalDate>
    ): Map<LocalDate, MutableList<TimetableEvent>> {
        val eventsByDate = mutableMapOf<LocalDate, MutableList<TimetableEvent>>()

        // Initialize empty lists for all dates
        dayToDateMap.values.forEach { date ->
            eventsByDate[date] = mutableListOf()
        }

        val appointmentCells = document.select("td.appointment")
        Log.d(TAG, "Found ${appointmentCells.size} appointment cells")

        for (cell in appointmentCells) {
            val event = parseEventFromCell(cell, dayToDateMap)
            if (event != null) {
                eventsByDate[event.first]?.add(event.second)
            }
        }

        return eventsByDate
    }

    private fun parseEventFromCell(
        cell: org.jsoup.nodes.Element,
        dayToDateMap: Map<String, LocalDate>
    ): Pair<LocalDate, TimetableEvent>? {
        // Extract title
        val clonedCell = cell.clone()
        clonedCell.select("span.timePeriod").remove()
        clonedCell.select("br").remove()
        val title = clonedCell.text().trim().replace(Regex(">\\s*$"), "").trim()

        if (title.isEmpty()) return null

        // Extract time and room
        val timePeriodSpan = cell.select("span.timePeriod").first()
        val timePeriodText = timePeriodSpan?.text()?.trim() ?: ""

        val (startTime, endTime, room) = parseTimeAndRoom(timePeriodText)

        // Get event date
        val abbrAttribute = cell.attr("abbr")
        val dayOfWeekInGerman = abbrAttribute.split(" ")[0]
        val eventDate = dayToDateMap[dayOfWeekInGerman]

        return if (eventDate != null) {
            val event = TimetableEvent(title, startTime, endTime, room, "")
            Pair(eventDate, event)
        } else {
            null
        }
    }

    private fun parseTimeAndRoom(timePeriodText: String): Triple<String, String, String> {
        val timeRoomParts = timePeriodText.split("\\s+".toRegex()).filter { it.isNotBlank() }

        return if (timeRoomParts.size >= 3) {
            val startTime = timeRoomParts[0]
            val endTime = timeRoomParts[2]
            val room = if (timeRoomParts.size > 3) {
                timeRoomParts.drop(3).joinToString(" ")
            } else {
                ""
            }
            Triple(startTime, endTime, room)
        } else {
            Triple("", "", "")
        }
    }

    private fun createTimetableDays(
        eventsByDate: Map<LocalDate, MutableList<TimetableEvent>>,
        dateFormatter: DateTimeFormatter
    ): List<TimetableDay> {
        return eventsByDate.entries
            .sortedBy { it.key }
            .map { entry ->
                TimetableDay(dateFormatter.format(entry.key), entry.value)
            }
    }
}
