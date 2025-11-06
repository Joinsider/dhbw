package de.joinside.dhbw.ui.schedule.modules

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TimelineView(
    startHour: Int = 8,
    endHour: Int = 18,
    hourHeight: Float = 80f,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(50.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Add spacing to match DayColumn header (titleMedium text + vertical padding + divider padding)
        // Text with vertical padding (8dp top + 8dp bottom) + divider bottom padding (4dp)
        Spacer(modifier = Modifier.height(48.dp))

        for (hour in startHour..endHour) {
            Box(
                modifier = Modifier.height(hourHeight.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = if (hour < 10) "0$hour:00" else "$hour:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}