/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.utils

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utility functions for calendar components
 */

/**
 * Extracts a clean title from a timetable event, removing course codes and suffixes
 */
fun extractCleanTitle(event: TimetableEvent): String {
    val fullTitle = event.fullTitle
    return if (fullTitle != null) {
        // Remove course code prefix (pattern: letters+numbers+dots followed by space)
        val withoutCourseCode = fullTitle.replaceFirst(Regex("^[A-Z0-9._]+\\s+"), "")

        // Remove trailing course group info (pattern: space + HOR-XXXXX)
        val cleanTitle = withoutCourseCode.replaceFirst(Regex("\\s+HOR-[A-Z0-9]+$"), "")

        cleanTitle.ifEmpty { event.title }
    } else {
        event.title.trim()
    }
}

/**
 * Formats an event date string from dd.MM.yyyy to a more readable format
 */
fun formatEventDate(eventDate: String): String {
    return try {
        val inputFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val outputFormatter = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.getDefault())
        val date = java.time.LocalDate.parse(eventDate, inputFormatter)
        date.format(outputFormatter)
    } catch (_: Exception) {
        eventDate // Return original if parsing fails
    }
}

/**
 * Parses a time string in various formats (HH:mm or HH.mm)
 */
fun parseTimeString(timeString: String): LocalTime? {
    return try {
        val cleanTime = timeString.trim()
        when {
            cleanTime.matches(Regex("\\d{1,2}:\\d{2}")) -> {
                LocalTime.parse(cleanTime, DateTimeFormatter.ofPattern("H:mm"))
            }
            cleanTime.matches(Regex("\\d{1,2}\\.\\d{2}")) -> {
                LocalTime.parse(cleanTime, DateTimeFormatter.ofPattern("H.mm"))
            }
            else -> null
        }
    } catch (e: Exception) {
        Log.e("CalendarUtils", "Error parsing time: $timeString", e)
        null
    }
}

/**
 * Data class for event positioning in time-based calendar
 */
data class EventPosition(
    val topOffset: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp
)
