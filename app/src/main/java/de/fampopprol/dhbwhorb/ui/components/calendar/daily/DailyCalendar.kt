/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components.calendar.daily

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import de.fampopprol.dhbwhorb.ui.components.calendar.eventPopup.EventDetailDialog
import de.fampopprol.dhbwhorb.ui.components.calendar.eventPopup.EventListItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun DailyCalendar(
    timetable: List<TimetableDay>,
    currentDate: LocalDate,
    onPreviousDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var selectedEvent by remember { mutableStateOf<TimetableEvent?>(null) }
    var selectedEventDate by remember { mutableStateOf<String?>(null) }

    // Format current date for display
    val timetableDateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    
    // Get events for the current day
    val currentDayEvents = remember(timetable, currentDate) {
        val currentDateString = currentDate.format(timetableDateFormatter)
        timetable.find { it.date == currentDateString }?.events ?: emptyList()
    }

    // Sort events by start time
    val sortedEvents = remember(currentDayEvents) {
        currentDayEvents.sortedBy { event ->
            try {
                LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) {
                LocalTime.MIN // Put events with invalid times at the beginning
            }
        }
    }

    // Swipe detection
    val swipeThreshold = 50f
    var totalDragAmount by remember { mutableFloatStateOf(0f) }

    Log.d("DailyCalendar", "Rendering daily list for ${currentDate} with ${currentDayEvents.size} events")

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(currentDate) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDragAmount = 0f
                    },
                    onDragEnd = {
                        if (abs(totalDragAmount) > swipeThreshold) {
                            if (totalDragAmount > 0) {
                                onPreviousDay()
                            } else {
                                onNextDay()
                            }
                        }
                        totalDragAmount = 0f
                    }
                ) { _, dragAmount ->
                    totalDragAmount += dragAmount
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Date header
            DailyCalendarHeader(currentDate = currentDate)

            if (sortedEvents.isEmpty()) {
                // Empty state
                EmptyDayState(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                // Events list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedEvents) { event ->
                        EventListItem(
                            event = event,
                            onClick = {
                                selectedEvent = event
                                selectedEventDate = currentDate.format(timetableDateFormatter)
                            }
                        )
                    }
                }
            }
        }

        // Event detail dialog
        selectedEvent?.let { event ->
            selectedEventDate?.let { eventDate ->
                EventDetailDialog(
                    event = event,
                    eventDate = eventDate,
                    onDismiss = {
                        selectedEvent = null
                        selectedEventDate = null
                    }
                )
            }
        }
    }
}
