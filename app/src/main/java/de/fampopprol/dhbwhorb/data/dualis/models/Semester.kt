/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.models

/**
 * Represents a semester with its display name and internal value
 */
data class Semester(
    val value: String,  // The internal value used in the API (e.g., "000000015158000")
    val displayName: String,  // The human-readable name (e.g., "SoSe 2025")
    val isSelected: Boolean = false
) {
    companion object {
        /**
         * Fallback semesters when dynamic loading fails
         * These are generic fallback values that should work for most programs
         */
        fun getDefaultSemesters(): List<Semester> {
            return listOf(
                Semester("", "Aktuelles Semester", isSelected = true), // Current semester - empty value for current
                Semester("000000015148000", "WiSe 2024/25"),
                Semester("000000015138000", "SoSe 2024"),
                Semester("000000015128000", "WiSe 2023/24")
            )
        }

        /**
         * Convert semester value to the format expected by the API
         * For current semester (empty value), return empty string
         * For specific semester, return the formatted argument
         */
        fun formatSemesterArgument(semesterValue: String): String {
            return if (semesterValue.isEmpty()) {
                "" // Current semester - no additional argument needed
            } else {
                ",-N$semesterValue" // Previous semester - append as additional argument
            }
        }

        /**
         * Create a semester list from Dualis dropdown data
         * This method processes the actual HTML options from Dualis
         */
        fun fromDualisOptions(options: List<Triple<String, String, Boolean>>): List<Semester> {
            val semesters = mutableListOf<Semester>()

            // Add current semester option (empty value) if no option is selected
            val hasSelectedSemester = options.any { it.third }
            if (!hasSelectedSemester) {
                semesters.add(Semester("", "Aktuelles Semester", isSelected = true))
            }

            // Add all semesters from Dualis
            options.forEach { (value, displayName, isSelected) ->
                semesters.add(Semester(value, displayName, isSelected))
            }

            return semesters
        }
    }
}
