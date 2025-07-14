/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.parser

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.StudyGrades
import de.fampopprol.dhbwhorb.data.dualis.models.Module
import de.fampopprol.dhbwhorb.data.dualis.models.ExamState
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parser to extract study grades information from Dualis HTML responses
 */
class StudyGradesParser {

    private val TAG = "StudyGradesParser"

    /**
     * Extracts study grades data from the student results page HTML
     */
    fun extractStudyGrades(html: String, semesterArgument: String = "-N000307,"): StudyGrades? {
        Log.d(TAG, "=== STARTING STUDY GRADES PARSING ===")
        Log.d(TAG, "HTML input length: ${html.length}")
        Log.d(TAG, "Semester argument: $semesterArgument")
        Log.d(TAG, "HTML preview (first 1000 chars):")
        Log.d(TAG, html.take(1000))
        Log.d(TAG, "HTML preview (last 500 chars):")
        Log.d(TAG, html.takeLast(500))

        return try {
            val document = Jsoup.parse(html)
            Log.d(TAG, "Document parsed successfully")

            // Look for the actual table structure - it's "nb list" not "tb"
            val moduleTable = document.select("table.nb.list").first()
            Log.d(TAG, "Found module table: ${moduleTable != null}")

            if (moduleTable == null) {
                Log.e(TAG, "Could not find module table with class 'nb list'")

                // Log all tables found for debugging
                val allTables = document.select("table")
                Log.d(TAG, "All tables found (${allTables.size} total):")
                allTables.forEachIndexed { index, table ->
                    val classes = table.attr("class")
                    val id = table.attr("id")
                    Log.d(TAG, "Table $index: class='$classes', id='$id'")
                    Log.d(TAG, "  Table content preview: ${table.text().take(200)}")
                }

                return null
            }

            Log.d(TAG, "Module table content: ${moduleTable.text()}")

            // Extract module data from the table
            val modules = parseModulesFromTable(moduleTable)
            Log.d(TAG, "Parsed ${modules.size} modules")

            // Calculate summary statistics from modules
            val studyGrades = calculateSummaryFromModules(modules, semesterArgument)

            Log.d(TAG, "=== STUDY GRADES PARSING COMPLETE ===")
            Log.d(TAG, "Final result: $studyGrades")
            studyGrades
        } catch (e: Exception) {
            Log.e(TAG, "=== PARSING ERROR ===")
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Error parsing study grades: ${e.message}", e)
            null
        }
    }

