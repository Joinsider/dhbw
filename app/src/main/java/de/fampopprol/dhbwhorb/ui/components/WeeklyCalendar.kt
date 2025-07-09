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

@Composable
fun WeeklyCalendar(timetable: List<TimetableDay>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val weekDays = (0..6).map { startOfWeek.plusDays(it.toLong()) }
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM")
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEE") // Mon, Tue, etc.

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
                    LocalDate.parse(it.date, DateTimeFormatter.ofPattern("dd.MM.yyyy")) == day
                }
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
            .height(IntrinsicSize.Min) // Allow content to define height
            .border(1.dp, Color.LightGray, MaterialTheme.shapes.small),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxHeight()
        ) {
            // Display day number if needed, or just rely on header
            // Text(text = day.dayOfMonth.toString(), fontWeight = FontWeight.Bold)

            if (timetableDay != null) {
                timetableDay.events.forEach { event ->
                    EventItem(event = event)
                }
            } else {
                Text(text = "No classes", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun EventItem(event: TimetableEvent) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = event.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(text = "${event.time} - ${event.location}", fontSize = 12.sp, color = Color.DarkGray)
        // Optionally display lecturer if space allows
        // Text(text = event.lecturer, fontSize = 10.sp, color = Color.Gray)
    }
}