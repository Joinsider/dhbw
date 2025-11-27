package de.joinside.dhbw.ui.schedule.modules.week

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth(),
        // keep vertical center alignment
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                onPreviousWeek()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            },
            modifier = Modifier
                .padding(start = 8.dp)
                .testTag("previousWeekButton"),
            content = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Week"
                )
            },
            enabled = !isRefreshing,
            shape = MaterialTheme.shapes.medium,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
        )
        // spacer with weight to push the text to center between icons
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                onWeekLabelClick()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            },
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .testTag("weekLabelButton"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = MaterialTheme.shapes.small,
            enabled = !isRefreshing,
            elevation = ButtonDefaults.buttonElevation(
            ),
            content = {
                Text(
                    text = weekLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        )

        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = {
                onNextWeek()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
            },
            enabled = !isRefreshing,
            modifier = Modifier
                .padding(end = 8.dp)
                .testTag("nextWeekButton"),
            content = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Week",
                )
            },
            shape = MaterialTheme.shapes.medium,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
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