/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.parser

import de.fampopprol.dhbwhorb.domain.model.ExamResult
import de.fampopprol.dhbwhorb.domain.model.ModuleAttempt
import de.fampopprol.dhbwhorb.domain.model.ModuleResultDetails
import de.fampopprol.dhbwhorb.domain.model.ModuleUnit
import io.github.aakira.napier.Napier

/**
 * Reads Dualis' "Ergebnisdetails" pop-up (`PRGNAME=RESULTDETAILS`).
 *
 * The page is two tables under a heading. The first lists the attempts: a `level01` row opens a
 * "Versuch", `tbdata` rows below it are that attempt's exams, and a `level02` "Gesamt" row closes
 * it with Dualis' own verdict. The second lists the module's lectures and labs. Row classes carry
 * the structure — indentation in this template is CSS, not markup — so the parser reads them
 * rather than counting columns.
 */
class ModuleDetailsParser {

    private companion object {
        const val TAG = "ModuleDetailsParser"

        /** Closes an attempt: "Gesamt 1", "Gesamt 2". */
        const val GESAMT = "Gesamt"

        /** Splits the attempts table from the "Zugehörige Bausteine" one. */
        val UNITS_HEADING = """<h2[^>]*>\s*Zugeh(?:ö|&ouml;)rige\s+Bausteine\s*</h2>""".toRegex(RegexOption.IGNORE_CASE)
    }

    private val headingPattern = """<h1[^>]*>([\s\S]*?)</h1>""".toRegex(RegexOption.IGNORE_CASE)
    private val rowPattern = """<tr\b[^>]*>([\s\S]*?)</tr>""".toRegex(RegexOption.IGNORE_CASE)
    private val cellPattern = """<td\b([^>]*)>([\s\S]*?)</td>""".toRegex(RegexOption.IGNORE_CASE)
    private val scriptPattern = """<script[\s\S]*?</script>""".toRegex(RegexOption.IGNORE_CASE)
    private val htmlTagPattern = """<[^>]+>""".toRegex()

    /** "Versuch  2" — Dualis pads the number with spaces. */
    private val attemptPattern = """Versuch\s+(\d+)""".toRegex(RegexOption.IGNORE_CASE)

    /** "Klausur oder Kombinierte Prüfung (50%)" — the share this exam has of the module grade. */
    private val weightPattern = """\((\d+(?:[.,]\d+)?)\s*%\)""".toRegex()

    /**
     * @return null when the page carries no heading, which is what a session-expired or otherwise
     *   unexpected document looks like — [HtmlParser.isValidModuleDetailsPage] is the gate, this
     *   is the second opinion.
     */
    fun parse(htmlContent: String): ModuleResultDetails? {
        return try {
            val heading = headingPattern.find(htmlContent)?.groupValues?.get(1)?.let { normalize(it) }
            if (heading.isNullOrBlank()) {
                Napier.w("Module details page without a heading", tag = TAG)
                return null
            }

            val split = UNITS_HEADING.find(htmlContent)
            val attemptsHtml = split?.let { htmlContent.substring(0, it.range.first) } ?: htmlContent
            val unitsHtml = split?.let { htmlContent.substring(it.range.last + 1) }.orEmpty()

            val (number, name, semester) = splitHeading(heading)
            ModuleResultDetails(
                moduleNumber = number,
                moduleName = name,
                semesterName = semester,
                attempts = parseAttempts(attemptsHtml),
                units = parseUnits(unitsHtml)
            )
        } catch (e: Exception) {
            Napier.e("Error parsing module details: ${e.message}", e, tag = TAG)
            null
        }
    }

    /** "T4INF4211 Compilerbau (SoSe 2026)" → number, name, semester. */
    private fun splitHeading(heading: String): Triple<String, String, String?> {
        val number = heading.substringBefore(' ').trim()
        var rest = heading.removePrefix(number).trim()

        // Only a trailing parenthesis is the semester; a module named "Mathematik (Vertiefung)"
        // would otherwise lose half its name.
        var semester: String? = null
        if (rest.endsWith(")")) {
            val open = rest.lastIndexOf('(')
            if (open > 0) {
                semester = rest.substring(open + 1, rest.length - 1).trim().ifBlank { null }
                rest = rest.substring(0, open).trim()
            }
        }
        return Triple(number, rest, semester)
    }

