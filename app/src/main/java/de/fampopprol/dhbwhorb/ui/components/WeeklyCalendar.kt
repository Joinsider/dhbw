/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.mutableFloatStateOf

@Composable
fun WeeklyCalendar(
    timetable: List<TimetableDay>,
    startOfWeek: LocalDate,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
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
                try {
                    val startTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
                    val endTime = LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))

                    // If any class starts at or after 20:00 (8 PM), extend to show 20-24
                    if (startTime.hour >= 20) {
                        maxHour = 24 // Extend to midnight
                        minHour = minOf(minHour, 20) // Start from 8 PM if needed
                    } else {
                        // For regular classes, use dynamic range but don't go below 7 AM or above 21 PM by default
                        minHour = minOf(minHour, startTime.hour)
                        maxHour = maxOf(maxHour, endTime.hour + 1) // +1 to show the full hour
                    }
                } catch (e: Exception) {
                    Log.e("WeeklyCalendar", "Error parsing time: ${event.startTime} - ${event.endTime}", e)
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
    val swipeThreshold = 50f // Reduced threshold for more responsive swipes

    // State for tracking swipe
    var totalDragAmount by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

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
                        isDragging = true
                        totalDragAmount = 0f
                    },
                    onDragEnd = {
                        isDragging = false
                        // Use accumulated drag amount for final decision
                        if (abs(totalDragAmount) > swipeThreshold) {
                            if (totalDragAmount > 0) {
                                onPreviousWeek() // Swipe right -> previous week
                            } else {
                                onNextWeek() // Swipe left -> next week
                            }
                        }
                        totalDragAmount = 0f
                    }
                ) { _, dragAmount ->
                    // Accumulate drag amount for smoother detection
                    totalDragAmount += dragAmount

                    // Optional: Provide immediate feedback for large swipes
                    if (abs(totalDragAmount) > swipeThreshold * 2) {
                        if (totalDragAmount > 0) {
                            onPreviousWeek()
                        } else {
                            onNextWeek()
                        }
                        totalDragAmount = 0f
                        isDragging = false
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
                            onEventClick = { event ->
                                selectedEvent = event
                                selectedEventDate = timetableDay?.date
                            }
                        )
                    }
                }
            }
        }

        // Event Detail Popup
        if (selectedEvent != null) {
            EventDetailPopup(
                event = selectedEvent!!,
                eventDate = selectedEventDate ?: "",
                onDismiss = { selectedEvent = null }
            )
        }
    }
}

