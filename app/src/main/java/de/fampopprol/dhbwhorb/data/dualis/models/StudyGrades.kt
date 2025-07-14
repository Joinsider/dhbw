/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.models

/**
 * Represents the overall study grades information from Dualis
 */
data class StudyGrades(
    val gpaTotal: Double,           // Total GPA of all courses
    val gpaMainModules: Double,     // GPA of main modules only
    val creditsTotal: Double,       // Total credits required
    val creditsGained: Double,      // Credits earned so far
    val modules: List<Module> = emptyList(),  // Individual modules with their grades
    val semester: String = "current"          // Semester identifier
)
