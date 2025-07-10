package de.fampopprol.dhbwhorb.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import de.fampopprol.dhbwhorb.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.dualis.models.TimetableEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun WeeklyCalendar(timetable: List<TimetableDay>, startOfWeek: LocalDate, modifier: Modifier = Modifier) {
    var selectedEvent by remember { mutableStateOf<TimetableEvent?>(null) }
    var selectedEventDate by remember { mutableStateOf<String?>(null) }

    val weekDays = (0..6).map { startOfWeek.plusDays(it.toLong()) }
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM")
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEE")
    val timetableDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Define time range (7 AM to 9 PM)
    val startHour = 7
    val endHour = 21
    val hourHeight = 60.dp
    val totalHeight = (endHour - startHour) * hourHeight.value

    Log.d("WeeklyCalendar", "Rendering time-based calendar with ${timetable.size} timetable days")

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with day names and dates
            Row(modifier = Modifier.fillMaxWidth()) {
                // Empty space for time column
                Box(modifier = Modifier.width(50.dp))

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

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // Scrollable time-based calendar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // Time column
                Column(
                    modifier = Modifier.width(50.dp)
                ) {
                    for (hour in startHour until endHour) {
                        Box(
                            modifier = Modifier.height(hourHeight),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = String.format(Locale.getDefault(), "%02d:00", hour),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }

                // Days columns with events
                weekDays.forEach { day ->
                    val timetableDay = timetable.find {
                        try {
                            LocalDate.parse(it.date, timetableDateFormatter) == day
                        } catch (e: Exception) {
                            Log.e("WeeklyCalendar", "Error parsing date: ${it.date}", e)
                            false
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((totalHeight).dp)
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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
                            val eventPosition = calculateEventPosition(event, startHour, hourHeight)
                            if (eventPosition != null) {
                                TimeBasedEventItem(
                                    event = event,
                                    onClick = {
                                        selectedEvent = event
                                        selectedEventDate = timetableDay.date
                                    },
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
fun TimeBasedEventItem(
    event: TimetableEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                .padding(6.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = event.title,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (event.startTime.isNotEmpty() && event.endTime.isNotEmpty()) {
                Text(
                    text = "${event.startTime} - ${event.endTime}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (event.room.isNotEmpty()) {
                Text(
                    text = event.room,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 1.dp)
                )
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
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp) // Add extra 32dp padding
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

fun calculateEventPosition(event: TimetableEvent, startHour: Int, hourHeight: androidx.compose.ui.unit.Dp): EventPosition? {
    try {
        // Parse start and end times
        val startTime = parseTimeString(event.startTime)
        val endTime = parseTimeString(event.endTime)

        if (startTime == null || endTime == null) {
            Log.w("WeeklyCalendar", "Could not parse times for event: ${event.title} (${event.startTime} - ${event.endTime})")
            return null
        }

        // Calculate position based on hours
        val startMinutesFromStart = (startTime.hour - startHour) * 60 + startTime.minute
        val endMinutesFromStart = (endTime.hour - startHour) * 60 + endTime.minute
        val durationMinutes = max(endMinutesFromStart - startMinutesFromStart, 30) // Minimum 30 minutes height

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
