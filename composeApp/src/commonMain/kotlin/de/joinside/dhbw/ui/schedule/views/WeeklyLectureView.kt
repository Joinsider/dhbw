package de.joinside.dhbw.ui.schedule.views

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.no_lectures_this_week
import de.joinside.dhbw.ui.schedule.models.LectureModel
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import de.joinside.dhbw.ui.schedule.modules.week.DayColumn
import de.joinside.dhbw.ui.schedule.modules.week.TimelineView
import de.joinside.dhbw.ui.schedule.modules.week.WeekNavigationBar
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
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX)
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
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .onGloballyPositioned { coordinates ->
                    boxWidth = with(density) { coordinates.size.width.toDp() }
                }
        ) {
            val availableHeightDp = maxHeight

            if (lectures.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_lectures_this_week),
                    modifier = Modifier.padding(16.dp).testTag("noLecturesMessage"),
                    textAlign = TextAlign.Center
                )
            } else {
                val lecturesByDay =
                    lectures.groupBy { it.start.dayOfWeek }.mapValues { (_, dayLectures) ->
                        dayLectures.sortedBy { it.start }
                    }

                val startHour = lectures.minOfOrNull { it.start.hour }?.coerceAtMost(8) ?: 8
                val endHour = lectures.maxOfOrNull { it.end.hour }?.coerceAtLeast(19) ?: 19

                val totalHours = endHour - startHour
                // THE FIX: We calculate space for one extra hour to ensure the last label is visible
                val hoursForCalculation = totalHours + 2

                val minHourHeightDp = 40.dp
                val minContentHeightDp = minHourHeightDp * hoursForCalculation

                val (hourHeight, scrollEnabled) = if (availableHeightDp >= minContentHeightDp) {
                    // Enough space: distribute available height by (totalHours + 1)
                    // This ensures the actual content (totalHours) leaves exactly 1 hour of empty space at bottom
                    val calculatedHeight = availableHeightDp / hoursForCalculation
                    Pair(calculatedHeight.value, false)
                } else {
                    // Not enough space: Use minimum height
                    Pair(minHourHeightDp.value, true)
                }

                val scrollState = rememberScrollState()

                // Create nested scroll connection to coordinate with parent PullToRefreshBox
                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            // Don't consume scroll if we're at the top and scrolling down
                            // This allows pull-to-refresh to work
                            if (scrollState.value == 0 && available.y > 0) {
                                return Offset.Zero
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            // Allow parent to handle fling when at scroll top
                            if (scrollState.value == 0 && available.y > 0) {
                                return Velocity.Zero
                            }
                            return Velocity.Zero
                        }
                    }
                }

                // Calculate the height needed for the container
                // If scrolling, we need the full calculated height
                // If not scrolling, we just take the full available height
                val containerHeight = if (scrollEnabled) minContentHeightDp else availableHeightDp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(
                            state = rememberDraggableState { delta ->
                                offsetX += delta
                            },
                            orientation = Orientation.Horizontal,
                            enabled = !isRefreshing,
                            onDragStopped = {
                                val widthInPx = with(density) { boxWidth.toPx() }
                                when {
                                    offsetX > widthInPx / 3 -> onPreviousWeek()
                                    offsetX < -widthInPx / 3 -> onNextWeek()
                                }
                                offsetX = 0f
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
                            modifier = Modifier.weight(1f)
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
                                        lectures = lecturesByDay[day] ?: emptyList(),
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
                }
            }
        }
    }
}
