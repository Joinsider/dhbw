package de.fampopprol.dhbwhorb.dualis.network

import android.annotation.SuppressLint
import android.util.Log
import de.fampopprol.dhbwhorb.dualis.models.dualis.DualisUrl
import de.fampopprol.dhbwhorb.dualis.models.timetable.TimetableDay
import de.fampopprol.dhbwhorb.dualis.models.timetable.TimetableEvent
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.JavaNetCookieJar
import java.net.CookieManager
import java.net.CookiePolicy
import java.io.IOException
import org.jsoup.Jsoup

class DualisService {

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .build()
    private val _tokenRegex = Regex("ARGUMENTS=-N([0-9]{15})")

    private var _authToken: String? = null
    private var _dualisUrls: DualisUrl = DualisUrl()
    private var _lastLoginCredentials: Pair<String, String>? = null
    private var _isReAuthenticating = false

    fun login(user: String, pass: String, callback: (String?) -> Unit) {
        // Store credentials for potential re-authentication
        _lastLoginCredentials = Pair(user, pass)

        val formBody = FormBody.Builder()
            .add("usrname", user)
            .add("pass", pass)
            .add("APPNAME", "CampusNet")
            .add("PRGNAME", "LOGINCHECK")
            .add("ARGUMENTS", "clino,usrname,pass,menuno,menu_type,browser,platform")
            .add("clino", "000000000000001")
            .add("menuno", "000324")
            .add("menu_type", "classic")
            .add("browser", "")
            .add("platform", "")
            .build()

        val request = Request.Builder()
            .url("https://dualis.dhbw.de/scripts/mgrqispi.dll")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DualisService", "Login request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("DualisService", "Login Response: ${response.code} - $responseBody")

                if (response.isSuccessful) {
                    val redirectUrlHeader = response.header("refresh")
                    if (redirectUrlHeader != null) {
                        val dualisEndpoint = "https://dualis.dhbw.de"
                        val redirectUrlPart = if (redirectUrlHeader.contains("URL=")) {
                            redirectUrlHeader.substring(redirectUrlHeader.indexOf("URL=") + "URL=".length)
                        } else {
                            redirectUrlHeader
                        }
                        val absoluteRedirectUrl = makeAbsoluteUrl(dualisEndpoint, redirectUrlPart)
                        _updateAccessToken(absoluteRedirectUrl)
                        followRedirects(absoluteRedirectUrl) { realMainPageContent ->
                            if (realMainPageContent != null) {
                                try {
                                    parseRealMainPage(realMainPageContent)
                                    callback("Login successful") // Indicate success
                                } catch (e: Exception) {
                                    Log.e("DualisService", "Error parsing real main page", e)
                                    callback(null)
                                }
                            } else {
                                Log.e("DualisService", "Real main page content is null after following redirects")
                                callback(null)
                            }
                        }
                    } else {
                        Log.e("DualisService", "Redirect URL is null")
                        callback(null)
                    }
                } else {
                    Log.e("DualisService", "Login failed with code: ${response.code}")
                    callback(null)
                }
            }
        })
    }

    private fun followRedirects(url: String, callback: (String?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DualisService", "Follow redirects request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("DualisService", "Follow Redirects Response: ${response.code} - $responseBody")

                if (response.isSuccessful && responseBody != null) {
                    val document = Jsoup.parse(responseBody)
                    val isRedirectPage = document.select("div#sessionId").first() != null
                    Log.d("DualisService", "isRedirectPage check: $isRedirectPage")
                    Log.d("DualisService", "isMainPage check: ${isMainPage(responseBody)}")

                    if (isRedirectPage) {
                        // Extract the next redirect URL
                        var nextRedirectUrl: String? = null

                        // Try to get from script first
                        for (element in document.select("script")) {
                            val content = element.html()
                            if (content.contains("window.location.href")) {
                                val regex = Regex("window\\.location\\.href\\s*=\\s*['\"]([^'\"]+)['\"]")
                                val match = regex.find(content)
                                val relativeUrl = match?.groupValues?.get(1)
                                if (relativeUrl != null) {
                                    nextRedirectUrl = makeAbsoluteUrl(url, relativeUrl)
                                    break
                                }
                            }
                        }

                        // If not found in script, try from the <a> tag
                        if (nextRedirectUrl == null) {
                            val anchorElement = document.select("h2 a[href]").first()
                            val relativeUrl = anchorElement?.attr("href")
                            if (relativeUrl != null) {
                                nextRedirectUrl = makeAbsoluteUrl(url, relativeUrl)
                            }
                        }

                        if (nextRedirectUrl != null) {
                            Log.d("DualisService", "Following redirect to: $nextRedirectUrl")
                            followRedirects(nextRedirectUrl, callback) // Recursive call
                        } else {
                            Log.e("DualisService", "Could not find next redirect URL in redirect page")
                            callback(null)
                        }
                    } else if (isMainPage(responseBody)) {
                        // This is the real main page
                        callback(responseBody)
                    } else {
                        Log.e("DualisService", "Unexpected page content, not a main page or known redirect page.")
                        callback(null)
                    }
                } else {
                    Log.e("DualisService", "Follow redirects failed with code: ${response.code}")
                    callback(null)
                }
            }
        })
    }

    private fun makeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = java.net.URL(baseUrl)
            java.net.URL(base, relativeUrl).toString()
        } catch (e: Exception) {
            Log.e("DualisService", "Error making absolute URL: $e")
            ""
        }
    }

    private fun _updateAccessToken(urlWithNewToken: String) {
        val tokenMatch = _tokenRegex.find(urlWithNewToken)
        if (tokenMatch != null) {
            _authToken = tokenMatch.groupValues[1]
            Log.d("DualisService", "Updated Auth Token: $_authToken")
        } else {
            Log.e("DualisService", "Auth token not found in URL: $urlWithNewToken")
        }
    }

    private fun _fillUrlWithAuthToken(url: String): String {
        val match = _tokenRegex.find(url)
        return if (match != null && _authToken != null) {
            val newUrl = url.replaceRange(match.range.first, match.range.last, "ARGUMENTS=-N$_authToken")
            Log.d("DualisService", "Filled URL with Auth Token: $newUrl")
            newUrl
        } else {
            Log.w("DualisService", "Could not fill URL with auth token. URL: $url, AuthToken: $_authToken")
            url
        }
    }

    private fun parseRealMainPage(html: String) {
        val document = Jsoup.parse(html)

        val dualisEndpoint = "https://dualis.dhbw.de"

        // Extracting student results URL
        val studentResultsElement = document.select("a:contains(Studienleistungen)").first()
        _dualisUrls.studentResultsUrl = studentResultsElement?.attr("href")?.let { if (it.startsWith("/")) dualisEndpoint + it else it }

        // Extracting course result URL
        val courseResultElement = document.select("a:contains(Prüfungsergebnisse)").first()
        _dualisUrls.courseResultUrl = courseResultElement?.attr("href")?.let { if (it.startsWith("/")) dualisEndpoint + it else it }

        // Extracting monthly schedule URL
        val monthlyScheduleElement = document.select("a:contains(diese Woche)").first()
        _dualisUrls.monthlyScheduleUrl = monthlyScheduleElement?.attr("href")?.let { if (it.startsWith("/")) dualisEndpoint + it else it }

        // Extracting logout URL
        val logoutElement = document.select("a:contains(Abmelden)").first()
        _dualisUrls.logoutUrl = logoutElement?.attr("href")?.let { if (it.startsWith("/")) dualisEndpoint + it else it }

        Log.d("DualisService", "Parsed Dualis URLs: $_dualisUrls")
    }

    @SuppressLint("DefaultLocale")
    fun getMonthlySchedule(year: Int, month: Int, callback: (List<TimetableDay>?) -> Unit) {
        if (_dualisUrls.monthlyScheduleUrl == null || _authToken == null) {
            Log.e("DualisService", "Monthly schedule URL or Auth Token is null. Cannot fetch timetable.")
            callback(null)
            return
        }

        val baseUrl = _dualisUrls.monthlyScheduleUrl!!
        val authToken = _authToken!!

        // Extract existing arguments
        val argumentsRegex = Regex("ARGUMENTS=([^&]+)")
        val existingArgumentsMatch = argumentsRegex.find(baseUrl)
        val existingArguments = existingArgumentsMatch?.groupValues?.get(1) ?: ""

        // Format the date as dd.MM.yyyy for the first day of the month
        val formattedDate = String.format("%02d.%02d.%d", 1, month, year)

        // Replace the first -A with the formatted date
        val updatedArguments = existingArguments.replaceFirst("-A", "-A$formattedDate")

        // Reconstruct the URL
        val url = baseUrl.replace(existingArguments, updatedArguments)
            .replace("ARGUMENTS=-N$authToken", "ARGUMENTS=-N$authToken") // Ensure auth token is correct
        Log.d("DualisService", "Constructed Monthly Schedule URL: $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DualisService", "Get monthly schedule request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("DualisService", "Monthly Schedule Response: ${response.code} - $responseBody")

                if (response.isSuccessful) {
                    try {
                        val timetableDays = parseMonthlySchedule(responseBody ?: "")
                        callback(timetableDays)
                    } catch (e: Exception) {
                        Log.e("DualisService", "Error parsing monthly schedule", e)
                        callback(null)
                    }
                } else {
                    Log.e("DualisService", "Get monthly schedule failed with code: ${response.code}")
                    callback(null)
                }
            }
        })
    }

    fun getWeeklySchedule(targetDate: java.time.LocalDate, callback: (List<TimetableDay>?) -> Unit) {
        getWeeklyScheduleWithRetry(targetDate, callback, retryCount = 0)
    }

    private fun getWeeklyScheduleWithRetry(targetDate: java.time.LocalDate, callback: (List<TimetableDay>?) -> Unit, retryCount: Int) {
        if (_dualisUrls.monthlyScheduleUrl == null || _authToken == null) {
            Log.e("DualisService", "Monthly schedule URL or Auth Token is null. Cannot fetch weekly timetable.")
            callback(null)
            return
        }

        val baseUrl = _dualisUrls.monthlyScheduleUrl!!
        val authToken = _authToken!!

        // Extract existing arguments
        val argumentsRegex = Regex("ARGUMENTS=([^&]+)")
        val existingArgumentsMatch = argumentsRegex.find(baseUrl)
        val existingArguments = existingArgumentsMatch?.groupValues?.get(1) ?: ""

        // Format the target date as dd.MM.yyyy
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val formattedDate = targetDate.format(dateFormatter)

        // Replace the first -A with the formatted target date
        val updatedArguments = existingArguments.replaceFirst("-A", "-A$formattedDate")

        // Reconstruct the URL
        val url = baseUrl.replace(existingArguments, updatedArguments)
            .replace("ARGUMENTS=-N$authToken", "ARGUMENTS=-N$authToken") // Ensure auth token is correct
        Log.d("DualisService", "Constructed Weekly Schedule URL for $targetDate: $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DualisService", "Get weekly schedule request failed", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("DualisService", "Weekly Schedule Response: ${response.code}")

                if (response.isSuccessful && responseBody != null) {
                    // Check if the response indicates an invalid token
                    if (isTokenInvalidResponse(responseBody)) {
                        Log.w("DualisService", "Token appears to be invalid, attempting re-authentication")
                        if (retryCount < 1) { // Only retry once
                            reAuthenticateIfNeeded { success ->
                                if (success) {
                                    Log.d("DualisService", "Re-authentication successful, retrying weekly schedule fetch")
                                    getWeeklyScheduleWithRetry(targetDate, callback, retryCount + 1)
                                } else {
                                    Log.e("DualisService", "Re-authentication failed")
                                    callback(null)
                                }
                            }
                        } else {
                            Log.e("DualisService", "Already retried once, giving up")
                            callback(null)
                        }
                        return
                    }

                    try {
                        val timetableDays = parseMonthlySchedule(responseBody)
                        Log.d("DualisService", "Parsed weekly schedule for $targetDate: ${timetableDays.size} days")
                        callback(timetableDays)
                    } catch (e: Exception) {
                        Log.e("DualisService", "Error parsing weekly schedule", e)
                        callback(null)
                    }
                } else {
                    Log.e("DualisService", "Get weekly schedule failed with code: ${response.code}")
                    callback(null)
                }
            }
        })
    }

    private fun parseMonthlySchedule(html: String): List<TimetableDay> {
        val document = Jsoup.parse(html)
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

        val table = document.select("table.nb").first() ?: return emptyList()
        val caption = table.select("caption").first()?.text()
        Log.d("DualisService", "Table caption: $caption")

        val dateRangeRegex = Regex("Stundenplan vom (\\d{2}\\.\\d{2}\\.) bis (\\d{2}\\.\\d{2}\\.)")
        val matchResult = caption?.let { dateRangeRegex.find(it) }

        val startDateString = matchResult?.groupValues?.get(1)
        val endDateString = matchResult?.groupValues?.get(2)
        Log.d("DualisService", "Start date string: $startDateString, End date string: $endDateString")

        val currentYear = java.time.LocalDate.now().year

        val startLocalDate = requireNotNull(startDateString?.let { java.time.LocalDate.parse(it + currentYear, dateFormatter) }) {
            "Could not parse start date from caption: $caption"
        }
        val endLocalDate = requireNotNull(endDateString?.let { java.time.LocalDate.parse(it + currentYear, dateFormatter) }) {
            "Could not parse end date from caption: $caption"
        }

        // Find the header row with weekday columns
        val headerRow = table.select("tr.tbsubhead").first() ?: return emptyList()
        val dayToDateMap = mutableMapOf<String, java.time.LocalDate>()

        // Parse dates from table headers directly - look for th.weekday elements with links
        headerRow.select("th.weekday").forEach { dayHeaderElement ->
            val link = dayHeaderElement.select("a").first()
            val headerText = link?.text()?.trim() ?: dayHeaderElement.text().trim()
            Log.d("DualisService", "Processing header: '$headerText'")

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
                        Log.w("DualisService", "Unknown day abbreviation: $dayAbbreviation")
                        ""
                    }
                }

                if (fullDayName.isNotEmpty()) {
                    try {
                        val parsedDate = java.time.LocalDate.parse(dateString, dateFormatter)
                        dayToDateMap[fullDayName] = parsedDate
                        Log.d("DualisService", "Mapped $fullDayName to $parsedDate")
                    } catch (e: Exception) {
                        Log.e("DualisService", "Error parsing date: $dateString", e)
                    }
                }
            } else {
                Log.w("DualisService", "Could not parse header: '$headerText'")
            }
        }

        // If no headers were found with the standard approach, try extracting directly from the range
        if (dayToDateMap.isEmpty()) {
            Log.w("DualisService", "No dates found in headers, trying to map from date range")

            // Create date mapping based on the date range from caption
            var currentDate = startLocalDate
            val weekDays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

            while (!currentDate.isAfter(endLocalDate)) {
                val dayOfWeek = currentDate.dayOfWeek.value // 1 = Monday, 7 = Sunday
                val dayName = weekDays[dayOfWeek - 1]
                dayToDateMap[dayName] = currentDate
                Log.d("DualisService", "Fallback mapped $dayName to $currentDate")
                currentDate = currentDate.plusDays(1)
            }
        }

        Log.d("DualisService", "Day To Date Map: $dayToDateMap")

        val eventsByFullDate = mutableMapOf<java.time.LocalDate, MutableList<TimetableEvent>>()
        var currentDay = startLocalDate
        while (!currentDay.isAfter(endLocalDate)) {
            eventsByFullDate[currentDay] = mutableListOf()
            currentDay = currentDay.plusDays(1)
        }

        val allAppointmentCells = document.select("td.appointment")
        Log.d("DualisService", "Found ${allAppointmentCells.size} appointment cells")

        for (cell in allAppointmentCells) {
            val cellHtml = cell.html()
            Log.d("DualisService", "Processing appointment cell HTML: $cellHtml")

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
                Log.d("DualisService", "Skipping cell with empty title")
                continue
            }

            // Extract time and room information
            val timePeriodSpan = cell.select("span.timePeriod").first()
            val timePeriodText = timePeriodSpan?.text()?.trim() ?: ""

            Log.d("DualisService", "Time period text: '$timePeriodText'")

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
                    room = timeRoomParts.drop(3).joinToString(" ")
                }
            }

            val lecturer = "" // Lecturer is not explicitly available in the current HTML structure

            // Get the day from the abbr attribute
            val abbrAttribute = cell.attr("abbr")
            val dayOfWeekInGerman = abbrAttribute.split(" ")[0]

            val eventDate = dayToDateMap[dayOfWeekInGerman]

            Log.d("DualisService", "Processing cell:")
            Log.d("DualisService", "  Title: '$title'")
            Log.d("DualisService", "  Time Period Text: '$timePeriodText'")
            Log.d("DualisService", "  Time Room Parts: $timeRoomParts")
            Log.d("DualisService", "  Start Time: '$startTime', End Time: '$endTime', Room: '$room'")
            Log.d("DualisService", "  Abbr Attribute: '$abbrAttribute', Day in German: '$dayOfWeekInGerman'")
            Log.d("DualisService", "  Event Date: $eventDate")

            if (eventDate != null && title.isNotEmpty()) {
                eventsByFullDate[eventDate]?.add(TimetableEvent(title, startTime, endTime, room, lecturer))
                Log.d("DualisService", "Added event to date $eventDate: $title")
            } else {
                Log.w("DualisService", "Skipping event - eventDate: $eventDate, title: '$title'")
            }
        }

        val sortedTimetableDays = eventsByFullDate.entries
            .sortedBy { it.key }
            .map { entry ->
                Log.d("DualisService", "Creating TimetableDay for ${dateFormatter.format(entry.key)} with ${entry.value.size} events")
                TimetableDay(dateFormatter.format(entry.key), entry.value)
            }

        Log.d("DualisService", "Parsed ${sortedTimetableDays.size} timetable days")
        sortedTimetableDays.forEach { day ->
            Log.d("DualisService", "Day ${day.date}: ${day.events.size} events")
            day.events.forEach { event ->
                Log.d("DualisService", "  Event: ${event.title} (${event.startTime} - ${event.endTime}) in ${event.room}")
            }
        }

        return sortedTimetableDays
    }

    private fun isMainPage(html: String): Boolean {
        val document = Jsoup.parse(html)
        return document.select("a:contains(Studienleistungen)").first() != null ||
               document.select("a:contains(Prüfungsergebnisse)").first() != null ||
               document.select("a:contains(Stundenplan)").first() != null ||
               document.select("a:contains(Abmelden)").first() != null
    }

    private fun isTokenInvalidResponse(html: String): Boolean {
        // Check for common indicators of invalid session/token
        return html.contains("SESSION_EXPIRED") ||
               html.contains("INVALID_SESSION") ||
               html.contains("session has expired") ||
               html.contains("Sie müssen sich erneut anmelden") ||
               html.contains("Login") && html.contains("Anmeldung") ||
               html.contains("LOGINCHECK") ||
               html.contains("usrname") && html.contains("pass") ||
               html.isEmpty() ||
               html.contains("error") && html.contains("token")
    }

    private fun reAuthenticateIfNeeded(onComplete: (Boolean) -> Unit) {
        if (_isReAuthenticating) {
            // Already re-authenticating, wait for completion
            return
        }

        val credentials = _lastLoginCredentials
        if (credentials == null) {
            Log.e("DualisService", "No stored credentials for re-authentication")
            onComplete(false)
            return
        }

        _isReAuthenticating = true
        Log.d("DualisService", "Re-authenticating due to invalid token")

        login(credentials.first, credentials.second) { result ->
            _isReAuthenticating = false
            if (result != null) {
                Log.d("DualisService", "Re-authentication successful")
                onComplete(true)
            } else {
                Log.e("DualisService", "Re-authentication failed")
                onComplete(false)
            }
        }
    }
}
