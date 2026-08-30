package de.fampopprol.dhbwhorb.ui.schedule.modules.week

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.util.isMobilePlatform
import androidx.compose.ui.tooling.preview.Preview

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
        PreviousWeekButton(
            isRefreshing = isRefreshing,
            hapticFeedback = hapticFeedback,
            onPreviousWeek = onPreviousWeek
        )
        // spacer with weight to push the text to center between icons
        Spacer(Modifier.weight(1f))
        WeekLabelBox(
            weekLabel = weekLabel,
            isRefreshing = isRefreshing,
            hapticFeedback = hapticFeedback,
            onWeekLabelClick = onWeekLabelClick,
            onRefresh = onRefresh
        )
        Spacer(Modifier.weight(1f))
        NextWeekButton(
            isRefreshing = isRefreshing,
            hapticFeedback = hapticFeedback,
            onNextWeek = onNextWeek
        )
        RefreshButton(isRefreshing = isRefreshing, onRefresh = onRefresh)
    }
}

@Composable
private fun PreviousWeekButton(
    isRefreshing: Boolean,
    hapticFeedback: HapticFeedback,
    onPreviousWeek: () -> Unit
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
}

@Composable
private fun NextWeekButton(
    isRefreshing: Boolean,
    hapticFeedback: HapticFeedback,
    onNextWeek: () -> Unit
) {
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
}

@Composable
private fun WeekLabelBox(
    weekLabel: String,
    isRefreshing: Boolean,
    hapticFeedback: HapticFeedback,
    onWeekLabelClick: () -> Unit,
    onRefresh: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .background(
                color = weekLabelBackgroundColor(isRefreshing),
                shape = MaterialTheme.shapes.small
            )
            .testTag("weekLabelButton")
            .pointerInput(isRefreshing) {
                detectWeekLabelGestures(isRefreshing, onWeekLabelClick, onRefresh, hapticFeedback)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = weekLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = weekLabelTextColor(isRefreshing)
        )
    }
}

@Composable
private fun weekLabelBackgroundColor(isRefreshing: Boolean): Color = if (isRefreshing) {
    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
} else {
    MaterialTheme.colorScheme.surface
}

@Composable
private fun weekLabelTextColor(isRefreshing: Boolean): Color = if (isRefreshing) {
    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
} else {
    MaterialTheme.colorScheme.onSecondaryContainer
}

/** Tap navigates to the week label action, long-press triggers a refresh; both are no-ops while refreshing. */
private suspend fun PointerInputScope.detectWeekLabelGestures(
    isRefreshing: Boolean,
    onWeekLabelClick: () -> Unit,
    onRefresh: (() -> Unit)?,
    hapticFeedback: HapticFeedback
) {
    detectTapGestures(
        onTap = {
            if (!isRefreshing) {
                onWeekLabelClick()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        },
        onLongPress = {
            if (!isRefreshing && onRefresh != null) {
                onRefresh()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    )
}

/** Desktop-only refresh button with a spin animation while refreshing. */
@Composable
private fun RefreshButton(isRefreshing: Boolean, onRefresh: (() -> Unit)?) {
    if (isMobilePlatform() || onRefresh == null) return

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
