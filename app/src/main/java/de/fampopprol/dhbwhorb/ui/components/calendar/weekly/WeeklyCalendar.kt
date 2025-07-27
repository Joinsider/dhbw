/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.calendar.weekly

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import de.fampopprol.dhbwhorb.ui.components.calendar.eventPopup.EventDetailPopup
import de.fampopprol.dhbwhorb.ui.components.calendar.TimeColumn
import de.fampopprol.dhbwhorb.ui.components.calendar.daily.DayColumnContent
import de.fampopprol.dhbwhorb.ui.components.utils.parseTimeString
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

@Composable
fun WeeklyCalendar(
    modifier: Modifier = Modifier,
    timetable: List<TimetableDay>,
    startOfWeek: LocalDate,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {}
) {
    var selectedEvent by remember { mutableStateOf<TimetableEvent?>(null) }
    var selectedEventDate by remember { mutableStateOf<String?>(null) }

    // Memoize expensive calculations
    val weekDays = remember(startOfWeek) { (0..4).map { startOfWeek.plusDays(it.toLong()) } }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }
    val dayOfWeekFormatter = remember { DateTimeFormatter.ofPattern("EEE") }
    val timetableDateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    // Calculate dynamic time range based on events
    val (startHour, endHour) = remember(timetable) {
        var minHour = 7  // Default start hour
        var maxHour = 21 // Default end hour (9 PM)

        // Check all events to find the earliest and latest times
        timetable.forEach { day ->
            day.events.forEach { event ->
                val startTime = parseTimeString(event.startTime)
                val endTime = parseTimeString(event.endTime)

                if (startTime != null && endTime != null) {
                    // If any class starts at or after 20:00 (8 PM), extend to show 20-24
                    if (startTime.hour >= 20) {
                        maxHour = 24 // Extend to midnight
                        minHour = minOf(minHour, 20) // Start from 8 PM if needed
                    } else {
                        // For regular classes, use dynamic range but don't go below 7 AM or above 21 PM by default
                        minHour = minOf(minHour, startTime.hour)
                        maxHour = maxOf(maxHour, endTime.hour + 1) // +1 to show the full hour
                    }
                }
            }
        }

        Log.d("WeeklyCalendar", "Dynamic time range: $minHour:00 - $maxHour:00")
        Pair(minHour, maxHour)
    }

    // Fixed sizing
    val hourHeight = 60.dp
    val timeColumnWidth = 50.dp
    val totalHeight = (endHour - startHour) * hourHeight.value

    // Improved swipe detection parameters
    val swipeThreshold = 50f
    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    // Memoize timetable mapping for better performance
    val timetableByDate = remember(timetable, startOfWeek) {
        val map = mutableMapOf<LocalDate, TimetableDay>()
        timetable.forEach { day ->
            try {
                val date = LocalDate.parse(day.date, timetableDateFormatter)
                map[date] = day
            } catch (e: Exception) {
                Log.e("WeeklyCalendar", "Error parsing date: ${day.date}", e)
            }
        }
        map
    }

    Log.d("WeeklyCalendar", "Rendering time-based calendar with ${timetable.size} timetable days")

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(startOfWeek) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDragAmount = 0f
                    },
                    onDragEnd = {
                        if (abs(totalDragAmount) > swipeThreshold) {
                            if (totalDragAmount > 0) {
                                onPreviousWeek()
                            } else {
                                onNextWeek()
                            }
                        }
                        totalDragAmount = 0f
                    }
                ) { _, dragAmount ->
                    totalDragAmount += dragAmount

                    if (abs(totalDragAmount) > swipeThreshold * 2) {
                        if (totalDragAmount > 0) {
                            onPreviousWeek()
                        } else {
                            onNextWeek()
                        }
                        totalDragAmount = 0f
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with day names and dates
            WeekHeaderRow(
                weekDays = weekDays,
                dateFormatter = dateFormatter,
                dayOfWeekFormatter = dayOfWeekFormatter,
                timeColumnWidth = timeColumnWidth
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // Scrollable time-based calendar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // Time column
                TimeColumn(
                    startHour = startHour,
                    endHour = endHour,
                    hourHeight = hourHeight,
                    timeColumnWidth = timeColumnWidth
                )

                // Days columns with events
                weekDays.forEach { day ->
                    val timetableDay = timetableByDate[day]

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((totalHeight).dp)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        DayColumnContent(
                            day = day,
                            timetableDay = timetableDay,
                            startHour = startHour,
                            endHour = endHour,
                            hourHeight = hourHeight,
                            onEventClick = { event: TimetableEvent ->
                                selectedEvent = event
                                selectedEventDate = timetableDay?.date
                            }
                        )
                    }
                }
            }
        }

        // Event Detail Popup
        selectedEvent?.let { event ->
            EventDetailPopup(
                event = event,
                eventDate = selectedEventDate ?: "",
                onDismiss = {
                    selectedEvent = null
                    selectedEventDate = null
                }
            )
        }
    }
}
