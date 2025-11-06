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
            // Extract the date range from caption (e.g., "Stundenplan vom 03.11. bis 09.11.")
            val captionPattern = """<caption>\s*Stundenplan vom (\d{2}\.\d{2}\.) bis (\d{2}\.\d{2}\.)\s*</caption>""".toRegex()
            val captionMatch = captionPattern.find(htmlContent)
            val weekStart = captionMatch?.groupValues?.get(1)
            Napier.d("Parsing week starting: $weekStart", tag = TAG)

            // Pattern to match appointment cells with lecture information
            val appointmentPattern = """<td class="appointment"[^>]*>([\s\S]*?)</td>""".toRegex()

            val matches = appointmentPattern.findAll(htmlContent)

            for (match in matches) {
                val cellContent = match.groupValues[1]

                try {
                    val tempLecture = parseLectureCell(cellContent, htmlContent)
                    if (tempLecture != null) {
                        lectures.add(tempLecture)
                        Napier.d("Parsed lecture: ${tempLecture.shortSubjectName} at ${tempLecture.startTime}", tag = TAG)
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
     * Parse a single lecture appointment cell from the weekly timetable.
     */
    private fun parseLectureCell(cellContent: String, fullHtml: String): TempLectureModel? {
        try {
            // Extract time period (e.g., "08:15 - 12:00")
            val timePattern = """(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""".toRegex()
            val timeMatch = timePattern.find(cellContent)

            if (timeMatch == null) {
                return null
            }

            val startHour = timeMatch.groupValues[1].toInt()
            val startMinute = timeMatch.groupValues[2].toInt()
            val endHour = timeMatch.groupValues[3].toInt()
            val endMinute = timeMatch.groupValues[4].toInt()

            // Extract location (appears after time, before <br />)
            val locationLines = cellContent.substringAfter(timeMatch.value).substringBefore("<br")
            val location = locationLines.trim().takeIf { it.isNotEmpty() } ?: "Unknown"

            // Extract short subject name and link from <a> tag
            val linkPattern = """<a\s+href="([^"]*)"[^>]*title="([^"]*)"[^>]*>\s*([^<]+)\s*</a>""".toRegex()
            val linkMatch = linkPattern.find(cellContent)

            if (linkMatch == null) {
                return null
            }

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

            // Extract date - need to find which day column this is in
            val date = extractDateFromContext(cellContent, fullHtml)

            val startTime = LocalDateTime(
                year = date.year,
                month = date.month,
                day = date.day,
                hour = startHour,
                minute = startMinute
            )

            val endTime = LocalDateTime(
                year = date.year,
                month = date.month,
                day = date.day,
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
     * Extract date from the week table header based on context.
     * Looks for patterns like "Mo 03.11.", "Di 04.11.", etc.
     */
    @OptIn(ExperimentalTime::class)
    private fun extractDateFromContext(@Suppress("UNUSED_PARAMETER") cellContent: String, fullHtml: String): LocalDateTime {
        // Try to extract from the table headers
        val headerPattern = """<th class="weekday"[^>]*><a[^>]*>(?:Mo|Di|Mi|Do|Fr|Sa|So) (\d{2})\.(\d{2})\.</a></th>""".toRegex()
        val matches = headerPattern.findAll(fullHtml)

        // For now, use the first date found or current date as fallback
        val firstMatch = matches.firstOrNull()
        if (firstMatch != null) {
            val day = firstMatch.groupValues[1].toInt()
            val month = firstMatch.groupValues[2].toInt()
            val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val year = now.year // Assume current year

            return LocalDateTime(year, Month(month), day, 0, 0)
        }

        // Fallback to current date
        return kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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
            val subjectPattern = """<h1>\s*([^<]+?)\s*</h1>""".toRegex()
            val subjectMatch = subjectPattern.find(htmlContent)
            val fullSubjectName = subjectMatch?.groupValues?.get(1)?.trim()?.replace("&nbsp;", " ")

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