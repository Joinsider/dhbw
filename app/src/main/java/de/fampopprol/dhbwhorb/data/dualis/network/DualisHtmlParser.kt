/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.DualisUrl
import de.fampopprol.dhbwhorb.data.dualis.models.Semester
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import org.jsoup.Jsoup
import java.time.format.DateTimeFormatter

/**
 * Handles HTML parsing operations for Dualis responses
 */
class DualisHtmlParser {

    /**
     * Parses the main page to extract Dualis URLs
     */
    fun parseMainPage(html: String, authToken: String?): DualisUrl {
        Log.d("DualisHtmlParser", "=== PARSING REAL MAIN PAGE ===")
        Log.d("DualisHtmlParser", "HTML length: ${html.length}")

        val document = Jsoup.parse(html)
        val dualisUrls = DualisUrl()
        val dualisEndpoint = "https://dualis.dhbw.de"

        // Log all links for debugging
        val allLinks = document.select("a")
        Log.d("DualisHtmlParser", "Found ${allLinks.size} total links in main page")

        // Construct the COURSERESULTS URL using the auth token
        if (authToken != null) {
            val baseUrl = "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N$authToken,-N000307,"
            dualisUrls.studentResultsUrl = baseUrl
            Log.d("DualisHtmlParser", "Constructed student results URL: ${dualisUrls.studentResultsUrl}")
        }

        // Extract course result URL
        val courseResultElement = document.select("a:contains(Prüfungsergebnisse)").first()
        if (courseResultElement != null) {
            val rawHref = courseResultElement.attr("href")
            dualisUrls.courseResultUrl = if (rawHref.startsWith("/")) dualisEndpoint + rawHref else rawHref
            Log.d("DualisHtmlParser", "Found course results URL: ${dualisUrls.courseResultUrl}")
        }

        // Extract monthly schedule URL
        val monthlyScheduleElement = document.select("a:contains(diese Woche)").first()
        if (monthlyScheduleElement != null) {
            val rawHref = monthlyScheduleElement.attr("href")
            dualisUrls.monthlyScheduleUrl = if (rawHref.startsWith("/")) dualisEndpoint + rawHref else rawHref
            Log.d("DualisHtmlParser", "Found schedule URL: ${dualisUrls.monthlyScheduleUrl}")
        }

        // Extract logout URL
        val logoutElement = document.select("a:contains(Abmelden)").first()
        if (logoutElement != null) {
            val rawHref = logoutElement.attr("href")
            dualisUrls.logoutUrl = if (rawHref.startsWith("/")) dualisEndpoint + rawHref else rawHref
            Log.d("DualisHtmlParser", "Found logout URL: ${dualisUrls.logoutUrl}")
        }

        return dualisUrls
    }

    /**
     * Checks if the HTML content represents the main page
     */
    fun isMainPage(html: String): Boolean {
        val document = Jsoup.parse(html)
        // Check for elements that are typically present on the main page
        return document.select("a:contains(Prüfungsergebnisse)").isNotEmpty() ||
               document.select("a:contains(diese Woche)").isNotEmpty() ||
               document.select("a:contains(Abmelden)").isNotEmpty()
    }

    /**
     * Checks if the response indicates an invalid token
     */
    fun isTokenInvalidResponse(html: String): Boolean {
        return html.contains("Session ist abgelaufen") ||
               html.contains("Session expired") ||
               html.contains("Anmeldung erforderlich") ||
               html.contains("Login required") ||
               html.contains("LOGINCHECK")
    }

    /**
     * Parses semester information from HTML
     */
    fun parseSemestersFromHtml(html: String): List<Semester> {
        Log.d("DualisHtmlParser", "=== PARSING SEMESTERS FROM HTML ===")

        val document = Jsoup.parse(html)
        val semesters = mutableListOf<Semester>()

        // Look for semester select dropdown
        val semesterSelect = document.select("select#semester").first()

        if (semesterSelect != null) {
            Log.d("DualisHtmlParser", "Found semester select dropdown")

            val options = semesterSelect.select("option")
            Log.d("DualisHtmlParser", "Found ${options.size} semester options")

            options.forEach { option ->
                val value = option.attr("value")
                val displayName = option.text().trim()
                val isSelected = option.hasAttr("selected")

                if (value.isNotEmpty() && displayName.isNotEmpty()) {
                    semesters.add(Semester(value, displayName, isSelected))
                    Log.d("DualisHtmlParser", "Added semester: $displayName (value: $value, selected: $isSelected)")
                }
            }
        } else {
            Log.w("DualisHtmlParser", "No semester select dropdown found")
        }

        return semesters
    }

