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
        val moduleList = convertToModuleList(modules)
        val creditsInfo = calculateCredits(modules)
        val gpaTotal = calculateGPA(modules, creditsInfo.gainedCredits)
        val semester = getSemesterName(semesterArgument)

        logSummary(creditsInfo, gpaTotal, moduleList.size, semester)

        return StudyGrades(
            gpaTotal = gpaTotal,
            gpaMainModules = gpaTotal,
            creditsTotal = creditsInfo.totalCredits,
            creditsGained = creditsInfo.gainedCredits,
            modules = moduleList,
            semester = semester
        )
    }

    /**
     * Converts ModuleData objects to Module objects
     */
    private fun convertToModuleList(modules: List<ModuleData>): List<Module> {
        return modules.map { moduleData ->
            Module(
                id = moduleData.id,
                name = moduleData.name,
                credits = moduleData.credits.toString(),
                grade = moduleData.grade?.toString() ?: "noch nicht gesetzt",
                state = determineExamState(moduleData)
            )
        }
    }

    /**
     * Determines exam state based on module data
     */
    private fun determineExamState(moduleData: ModuleData): ExamState {
        return when {
            moduleData.status.contains("bestanden", ignoreCase = true) -> ExamState.PASSED
            moduleData.status.contains("nicht bestanden", ignoreCase = true) -> ExamState.FAILED
            moduleData.grade != null && moduleData.grade > 0.0 -> ExamState.PASSED
            else -> ExamState.PENDING
        }
    }

    /**
     * Calculates total and gained credits
     */
    private fun calculateCredits(modules: List<ModuleData>): CreditsInfo {
        var totalCredits = 0.0
        var gainedCredits = 0.0

        modules.forEach { module ->
            totalCredits += module.credits
            if (isModulePassed(module)) {
                gainedCredits += module.credits
            }
        }

        return CreditsInfo(totalCredits, gainedCredits)
    }

    /**
     * Checks if a module is passed
     */
    private fun isModulePassed(module: ModuleData): Boolean {
        return module.status.contains("bestanden", ignoreCase = true) ||
               module.status.contains("Prüfungen", ignoreCase = true) ||
               (module.grade != null && module.grade > 0.0)
    }

    /**
     * Calculates GPA from modules
     */
    private fun calculateGPA(modules: List<ModuleData>, gainedCredits: Double): Double {
        var weightedGradeSum = 0.0

        modules.forEach { module ->
            module.grade?.let { grade ->
                if (grade > 0.0) {
                    weightedGradeSum += grade * module.credits
                }
            }
        }

        return if (gainedCredits > 0.0) weightedGradeSum / gainedCredits else 0.0
    }

    /**
     * Gets semester name from argument
     */
    private fun getSemesterName(semesterArgument: String): String {
        return when (semesterArgument) {
            "-N000307," -> "Current Semester"
            "-N000308," -> "Previous Semester"
            "-N000309," -> "Two Semesters Ago"
            else -> "Semester"
        }
    }

    /**
     * Logs summary information
     */
    private fun logSummary(creditsInfo: CreditsInfo, gpaTotal: Double, moduleCount: Int, semester: String) {
        Log.d(TAG, "Summary calculation:")
        Log.d(TAG, "  Total Credits: ${creditsInfo.totalCredits}")
        Log.d(TAG, "  Gained Credits: ${creditsInfo.gainedCredits}")
        Log.d(TAG, "  GPA Total: $gpaTotal")
        Log.d(TAG, "  Modules: $moduleCount")
        Log.d(TAG, "  Semester: $semester")
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
     * Data class to hold credits information
     */
    private data class CreditsInfo(
        val totalCredits: Double,
        val gainedCredits: Double
    )

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
