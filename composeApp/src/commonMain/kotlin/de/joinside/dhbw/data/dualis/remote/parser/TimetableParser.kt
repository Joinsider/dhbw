package de.joinside.dhbw.data.dualis.remote.parser

import de.joinside.dhbw.data.dualis.remote.parser.temp_models.TempLectureModel
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * Parser for Dualis timetable HTML pages.
 * This class ONLY parses HTML - it does NOT make any API calls.
 * The service layer is responsible for fetching HTML and coordinating parsing.
 */
class TimetableParser {

    companion object {
        private const val TAG = "TimetableParser"
        private const val BASE_URL = "https://dualis.dhbw.de"
    }

    /**
     * Parse the weekly timetable HTML and extract temporary lecture models.
     * These models contain basic info from the weekly view and links to individual pages.
     *
     * Example structure from timetable-week.html:
     * ```
     * <td class="appointment" style="background-color:#FFFFFF;" rowspan="15" abbr="Montag Spalte 1">
     *     <span style="font:9px Arial;" class="timePeriod">
     *         08:15 - 12:00
     *         HOR-120
     *     </span>
     *     <br />
     *     <a href="/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=COURSEPREP&ARGUMENTS=..." class="link" title="Paralleles Programmieren  HOR-TINF2024">
     *         T4INF2904.1
     *     </a>
     * </td>
     * ```
     *
     * @param htmlContent The HTML content of the weekly timetable page
     * @return List of temporary lecture models with basic information and links
     */
    fun parseWeeklyView(htmlContent: String): List<TempLectureModel> {
        Napier.d("Parsing lectures from weekly view HTML", tag = TAG)

        val lectures = mutableListOf<TempLectureModel>()

        try {
            // Log a sample of the HTML around the weekday class to help debug
            val weekdayIndex = htmlContent.indexOf("class=\"weekday\"")
            if (weekdayIndex >= 0) {
                val start = maxOf(0, weekdayIndex - 100)
                val end = minOf(htmlContent.length, weekdayIndex + 300)
                val sample = htmlContent.substring(start, end)
                Napier.d("HTML sample around weekday header: $sample", tag = TAG)
            } else {
                Napier.w("No 'class=\"weekday\"' found in HTML!", tag = TAG)
                // Log first 500 chars to see what we're getting
                Napier.d("HTML start: ${htmlContent.take(500)}", tag = TAG)
            }

            // Extract all weekday dates from headers
            // Format: <th class="weekday"><a href="...">Mo 03.11.</a></th>
            val weekDates = extractWeekDates(htmlContent)
            Napier.d("Extracted week dates: $weekDates", tag = TAG)

            // Pattern to match appointment cells WITH the abbr attribute to determine the day
            val appointmentPattern = """<td class="appointment"[^>]*abbr="([^"]*)"[^>]*>([\s\S]*?)</td>""".toRegex()

            val matches = appointmentPattern.findAll(htmlContent)

            for (match in matches) {
                val abbr = match.groupValues[1] // e.g., "Montag Spalte 1"
                val cellContent = match.groupValues[2]

                try {
                    val tempLecture = parseLectureCell(cellContent, abbr, weekDates)
                    if (tempLecture != null) {
                        lectures.add(tempLecture)
                        Napier.d("Parsed lecture: ${tempLecture.shortSubjectName} on ${tempLecture.startTime}", tag = TAG)
                    }
                } catch (e: Exception) {
                    Napier.w("Failed to parse lecture cell: ${e.message}", tag = TAG)
                }
            }

            Napier.d("Successfully parsed ${lectures.size} lectures from weekly view", tag = TAG)
        } catch (e: Exception) {
            Napier.e("Error parsing weekly view: ${e.message}", e, tag = TAG)
        }

        return lectures
    }