    private fun parseAttempts(html: String): List<ModuleAttempt> {
        val attempts = mutableListOf<ModuleAttempt>()
        var current: MutableList<ExamResult>? = null
        var currentNumber: Int? = null
        var currentUnit: String? = null

        fun close(result: String?) {
            val exams = current ?: return
            attempts += ModuleAttempt(number = currentNumber, exams = exams.toList(), result = result)
            current = null
            currentNumber = null
            currentUnit = null
        }

        for (rowMatch in rowPattern.findAll(html)) {
            val cells = cellsOf(rowMatch.groupValues[1])
            if (cells.isEmpty()) continue
            if (cells.any { it.hasClass("tbsubhead") || it.hasClass("tbhead") }) continue

            val level02 = cells.filter { it.hasClass("level02") }
            val text = cells.joinToString(" ") { it.text }.trim()

            when {
                // "Versuch 2" opens the next block, so whatever was open ends here — Dualis
                // writes a Gesamt row for every finished attempt, but an attempt still being
                // marked has none, and its exams must not spill into the next attempt.
                cells.any { it.hasClass("level01") } && attemptPattern.containsMatchIn(text) -> {
                    close(result = null)
                    currentNumber = attemptPattern.find(text)?.groupValues?.get(1)?.toIntOrNull()
                    current = mutableListOf()
                }

                // A lone level02 cell heads the Baustein the following exams belong to
                // ("T4INF2001.1 Angewandte Mathematik"), or names the block generically
                // ("Modulabschlussleistungen") when the module has no separate Bausteine.
                level02.size == 1 -> currentUnit = level02.single().text.ifBlank { null }

                // The Gesamt row closes the attempt with Dualis' own verdict for it.
                level02.size > 1 && text.contains(GESAMT, ignoreCase = true) -> {
                    val verdict = level02
                        .map { it.text }
                        .firstOrNull { it.isNotBlank() && !it.contains(GESAMT, ignoreCase = true) }
                    close(result = verdict)
                }

                cells.any { it.hasClass("tbdata") } -> {
                    val data = cells.filter { it.hasClass("tbdata") }
                    if (data.size < 2) continue
                    if (current == null) {
                        // An exam before any "Versuch" heading: keep it rather than drop it.
                        current = mutableListOf()
                    }
                    current?.add(examOf(data, currentUnit))
                }
            }
        }

        close(result = null)
        return attempts
    }

    private fun examOf(cells: List<Cell>, unitName: String?): ExamResult {
        val rawName = cells.getOrNull(1)?.text.orEmpty()
        val weight = weightPattern.find(rawName)
            ?.groupValues?.get(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()

        return ExamResult(
            unitName = unitName,
            semesterName = cells.getOrNull(0)?.text?.ifBlank { null },
            name = weightPattern.replace(rawName, "").trim().ifBlank { rawName },
            weightPercent = weight,
            date = cells.getOrNull(2)?.text?.ifBlank { null },
            grade = cells.getOrNull(3)?.text?.ifBlank { null }
        )
    }

    private fun parseUnits(html: String): List<ModuleUnit> {
        val units = mutableListOf<ModuleUnit>()

        for (rowMatch in rowPattern.findAll(html)) {
            val rowHtml = rowMatch.groupValues[1]
            val cells = cellsOf(rowHtml).filter { it.hasClass("tbdata") }
            if (cells.size < 3) continue

            units += ModuleUnit(
                number = cells[0].text,
                name = cells[1].text,
                event = cells[2].text,
                // The tick is an image, so its presence is the answer, not any text.
                attended = cells.getOrNull(3)?.html?.contains("pass.gif", ignoreCase = true) == true
            )
        }
        return units
    }

    private class Cell(attributes: String, val html: String, val text: String) {
        private val classes: Set<String> =
            CLASS_ATTRIBUTE.find(attributes)?.groupValues?.get(1)
                ?.split(' ')
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty()

        fun hasClass(name: String): Boolean = name in classes

        private companion object {
            val CLASS_ATTRIBUTE = """class\s*=\s*"([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
        }
    }

    private fun cellsOf(rowHtml: String): List<Cell> =
        cellPattern.findAll(rowHtml)
            .map { Cell(it.groupValues[1], it.groupValues[2], normalize(it.groupValues[2])) }
            .toList()

    private fun normalize(text: String): String =
        scriptPattern.replace(text, "")
            .replace(htmlTagPattern, "")
            .replace("&nbsp;", " ")
            // The template also writes real U+00A0, e.g. between "1,0" and "bestanden".
            .replace("\u00A0", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
}
