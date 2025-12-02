package de.joinside.dhbw.data.dualis.remote.parser

import de.joinside.dhbw.data.storage.database.entities.grades.GradeEntity
import io.github.aakira.napier.Napier

class GradeParser {
    companion object {
        private const val TAG = "GradeParser"
    }

    private val rowPattern = """<tr\b[^>]*>([\s\S]*?)</tr>""".toRegex(RegexOption.IGNORE_CASE)
    private val tdPattern = """<td\b[^>]*>([\s\S]*?)</td>""".toRegex(RegexOption.IGNORE_CASE)
    private val scriptPattern = """<script[\s\S]*?</script>""".toRegex(RegexOption.IGNORE_CASE)
    private val htmlTagPattern = """<[^>]+>""".toRegex()

    /**
     * Extracts available semesters from the semester dropdown.
     * @return Map of Semester Name (key) -> Semester ID (value)
     */
    fun parseSemesterList(htmlContent: String): Map<String, String> {
        val semesters = mutableMapOf<String, String>()
        try {
            // Match <option value="000000015168000" selected="selected">WiSe 2025/26</option>
            // or <option value="000000015158000">SoSe 2025</option>
            val optionPattern = """<option\s+value="(\d+)"[^>]*>([^<]+)</option>""".toRegex()
            val matches = optionPattern.findAll(htmlContent)

            for (match in matches) {
                val id = match.groupValues[1]
                val name = match.groupValues[2].trim()
                semesters[name] = id
            }
            Napier.d("Parsed ${semesters.size} semesters", tag = TAG)
        } catch (e: Exception) {
            Napier.e("Error parsing semester list: ${e.message}", e, tag = TAG)
        }
        return semesters
    }

    /**
     * Parses grades from the table.
     * @param htmlContent The HTML content
     * @param studentId The student ID to associate with the grades
     * @param semesterId The semester ID
     * @param semesterName The semester name
     */
    fun parseGrades(
        htmlContent: String,
        studentId: String,
        semesterId: String,
        semesterName: String
    ): List<GradeEntity> {
        val grades = mutableListOf<GradeEntity>()
        try {
            for (rowMatch in rowPattern.findAll(htmlContent)) {
                val rowHtml = rowMatch.groupValues[1]

                if (rowHtml.contains("<th", ignoreCase = true) || rowHtml.contains("<td class=\"tbsubhead\"", ignoreCase = true)) {
                    // Skip header/footer rows like "Semester-GPA"
                    continue
                }

                val cells = tdPattern.findAll(rowHtml)
                    .map { it.groupValues[1] }
                    .toList()

                if (cells.size < 5) {
                    continue
                }

                val moduleNumber = normalizeCell(cells[0])
                if (moduleNumber.isBlank()) {
                    continue
                }
                val moduleName = normalizeCell(cells[1])
                val rawGrade = normalizeCell(cells[2])
                val sanitizedGrade = rawGrade.replace("noch nicht gesetzt", "", ignoreCase = true).trim()
                val finalGrade = sanitizedGrade.ifBlank { null }

                val creditsText = normalizeCell(cells[3]).replace("\u00A0", " ").replace(Regex("\\s+"), "").replace(',', '.')
                val credits = creditsText.toDoubleOrNull() ?: 0.0

                val statusText = normalizeCell(cells[4])
                val status = statusText.ifBlank { null }

                grades.add(
                    GradeEntity(
                        studentId = studentId,
                        semesterId = semesterId,
                        semesterName = semesterName,
                        moduleNumber = moduleNumber,
                        moduleName = moduleName,
                        grade = finalGrade,
                        credits = credits,
                        status = status
                    )
                )
            }
            Napier.d("Parsed ${grades.size} grades for semester $semesterName", tag = TAG)
        } catch (e: Exception) {
            Napier.e("Error parsing grades: ${e.message}", e, tag = TAG)
        }
        return grades
    }

    private fun normalizeCell(text: String): String {
        return scriptPattern
            .replace(text, "")
            .replace(htmlTagPattern, "")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