    /**
     * Parses monthly/weekly schedule from HTML
     */
    fun parseSchedule(html: String): List<TimetableDay> {
        Log.d("DualisHtmlParser", "=== PARSING SCHEDULE FROM HTML ===")

        val document = Jsoup.parse(html)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        val table = document.select("table.nb").first() ?: return emptyList()
        val caption = table.select("caption").first()?.text()
        Log.d("DualisHtmlParser", "Table caption: $caption")

        val dateRangeRegex = Regex("Stundenplan vom (\\d{2}\\.\\d{2}\\.) bis (\\d{2}\\.\\d{2}\\.)")
        val matchResult = caption?.let { dateRangeRegex.find(it) }

        val startDateString = matchResult?.groupValues?.get(1)
        val endDateString = matchResult?.groupValues?.get(2)
        Log.d("DualisHtmlParser", "Start date string: $startDateString, End date string: $endDateString")

        val currentYear = java.time.LocalDate.now().year

        val startLocalDate = requireNotNull(startDateString?.let {
            java.time.LocalDate.parse(it + currentYear, dateFormatter)
        }) {
            "Could not parse start date from caption: $caption"
        }
        val endLocalDate = requireNotNull(endDateString?.let {
            java.time.LocalDate.parse(it + currentYear, dateFormatter)
        }) {
            "Could not parse end date from caption: $caption"
        }

        // Find the header row with weekday columns
        val headerRow = table.select("tr.tbsubhead").first() ?: return emptyList()
        val dayToDateMap = mutableMapOf<String, java.time.LocalDate>()

        // Parse dates from table headers directly - look for th.weekday elements with links
        headerRow.select("th.weekday").forEach { dayHeaderElement ->
            val link = dayHeaderElement.select("a").first()
            val headerText = link?.text()?.trim() ?: dayHeaderElement.text().trim()
            Log.d("DualisHtmlParser", "Processing header: '$headerText'")

            // Extract day abbreviation and date from header text like "Mo 30.06."
            val headerPattern = Regex("(\\w+)\\s+(\\d{2}\\.\\d{2})\\.")
            val headerMatch = headerPattern.find(headerText)

            if (headerMatch != null) {
                val dayAbbreviation = headerMatch.groupValues[1]
                val dateString = headerMatch.groupValues[2] + ".$currentYear"

                val fullDayName = when (dayAbbreviation) {
                    "Mo" -> "Montag"
                    "Di" -> "Dienstag"
                    "Mi" -> "Mittwoch"
                    "Do" -> "Donnerstag"
                    "Fr" -> "Freitag"
                    "Sa" -> "Samstag"
                    "So" -> "Sonntag"
                    else -> {
                        Log.w("DualisHtmlParser", "Unknown day abbreviation: $dayAbbreviation")
                        ""
                    }
                }

                if (fullDayName.isNotEmpty()) {
                    try {
                        val parsedDate = java.time.LocalDate.parse(dateString, dateFormatter)
                        dayToDateMap[fullDayName] = parsedDate
                        Log.d("DualisHtmlParser", "Mapped $fullDayName to $parsedDate")
                    } catch (e: Exception) {
                        Log.e("DualisHtmlParser", "Error parsing date: $dateString", e)
                    }
                }
            } else {
                Log.w("DualisHtmlParser", "Could not parse header: '$headerText'")
            }
        }

        // If no headers were found with the standard approach, try extracting directly from the range
        if (dayToDateMap.isEmpty()) {
            Log.w("DualisHtmlParser", "No dates found in headers, trying to map from date range")

            // Create date mapping based on the date range from caption
            var currentDate = startLocalDate
            val weekDays = listOf(
                "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"
            )

            while (!currentDate.isAfter(endLocalDate)) {
                val dayOfWeek = currentDate.dayOfWeek.value // 1 = Monday, 7 = Sunday
                val dayName = weekDays[dayOfWeek - 1]
                dayToDateMap[dayName] = currentDate
                Log.d("DualisHtmlParser", "Fallback mapped $dayName to $currentDate")
                currentDate = currentDate.plusDays(1)
            }
        }

        Log.d("DualisHtmlParser", "Day To Date Map: $dayToDateMap")

        val eventsByFullDate = mutableMapOf<java.time.LocalDate, MutableList<TimetableEvent>>()
        var currentDay = startLocalDate
        while (!currentDay.isAfter(endLocalDate)) {
            eventsByFullDate[currentDay] = mutableListOf()
            currentDay = currentDay.plusDays(1)
        }

        val allAppointmentCells = document.select("td.appointment")
        Log.d("DualisHtmlParser", "Found ${allAppointmentCells.size} appointment cells")

        // Parse events
        for (cell in allAppointmentCells) {
            val cellHtml = cell.html()
            Log.d("DualisHtmlParser", "Processing appointment cell HTML: $cellHtml")

            // Extract event detail URL from links in the cell
            val eventLink = cell.select("a").first()
            var eventDetailUrl: String? = null

            if (eventLink != null) {
                val href = eventLink.attr("href")
                if (href.isNotEmpty()) {
                    eventDetailUrl = if (href.startsWith("/")) {
                        "https://dualis.dhbw.de$href"
                    } else if (href.startsWith("scripts/")) {
                        "https://dualis.dhbw.de/$href"
                    } else if (!href.startsWith("http")) {
                        "https://dualis.dhbw.de/scripts/$href"
                    } else {
                        href
                    }
                    Log.d("DualisHtmlParser", "Found event detail URL: $eventDetailUrl")
                }
            }

            // Extract title - get text content excluding timePeriod span
            val clonedCell = cell.clone()
            clonedCell.select("span.timePeriod").remove()
            clonedCell.select("br").remove()
            // Remove HTML comments and clean up
            var title = clonedCell.text().trim()

            // Clean up title by removing trailing ">" and any HTML artifacts
            title = title.replace(Regex(">\\s*$"), "").trim()

            // Skip if title is empty
            if (title.isEmpty()) {
                Log.d("DualisHtmlParser", "Skipping cell with empty title")
                continue
            }

            // Extract time and room information
            val timePeriodSpans = cell.select("span.timePeriod")
            var timePeriodText = ""

            // Combine text from all timePeriod spans
            for (span in timePeriodSpans) {
                val spanText = span.text().trim()
                if (spanText.isNotEmpty()) {
                    if (timePeriodText.isNotEmpty()) {
                        timePeriodText += " $spanText"
                      } else {
                        timePeriodText = spanText
                    }
                }
            }

            Log.d("DualisHtmlParser", "Time period text: '$timePeriodText'")

            // Parse time period - it might be in format "08:15 - 12:30 HOR-120" or similar
            val timeRoomParts = timePeriodText.split("\\s+".toRegex()).filter { it.isNotBlank() }

            var startTime = ""
            var endTime = ""
            var room = ""

            if (timeRoomParts.size >= 3) {
                startTime = timeRoomParts[0]
                // Skip the "-" separator
                endTime = timeRoomParts[2]
                // Room might be in the remaining parts
                if (timeRoomParts.size > 3) {
                    val rawRoom = timeRoomParts.drop(3).joinToString(" ")
                    room = parseRooms(rawRoom)
                }
            }

            val lecturer = "" // Will be filled from detailed information later

            // Get the day from the abbr attribute
            val abbrAttribute = cell.attr("abbr")
            val dayOfWeekInGerman = abbrAttribute.split(" ")[0]

            val eventDate = dayToDateMap[dayOfWeekInGerman]

            Log.d("DualisHtmlParser", "Processing cell:")
            Log.d("DualisHtmlParser", "  Title: '$title'")
            Log.d("DualisHtmlParser", "  Time Period Text: '$timePeriodText'")
            Log.d("DualisHtmlParser", "  Time Room Parts: $timeRoomParts")
            Log.d("DualisHtmlParser", "  Start Time: '$startTime', End Time: '$endTime', Room: '$room'")
            Log.d("DualisHtmlParser", "  Abbr Attribute: '$abbrAttribute', Day in German: '$dayOfWeekInGerman'")
            Log.d("DualisHtmlParser", "  Event Date: $eventDate")
            Log.d("DualisHtmlParser", "  Event Detail URL: $eventDetailUrl")

            if (eventDate != null && title.isNotEmpty()) {
                // Create event object with basic information
                val event = TimetableEvent(
                    title = title,
                    startTime = startTime,
                    endTime = endTime,
                    room = room,
                    lecturer = lecturer,
                    detailUrl = eventDetailUrl
                )

                eventsByFullDate[eventDate]?.add(event)
                Log.d("DualisHtmlParser", "Added event to date $eventDate: $title")
            } else {
                Log.w("DualisHtmlParser", "Skipping event - eventDate: $eventDate, title: '$title'")
            }
        }

        val sortedTimetableDays = eventsByFullDate.entries.sortedBy { it.key }.map { entry ->
            Log.d("DualisHtmlParser", "Creating TimetableDay for ${dateFormatter.format(entry.key)} with ${entry.value.size} events")
            TimetableDay(dateFormatter.format(entry.key), entry.value)
        }

        Log.d("DualisHtmlParser", "Parsed ${sortedTimetableDays.size} timetable days")
        sortedTimetableDays.forEach { day ->
            Log.d("DualisHtmlParser", "Day ${day.date}: ${day.events.size} events")
            day.events.forEach { event ->
                Log.d("DualisHtmlParser", "  Event: ${event.title} (${event.startTime} - ${event.endTime}) in ${event.room}")
            }
        }

        return sortedTimetableDays
    }

