package de.fampopprol.dhbwhorb.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.fampopprol.dhbwhorb.dualis.models.TimetableDay
import de.fampopprol.dhbwhorb.dualis.models.TimetableEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import android.util.Log

@Composable
fun WeeklyCalendar(timetable: List<TimetableDay>, startOfWeek: LocalDate, modifier: Modifier = Modifier) {
    val weekDays = (0..6).map { startOfWeek.plusDays(it.toLong()) }
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM")
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEE") // Mon, Tue, etc.
    val timetableDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    Log.d("WeeklyCalendar", "Rendering weekly calendar with ${timetable.size} timetable days")
    Log.d("WeeklyCalendar", "Week days: ${weekDays.map { it.format(timetableDateFormatter) }}")
    Log.d("WeeklyCalendar", "Timetable dates: ${timetable.map { it.date }}")

    Column(modifier = modifier.fillMaxWidth()) {
        // Weekday headers
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f).padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = dayOfWeekFormatter.format(day), fontWeight = FontWeight.Bold)
                    Text(text = dateFormatter.format(day), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Timetable content grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(weekDays) { day ->
                val timetableDay = timetable.find {
                    try {
                        LocalDate.parse(it.date, timetableDateFormatter) == day
                    } catch (e: Exception) {
                        Log.e("WeeklyCalendar", "Error parsing date: ${it.date}", e)
                        false
                    }
                }
                Log.d("WeeklyCalendar", "Day ${day.format(timetableDateFormatter)}: ${timetableDay?.events?.size ?: 0} events")
                DayCell(day = day, timetableDay = timetableDay)
            }
        }
    }
}

@Composable
fun DayCell(day: LocalDate, timetableDay: TimetableDay?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Fixed height for better layout
            .border(1.dp, Color.LightGray, MaterialTheme.shapes.small),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxHeight()
        ) {
            if (timetableDay != null && timetableDay.events.isNotEmpty()) {
                timetableDay.events.forEach { event ->
                    EventItem(event = event)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Text(text = "No classes", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun EventItem(event: TimetableEvent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Text(
                text = event.title,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 2
            )
            if (event.startTime.isNotEmpty() && event.endTime.isNotEmpty()) {
                Text(
                    text = "${event.startTime} - ${event.endTime}",
                    fontSize = 10.sp,
                    color = Color.DarkGray
                )
            }
            if (event.room.isNotEmpty()) {
                Text(
                    text = event.room,
                    fontSize = 10.sp,
                    color = Color.DarkGray
                )
            }
        }
    }
}