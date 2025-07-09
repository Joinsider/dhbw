package de.fampopprol.dhbwhorb.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.dualis.models.DualisUrl
import de.fampopprol.dhbwhorb.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.dualis.models.TimetableEvent
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

    fun login(user: String, pass: String, callback: (String?) -> Unit) {
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
        val monthlyScheduleElement = document.select("a:contains(Stundenplan)").first()
        _dualisUrls.monthlyScheduleUrl = monthlyScheduleElement?.attr("href")?.let { if (it.startsWith("/")) dualisEndpoint + it else it }

        // Extracting logout URL
        val logoutElement = document.select("a:contains(Abmelden)").first()
        _dualisUrls.logoutUrl = logoutElement?.attr("href")?.let { if (it.startsWith("/")) dualisEndpoint + it else it }

        Log.d("DualisService", "Parsed Dualis URLs: $_dualisUrls")
    }

    fun getMonthlySchedule(year: Int, month: Int, callback: (List<TimetableDay>?) -> Unit) {
        if (_dualisUrls.monthlyScheduleUrl == null || _authToken == null) {
            Log.e("DualisService", "Monthly schedule URL or Auth Token is null. Cannot fetch timetable.")
            callback(null)
            return
        }

        val url = _fillUrlWithAuthToken("${_dualisUrls.monthlyScheduleUrl}01.$month.$year")

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

    private fun parseMonthlySchedule(html: String): List<TimetableDay> {
        val document = Jsoup.parse(html)
        val timetableDays = mutableListOf<TimetableDay>()

        val dayElements = document.select("table.plan_table tr")

        var currentDate = ""
        for (element in dayElements) {
            val dateHeader = element.select("td.plan_header").first()
            if (dateHeader != null) {
                currentDate = dateHeader.text().trim()
            }

            val eventElements = element.select("td.plan_item")
            if (eventElements.isNotEmpty()) {
                val events = mutableListOf<TimetableEvent>()
                for (eventElement in eventElements) {
                    val title = eventElement.select("b").first()?.text() ?: ""
                    val details = eventElement.html().replace("<b>" + title + "</b><br>", "").split("<br>")

                    val time = details.getOrNull(0)?.trim() ?: ""
                    val location = details.getOrNull(1)?.trim() ?: ""
                    val lecturer = details.getOrNull(2)?.trim() ?: ""

                    events.add(TimetableEvent(title, time, location, lecturer))
                }
                timetableDays.add(TimetableDay(currentDate, events))
            }
        }
        return timetableDays
    }

    private fun isMainPage(html: String): Boolean {
        val document = Jsoup.parse(html)
        return document.select("a:contains(Studienleistungen)").first() != null ||
               document.select("a:contains(Prüfungsergebnisse)").first() != null ||
               document.select("a:contains(Stundenplan)").first() != null ||
               document.select("a:contains(Abmelden)").first() != null
    }
}
