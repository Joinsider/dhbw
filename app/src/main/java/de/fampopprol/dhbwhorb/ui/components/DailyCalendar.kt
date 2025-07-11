package de.fampopprol.dhbwhorb.ui.components

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.data.dualis.models.TimetableEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

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
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.getDefault()) }
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

    Log.d("DailyCalendar", "Rendering daily list for ${currentDate.format(dateFormatter)} with ${currentDayEvents.size} events")

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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = currentDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (sortedEvents.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No events scheduled for this day",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enjoy your free day!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
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

@Composable
private fun EventListItem(
    event: TimetableEvent,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time column
            if (event.startTime.isNotEmpty() && event.endTime.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(80.dp)
                ) {
                    Text(
                        text = event.startTime,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "to",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = event.endTime,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                // Placeholder for events without time
                Box(
                    modifier = Modifier.width(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All Day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Event details column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (event.room.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Location",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = event.room,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDetailDialog(
    event: TimetableEvent,
    eventDate: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Event Details",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
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

                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                EventDetailRow(
                    label = "Date",
                    value = formatEventDate(eventDate)
                )

                if (event.startTime.isNotEmpty() && event.endTime.isNotEmpty()) {
                    EventDetailRow(
                        label = "Time",
                        value = "${event.startTime} - ${event.endTime}"
                    )
                }

                if (event.room.isNotEmpty()) {
                    EventDetailRow(
                        label = "Room",
                        value = event.room
                    )
                }
            }
        }
    }
}