@Composable
private fun WeekHeaderRow(
    weekDays: List<LocalDate>,
    dateFormatter: DateTimeFormatter,
    dayOfWeekFormatter: DateTimeFormatter,
    timeColumnWidth: androidx.compose.ui.unit.Dp
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Empty space for time column
        Box(modifier = Modifier.width(timeColumnWidth))

        weekDays.forEach { day ->
            Column(
                modifier = Modifier.weight(1f).padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dayOfWeekFormatter.format(day),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = dateFormatter.format(day),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun TimeColumn(
    startHour: Int,
    endHour: Int,
    hourHeight: androidx.compose.ui.unit.Dp,
    timeColumnWidth: androidx.compose.ui.unit.Dp
) {
    // Dynamic font size based on size class
    val fontSize = 10.sp

    val padding = 4.dp

    Column(
        modifier = Modifier.width(timeColumnWidth)
    ) {
        for (hour in startHour until endHour) {
            Box(
                modifier = Modifier.height(hourHeight),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%02d:00", hour),
                    fontSize = fontSize,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = padding, top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DayColumnContent(
    day: LocalDate,
    timetableDay: TimetableDay?,
    startHour: Int,
    endHour: Int,
    hourHeight: androidx.compose.ui.unit.Dp,
    onEventClick: (TimetableEvent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Hour grid lines
        for (hour in startHour until endHour) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = ((hour - startHour) * hourHeight.value).dp)
            )
        }

        // Events positioned by time
        timetableDay?.events?.forEach { event ->
            val eventPosition = remember(event, startHour, hourHeight) {
                calculateEventPosition(event, startHour, hourHeight)
            }

            if (eventPosition != null) {
                TimeBasedEventItem(
                    event = event,
                    onClick = { onEventClick(event) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(eventPosition.height)
                        .offset(y = eventPosition.topOffset)
                        .padding(horizontal = 2.dp)
                        .zIndex(1f)
                )
            }
        }

        // Show "No classes" only if no events for the day
        if (timetableDay?.events?.isEmpty() != false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No classes",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun TimeBasedEventItem(
    event: TimetableEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic sizing based on size class
    val titleFontSize = 11.sp

    val detailFontSize = 9.sp

    val padding = 6.dp

    val maxLines = 2

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = event.title,
                fontWeight = FontWeight.Medium,
                fontSize = titleFontSize,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            // Only show time and room if not extra compact or if there's enough space
            if (padding > 4.dp) {
                if (event.startTime.isNotEmpty() && event.endTime.isNotEmpty()) {
                    Text(
                        text = "${event.startTime} - ${event.endTime}",
                        fontSize = detailFontSize,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                if (event.room.isNotEmpty()) {
                    Text(
                        text = event.room,
                        fontSize = detailFontSize,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EventDetailPopup(
    event: TimetableEvent,
    eventDate: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable { }, // Prevent clicks from propagating through the popup
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding()
                        ) // Add extra 32dp padding
                ) {
                    // Header with close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Event Details",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Event Title
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date
                    EventDetailRow(
                        label = "Date",
                        value = formatEventDate(eventDate)
                    )

                    // Time
                    if (event.startTime.isNotEmpty() && event.endTime.isNotEmpty()) {
                        EventDetailRow(
                            label = "Time",
                            value = "${event.startTime} - ${event.endTime}"
                        )
                    }

                    // Room
                    if (event.room.isNotEmpty()) {
                        EventDetailRow(
                            label = "Room",
                            value = event.room
                        )
                    }

                    // Lecturer
                    if (event.lecturer.isNotEmpty()) {
                        EventDetailRow(
                            label = "Lecturer",
                            value = event.lecturer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun EventDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

fun formatEventDate(dateString: String): String {
    return try {
        val date = LocalDate.parse(dateString, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val dayOfWeek = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val formattedDate = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        "$dayOfWeek, $formattedDate"
    } catch (e: Exception) {
        dateString // Return original if parsing fails
    }
}

data class EventPosition(
    val topOffset: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp
)

fun calculateEventPosition(
    event: TimetableEvent,
    startHour: Int,
    hourHeight: androidx.compose.ui.unit.Dp
): EventPosition? {
    try {
        // Parse start and end times
        val startTime = parseTimeString(event.startTime)
        val endTime = parseTimeString(event.endTime)

        if (startTime == null || endTime == null) {
            Log.w(
                "WeeklyCalendar",
                "Could not parse times for event: ${event.title} (${event.startTime} - ${event.endTime})"
            )
            return null
        }

        // Calculate position based on hours
        val startMinutesFromStart = (startTime.hour - startHour) * 60 + startTime.minute
        val endMinutesFromStart = (endTime.hour - startHour) * 60 + endTime.minute
        val durationMinutes =
            max(endMinutesFromStart - startMinutesFromStart, 30) // Minimum 30 minutes height

        val pixelsPerMinute = hourHeight.value / 60f
        val topOffset = (startMinutesFromStart * pixelsPerMinute).dp
        val height = (durationMinutes * pixelsPerMinute).dp

        return EventPosition(topOffset, height)
    } catch (e: Exception) {
        Log.e("WeeklyCalendar", "Error calculating position for event: ${event.title}", e)
        return null
    }
}

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
        Log.e("WeeklyCalendar", "Error parsing time: $timeString", e)
        null
    }
}