    /**
     * Extract week dates from table headers.
     * Returns a map of day name to LocalDateTime.
     */
    @OptIn(ExperimentalTime::class)
    private fun extractWeekDates(htmlContent: String): Map<String, LocalDateTime> {
        val weekDates = mutableMapOf<String, LocalDateTime>()

        // Pattern: <th class="weekday" ...><a ...>Mo 03.11.</a></th>
        // More lenient pattern to handle variations in HTML formatting
        val headerPattern = """<th\s+class="weekday"[^>]*>\s*<a[^>]*>\s*(Mo|Di|Mi|Do|Fr|Sa|So)\s+(\d{2})\.(\d{2})\.\s*</a>\s*</th>""".toRegex()
        val matches = headerPattern.findAll(htmlContent)

        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentYear = now.year

        Napier.d("Attempting to extract week dates from HTML (length: ${htmlContent.length})", tag = TAG)

        var matchCount = 0
        for (match in matches) {
            val dayAbbr = match.groupValues[1] // Mo, Di, Mi, etc.
            val day = match.groupValues[2].toInt()
            val month = match.groupValues[3].toInt()

            // Map German day abbreviations to full names
            val fullDayName = when (dayAbbr) {
                "Mo" -> "Montag"
                "Di" -> "Dienstag"
                "Mi" -> "Mittwoch"
                "Do" -> "Donnerstag"
                "Fr" -> "Freitag"
                "Sa" -> "Samstag"
                "So" -> "Sonntag"
                else -> dayAbbr
            }

            val dateTime = LocalDateTime(currentYear, Month(month), day, 0, 0)
            weekDates[fullDayName] = dateTime
            matchCount++
            Napier.d("Mapped $fullDayName -> $dateTime", tag = TAG)
        }

        if (matchCount == 0) {
            Napier.w("No week dates found in HTML! Trying alternative pattern...", tag = TAG)

            // Try alternative pattern without strict whitespace requirements
            val alternativePattern = """class="weekday"[^>]*>.*?>(Mo|Di|Mi|Do|Fr|Sa|So)\s+(\d{2})\.(\d{2})\.""".toRegex()
            val altMatches = alternativePattern.findAll(htmlContent)

            for (match in altMatches) {
                val dayAbbr = match.groupValues[1]
                val day = match.groupValues[2].toInt()
                val month = match.groupValues[3].toInt()

                val fullDayName = when (dayAbbr) {
                    "Mo" -> "Montag"
                    "Di" -> "Dienstag"
                    "Mi" -> "Mittwoch"
                    "Do" -> "Donnerstag"
                    "Fr" -> "Freitag"
                    "Sa" -> "Samstag"
                    "So" -> "Sonntag"
                    else -> dayAbbr
                }

                val dateTime = LocalDateTime(currentYear, Month(month), day, 0, 0)
                weekDates[fullDayName] = dateTime
                Napier.d("Mapped (alternative) $fullDayName -> $dateTime", tag = TAG)
            }
        }

        return weekDates
    }

