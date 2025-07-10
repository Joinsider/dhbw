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
         * Default semesters based on the typical academic calendar
         * These values need to be updated when new semesters are available
         */
        fun getDefaultSemesters(): List<Semester> {
            return listOf(
                Semester("000000015178000", "SoSe 2026"),
                Semester("000000015168000", "WiSe 2025/26"),
                Semester("", "SoSe 2025 (Current)", isSelected = true), // Current semester - empty value
                Semester("000000015148000", "WiSe 2024/25")
            )
        }

        /**
         * Convert semester value to the format expected by the API
         * For current semester (no value), return empty string
         * For specific semester, return the formatted argument
         */
        fun formatSemesterArgument(semesterValue: String): String {
            return if (semesterValue.isEmpty()) {
                "" // Current semester - no additional argument needed
            } else {
                ",-N$semesterValue" // Previous semester - append as additional argument
            }
        }
    }
}
