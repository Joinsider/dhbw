/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

/**
 * What Dualis records behind one module result — the page its grade table links to.
 *
 * The grade list can only say "3,2, bestanden (Wh.)". This is where that number comes from: the
 * attempts, and inside each attempt the individual exams with their weights. A module whose grade
 * is composed of a 50%-Klausur and a 50%-Labor looks exactly like a single-exam module in the
 * list, and only here is the difference visible.
 */
data class ModuleResultDetails(
    val moduleNumber: String,
    val moduleName: String,
    /** The semester Dualis names in the heading, e.g. "SoSe 2026"; null when it writes none. */
    val semesterName: String?,
    /** Oldest attempt first, the way Dualis numbers them. */
    val attempts: List<ModuleAttempt>,
    /** The lectures and labs this module is made of ("Zugehörige Bausteine"). */
    val units: List<ModuleUnit>
)

/**
 * One "Versuch" block.
 *
 * @param number the attempt's number, null when Dualis writes a heading this cannot read
 * @param exams the exams graded within this attempt
 * @param result the "Gesamt" line, e.g. "1,0 bestanden" — Dualis' own summary of the attempt
 */
data class ModuleAttempt(
    val number: Int?,
    val exams: List<ExamResult>,
    val result: String?
)

/**
 * One graded exam inside an attempt.
 *
 * @param unitName the Baustein this exam belongs to, as Dualis heads the block — e.g.
 *   "T4INF2001.1 Angewandte Mathematik HOR-TINF2024". This is the answer to "which grades make up
 *   this module": Mathematik III carries a Klausur in Angewandte Mathematik and one in Statistik,
 *   and the module grade is what the two come to together.
 * @param weightPercent the share this exam has *within its Baustein*, when Dualis states it — the
 *   "(100%)" in "Klausur (100%)". A Baustein with a single exam always reads 100%.
 */
data class ExamResult(
    val unitName: String?,
    val semesterName: String?,
    val name: String,
    val weightPercent: Double?,
    val date: String?,
    val grade: String?
)

/** A lecture or lab belonging to the module. */
data class ModuleUnit(
    val number: String,
    val name: String,
    val event: String,
    val attended: Boolean
)