    /**
     * Parse a single lecture appointment cell from the weekly timetable.
     */
    private fun parseLectureCell(
        cellContent: String,
        abbr: String,
        weekDates: Map<String, LocalDateTime>
    ): TempLectureModel? {
        try {
            // Extract time period (e.g., "08:15 - 12:00")
            val timePattern = """(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""".toRegex()
            val timeMatch = timePattern.find(cellContent) ?: return null

            val startHour = timeMatch.groupValues[1].toInt()
            val startMinute = timeMatch.groupValues[2].toInt()
            val endHour = timeMatch.groupValues[3].toInt()
            val endMinute = timeMatch.groupValues[4].toInt()

            // Extract location (appears after time, before <br />)
            val locationLines = cellContent.substringAfter(timeMatch.value).substringBefore("<br")
            // Remove any HTML tags including </span>
            val location = locationLines
                .replace("""<[^>]*>""".toRegex(), "")  // Remove all HTML tags
                .trim()
                .takeIf { it.isNotEmpty() } ?: "Unknown"

            // Extract short subject name and link from <a> tag
            val linkPattern = """<a\s+href="([^"]*)"[^>]*title="([^"]*)"[^>]*>\s*([^<]+)\s*</a>""".toRegex()
            val linkMatch = linkPattern.find(cellContent) ?: return null

            val linkPath = linkMatch.groupValues[1]
            val fullTitle = linkMatch.groupValues[2] // e.g., "Paralleles Programmieren  HOR-TINF2024"
            val shortSubjectName = linkMatch.groupValues[3].trim() // e.g., "T4INF2904.1"

            // Build full link
            val fullLink = if (linkPath.startsWith("http")) {
                linkPath
            } else if (linkPath.startsWith("/")) {
                "$BASE_URL$linkPath"
            } else {
                "$BASE_URL/$linkPath"
            }

            // Extract day from abbr (e.g., "Montag Spalte 1" -> "Montag")
            val dayName = abbr.substringBefore(" Spalte").substringBefore(" spalte")
            val baseDate = weekDates[dayName]

            if (baseDate == null) {
                Napier.w("Could not find date for day: $dayName (abbr: $abbr)", tag = TAG)
                return null
            }

            val startTime = LocalDateTime(
                year = baseDate.year,
                month = baseDate.month,
                day = baseDate.day,
                hour = startHour,
                minute = startMinute
            )

            val endTime = LocalDateTime(
                year = baseDate.year,
                month = baseDate.month,
                day = baseDate.day,
                hour = endHour,
                minute = endMinute
            )

            // Check if this is a test (from the yellow background or title)
            val isTest = cellContent.contains("background-color:#FFFF00", ignoreCase = true) ||
                        fullTitle.contains("klausur", ignoreCase = true) ||
                        fullTitle.contains("prüfung", ignoreCase = true)

            return TempLectureModel(
                shortSubjectName = shortSubjectName,
                fullSubjectName = fullTitle.takeIf { it != shortSubjectName },
                linkToIndividualPage = fullLink,
                startTime = startTime,
                endTime = endTime,
                location = location,
                isTest = isTest
            )
        } catch (e: Exception) {
            Napier.w("Error parsing lecture cell: ${e.message}", tag = TAG)
            return null
        }
    }


    /**
     * Parse the individual lecture page HTML and extract detailed information.
     *
     * Example structure from lecture-individual.html:
     * ```
     * <h1>T3INF2002.1&nbsp; Form. Sp+Autom.1+2 Gr. B  HOR-TINF2024</h1>
     * <p><span name="appointmentDate">Mi, 5. Nov. 2025</span>&nbsp;...
     *    <span name="appointmentTimeFrom">09:15</span> -
     *    <span name="appointmentTimeTo">10:45 Uhr</span>
     * </p>
     * <h2>Räume:</h2>
     * <span name="appoinmentRooms">HOR-120</span>
     * <span name="appoinmentRooms">HOR-133</span>
     * ...
     * <td class="tbdata" style="text-align:center;" name="instructorName">B.Sc. Julian Schmidt</td>
     * ```
     *
     * @param htmlContent The HTML content of the individual lecture page
     * @return Pair of (fullSubjectName, list of lecturer names) or null if parsing failed
     */
    fun parseIndividualPage(htmlContent: String): Pair<String, List<String>>? {
        try {
            // Extract full subject name from <h1> tag
            val subjectPattern = """<h1>\s*[^&]+&nbsp;\s*([^<]+?)\s+HOR-[^<]*</h1>""".toRegex()
            val subjectMatch = subjectPattern.find(htmlContent)
            var fullSubjectName = subjectMatch?.groupValues?.get(1)?.trim()?.replace("&nbsp;", " ")

            // Remove HOR-*** e.g. HOR-TINF2024 from fullSubjectName
            fullSubjectName = fullSubjectName?.replace(Regex("""HOR-\w+"""), "")?.trim()


            if (fullSubjectName == null) {
                Napier.w("Could not extract full subject name from individual page", tag = TAG)
                return null
            }

            // Extract lecturer names from table data with name="instructorName"
            val lecturerPattern = """<td[^>]*name="instructorName"[^>]*>\s*(?:<!--.*?-->)?\s*([^<]+?)\s*</td>""".toRegex()
            val lecturerMatches = lecturerPattern.findAll(htmlContent)
            val lecturers = lecturerMatches
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() && !it.contains("standardLink undef") }
                .toList()

            Napier.d("Parsed individual page: '$fullSubjectName' with ${lecturers.size} lecturer(s)", tag = TAG)

            return Pair(fullSubjectName, lecturers)
        } catch (e: Exception) {
            Napier.e("Error parsing individual lecture page: ${e.message}", e, tag = TAG)
            return null
        }
    }
}