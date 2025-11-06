package de.joinside.dhbw.ui.schedule.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun WeekNavigationBar(
    weekLabel: String = "Week Example",
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        // keep vertical center alignment
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
            contentDescription = "Previous Week",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .testTag("previousWeekButton")
                .clickable { onPreviousWeek() }
                .padding(start = 16.dp, end = 4.dp)
        )
        // spacer with weight to push the text to center between icons
        Spacer(Modifier.weight(1f))
        Text(
            text = weekLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Next Week",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .testTag("nextWeekButton")
                .clickable { onNextWeek() }
                .padding(start = 4.dp, end = 16.dp)
        )
    }
}