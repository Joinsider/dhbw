package de.fampopprol.dhbwhorb.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun WeekNavigationBar(
    currentWeekStart: LocalDate,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit,
    isLoading: Boolean,
    lastUpdated: String? = null,
    modifier: Modifier = Modifier
) {
    val weekEnd = currentWeekStart.plusDays(6)
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val isCurrentWeek = currentWeekStart == LocalDate.now().with(
        TemporalAdjusters.previousOrSame(
            DayOfWeek.MONDAY))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Date range display
            Text(
                text = "${dateFormatter.format(currentWeekStart)} - ${dateFormatter.format(weekEnd)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            // Last updated text
            if (lastUpdated != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.last_updated_at, lastUpdated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous week button
                Button(
                    onClick = onPreviousWeek,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.previous_week)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Current week button (only show if not already on current week)
                if (!isCurrentWeek) {
                    OutlinedButton(
                        onClick = onCurrentWeek,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.current_week))
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Next week button
                Button(
                    onClick = onNextWeek,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.next_week)
                    )
                }
            }
        }
    }
}