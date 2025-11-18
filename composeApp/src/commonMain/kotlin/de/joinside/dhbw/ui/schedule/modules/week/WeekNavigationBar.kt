package de.joinside.dhbw.ui.schedule.modules.week

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.util.isMobilePlatform
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun WeekNavigationBar(
    weekLabel: String = "Week Example",
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onWeekLabelClick: () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
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
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickable { onWeekLabelClick() }
                .padding(8.dp)
                .testTag("weekLabelButton")
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

        // Add refresh button for desktop platforms
        if (!isMobilePlatform() && onRefresh != null) {
            // Animate rotation when refreshing
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing)
                )
            )

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("refreshButton")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier
                )
            }
        }
    }
}