    /**
     * Parses individual modules from the table
     */
    private fun parseModulesFromTable(table: Element): List<ModuleData> {
        val modules = mutableListOf<ModuleData>()
        val rows = table.select("tbody tr")

        Log.d(TAG, "Found ${rows.size} rows in module table")

        rows.forEach { row ->
            val cells = row.select("td")
            if (cells.size >= 5) {
                try {
                    val moduleId = cells[0].text().trim()
                    val moduleName = cells[1].text().trim()
                    val gradeText = cells[2].text().trim()
                    val creditsText = cells[3].text().trim()
                    val status = cells[4].text().trim()

                    Log.d(TAG, "Parsing module: $moduleId - $moduleName - Grade: '$gradeText' - Credits: '$creditsText' - Status: '$status'")

                    val credits = parseCredits(creditsText) ?: 0.0
                    val grade = parseGrade(gradeText)

                    modules.add(ModuleData(
                        id = moduleId,
                        name = moduleName,
                        grade = grade,
                        credits = credits,
                        status = status
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing module row: ${e.message}")
                }
            }
        }

        return modules
    }

    /**
     * Calculates summary grades from individual modules
     */
    private fun calculateSummaryFromModules(modules: List<ModuleData>, semesterArgument: String = "-N000307,"): StudyGrades {
        var totalCredits = 0.0
        var gainedCredits = 0.0
        var weightedGradeSum = 0.0
        var gradeCount = 0

        // Convert ModuleData to Module objects
        val moduleList = modules.map { moduleData ->
            val examState = when {
                moduleData.status.contains("bestanden", ignoreCase = true) -> ExamState.PASSED
                moduleData.status.contains("nicht bestanden", ignoreCase = true) -> ExamState.FAILED
                moduleData.grade != null && moduleData.grade > 0.0 -> ExamState.PASSED
                else -> ExamState.PENDING
            }

            Module(
                id = moduleData.id,
                name = moduleData.name,
                credits = moduleData.credits.toString(),
                grade = moduleData.grade?.toString() ?: "noch nicht gesetzt",
                state = examState
            )
        }

        modules.forEach { module ->
            totalCredits += module.credits

            // Count as gained if status indicates completion or if grade is set
            if (module.status.contains("bestanden", ignoreCase = true) ||
                module.status.contains("Prüfungen", ignoreCase = true) ||
                (module.grade != null && module.grade > 0.0)) {
                gainedCredits += module.credits
            }

            // Include in GPA calculation if grade is numerical
            module.grade?.let { grade ->
                if (grade > 0.0) {
                    weightedGradeSum += grade * module.credits
                    gradeCount++
                }
            }
        }

        val gpaTotal = if (gainedCredits > 0.0) weightedGradeSum / gainedCredits else 0.0

        // Determine semester identifier
        val semester = when (semesterArgument) {
            "-N000307," -> "Current Semester"
            "-N000308," -> "Previous Semester"
            "-N000309," -> "Two Semesters Ago"
            else -> "Semester"
        }

        Log.d(TAG, "Summary calculation:")
        Log.d(TAG, "  Total Credits: $totalCredits")
        Log.d(TAG, "  Gained Credits: $gainedCredits")
        Log.d(TAG, "  Weighted Grade Sum: $weightedGradeSum")
        Log.d(TAG, "  GPA Total: $gpaTotal")
        Log.d(TAG, "  Modules: ${moduleList.size}")
        Log.d(TAG, "  Semester: $semester")

        return StudyGrades(
            gpaTotal = gpaTotal,
            gpaMainModules = gpaTotal, // Use same value for both since we can't distinguish
            creditsTotal = totalCredits,
            creditsGained = gainedCredits,
            modules = moduleList,
            semester = semester
        )
    }

    /**
     * Data class to hold individual module information
     */
    private data class ModuleData(
        val id: String,
        val name: String,
        val grade: Double?,
        val credits: Double,
        val status: String
    )

    /**
     * Extracts credits information from the credits table
     */
    private fun extractCredits(table: Element): Pair<Double?, Double?> {
        val rows = table.select("tbody tr")

        if (rows.size < 2) {
            Log.e(TAG, "Credits table does not have enough rows")
            return Pair(null, null)
        }

        // Total credits needed
        val totalCreditsRow = rows[rows.size - 1]
        val totalCreditsText = totalCreditsRow.child(0).text()
        val totalCredits = parseCredits(totalCreditsText.split(":").getOrNull(1))

        // Credits gained so far
        val gainedCreditsRow = rows[rows.size - 2]
        val gainedCreditsText = gainedCreditsRow.child(2).text()
        val gainedCredits = parseCredits(gainedCreditsText)

        return Pair(totalCredits, gainedCredits)
    }

    /**
     * Extracts GPA information from the GPA table
     */
    private fun extractGpa(table: Element): Pair<Double?, Double?> {
        val rows = table.select("tbody tr")

        if (rows.size < 2) {
            Log.e(TAG, "GPA table does not have enough rows")
            return Pair(null, null)
        }

        // Total GPA
        val totalGpaRow = rows[0]
        val totalGpaCells = totalGpaRow.select("th")
        val totalGpaText = totalGpaCells.getOrNull(1)?.text()
        val totalGpa = parseGrade(totalGpaText)

        // Main modules GPA
        val mainModulesGpaRow = rows[1]
        val mainModulesGpaCells = mainModulesGpaRow.select("th")
        val mainModulesGpaText = mainModulesGpaCells.getOrNull(1)?.text()
        val mainModulesGpa = parseGrade(mainModulesGpaText)

        return Pair(totalGpa, mainModulesGpa)
    }

    /**
     * Parses a grade string to a Double, handling German number format
     */
    private fun parseGrade(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        if (text.contains("noch nicht gesetzt", ignoreCase = true)) return null
        if (text.contains("bestanden", ignoreCase = true)) return null
        return try {
            text.trim().replace(",", ".").toDoubleOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse grade: $text", e)
            null
        }
    }

    /**
     * Parses a credits string to a Double, handling German number format
     */
    private fun parseCredits(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        return try {
            text.trim().replace(",", ".").toDoubleOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse credits: $text", e)
            null
        }
    }
}
