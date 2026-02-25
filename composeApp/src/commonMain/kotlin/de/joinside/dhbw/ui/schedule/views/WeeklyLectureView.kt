package de.joinside.dhbw.ui.schedule.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.no_lectures_this_week
import de.joinside.dhbw.ui.schedule.models.LectureModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import de.joinside.dhbw.ui.schedule.modules.week.DayColumn
import de.joinside.dhbw.ui.schedule.modules.week.TimelineView
import de.joinside.dhbw.ui.schedule.modules.week.WeekNavigationBar
import kotlinx.datetime.DayOfWeek
import androidx.compose.ui.platform.testTag
import de.joinside.dhbw.resources.loading_week_from_dualis
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
@Preview
fun WeeklyLecturesView(
    lectures: List<LectureModel> = emptyList(),
    weekLabel: String = "Week 42",
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onWeekLabelClick: () -> Unit = {},
    onLectureClick: (LectureModel) -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var rowWidth by remember { mutableStateOf(0.dp) }
    var boxWidth by remember { mutableStateOf(0.dp) }

    Column(modifier = modifier.fillMaxSize()) {
        WeekNavigationBar(
            weekLabel = weekLabel,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onWeekLabelClick = onWeekLabelClick,
            onRefresh = onRefresh,
            isRefreshing = isRefreshing,
            modifier = Modifier.padding(8.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .onGloballyPositioned { coordinates ->
                    boxWidth = with(density) { coordinates.size.width.toDp() }
                }
        ) {
            val availableHeightDp = maxHeight

            // Always show the grid structure; if no lectures yet, still render timeline and days
            val startHour = lectures.minOfOrNull { it.start.hour }?.coerceAtMost(8) ?: 8
            val endHour = lectures.maxOfOrNull { it.end.hour }?.coerceAtLeast(19) ?: 19

            val totalHours = endHour - startHour
            val hoursForCalculation = totalHours + 2
            val minHourHeightDp = 40.dp
            val minContentHeightDp = minHourHeightDp * hoursForCalculation

            val (hourHeight, scrollEnabled) = if (availableHeightDp >= minContentHeightDp) {
                val calculatedHeight = availableHeightDp / hoursForCalculation
                Pair(calculatedHeight.value, false)
            } else {
                Pair(minHourHeightDp.value, true)
            }

            val scrollState = rememberScrollState()

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        if (scrollState.value == 0 && available.y > 0) {
                            return Offset.Zero
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (scrollState.value == 0 && available.y > 0) {
                            return Velocity.Zero
                        }
                        return Velocity.Zero
                    }
                }
            }

            val containerHeight = if (scrollEnabled) minContentHeightDp else availableHeightDp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        state = rememberDraggableState { delta ->
                            coroutineScope.launch {
                                offsetX.snapTo(offsetX.value + delta)
                            }
                        },
                        orientation = Orientation.Horizontal,
                        enabled = !isRefreshing,
                        onDragStopped = { velocity ->
                            coroutineScope.launch {
                                val widthInPx = with(density) { boxWidth.toPx() }
                                val threshold = widthInPx / 3

                                when {
                                    offsetX.value > threshold -> {
                                        // Swipe right to previous week - animate to completion
                                        offsetX.animateTo(
                                            targetValue = widthInPx,
                                            animationSpec = tween(durationMillis = 200)
                                        )
                                        onPreviousWeek()
                                        offsetX.snapTo(0f)
                                    }
                                    offsetX.value < -threshold -> {
                                        // Swipe left to next week - animate to completion
                                        offsetX.animateTo(
                                            targetValue = -widthInPx,
                                            animationSpec = tween(durationMillis = 200)
                                        )
                                        onNextWeek()
                                        offsetX.snapTo(0f)
                                    }
                                    else -> {
                                        // Didn't meet threshold - spring back to center
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            initialVelocity = velocity
                                        )
                                    }
                                }
                            }
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(containerHeight)
                        .then(
                            Modifier
                                .nestedScroll(nestedScrollConnection)
                                .verticalScroll(scrollState)
                        )
                ) {
                    TimelineView(
                        startHour = startHour, endHour = endHour, hourHeight = hourHeight
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                rowWidth = with(density) { coordinates.size.width.toDp() }
                            }
                    ) {
                        if (rowWidth > 0.dp) {
                            val dayColumnWidth = rowWidth / 5
                            listOf(
                                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                            ).forEach { day ->
                                DayColumn(
                                    dayOfWeek = day,
                                    lectures = lectures.filter { it.start.dayOfWeek == day }.sortedBy { it.start },
                                    startHour = startHour,
                                    endHour = endHour,
                                    hourHeight = hourHeight,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                        .width(dayColumnWidth),
                                    width = dayColumnWidth,
                                    onLectureClick = onLectureClick
                                )
                            }
                        }
                    }
                }

                // Overlay center text message when reloading or empty list
                if (isRefreshing || lectures.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val text = if (isRefreshing) stringResource(Res.string.loading_week_from_dualis) else stringResource(Res.string.no_lectures_this_week)
                        // Background chip around the text for readability
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .then(if (!isRefreshing) Modifier.testTag("noLecturesMessage") else Modifier)
                        ) {
                            Text(
                                text = text,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