    /**
     * Checks if the page is a redirect page
     */
    fun isRedirectPage(html: String): Boolean {
        val document = Jsoup.parse(html)
        return document.select("div#sessionId").first() != null
    }

    /**
     * Extracts redirect URL from HTML page
     */
    fun extractRedirectUrl(html: String, baseUrl: String): String? {
        val document = Jsoup.parse(html)
        var nextRedirectUrl: String? = null

        // Try to get from script first
        for (element in document.select("script")) {
            val content = element.html()
            if (content.contains("window.location.href")) {
                val regex = Regex("window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]")
                val match = regex.find(content)
                val relativeUrl = match?.groupValues?.get(1)
                if (relativeUrl != null) {
                    nextRedirectUrl = makeAbsoluteUrl(baseUrl, relativeUrl)
                    break
                }
            }
        }

        // If not found in script, try from the <a> tag
        if (nextRedirectUrl == null) {
            val anchorElement = document.select("h2 a[href]").first()
            val relativeUrl = anchorElement?.attr("href")
            if (relativeUrl != null) {
                nextRedirectUrl = makeAbsoluteUrl(baseUrl, relativeUrl)
            }
        }

        return nextRedirectUrl
    }

    /**
     * Parses concatenated room strings like "HOR-135HOR-136" into separate rooms
     * separated by commas like "HOR-135, HOR-136"
     */
    private fun parseRooms(roomString: String): String {
        if (roomString.isEmpty()) return roomString

        // Pattern to match room codes like HOR-135, A1.2.03, etc.
        val roomPattern = Regex("([A-Z]+(?:\\d+)?[-.]\\d+(?:\\.\\d+)?)")

        val matches = roomPattern.findAll(roomString)
        val rooms = matches.map { it.value }.toList()

        return if (rooms.size > 1) {
            rooms.joinToString(", ")
        } else {
            roomString
        }
    }

    /**
     * Utility method to make absolute URLs
     */
    private fun makeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = java.net.URL(baseUrl)
            java.net.URL(base, relativeUrl).toString()
        } catch (e: Exception) {
            Log.e("DualisHtmlParser", "Error making absolute URL: $e")
            ""
        }
    }
}
