package de.joinside.dhbw.ui.schedule.views

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.no_lectures_this_week
import de.joinside.dhbw.ui.schedule.models.LectureModel
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import de.joinside.dhbw.util.isMobilePlatform
import kotlinx.coroutines.launch
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    if (!isRefreshing) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount
                            },
                            onDragEnd = {
                                val widthInPx = with(density) { boxWidth.toPx() }
                                if (offsetX > widthInPx / 3) {
                                    onPreviousWeek()
                                } else if (offsetX < -widthInPx / 3) {
                                    onNextWeek()
                                }
                                offsetX = 0f
                            }
                        )
                    }
                }
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .onGloballyPositioned { coordinates ->
                    boxWidth = with(density) { coordinates.size.width.toDp() }
                }
        ) {
            if (lectures.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_lectures_this_week),
                    modifier = Modifier.padding(16.dp).testTag("noLecturesMessage"),
                    textAlign = TextAlign.Center
                )
            } else {
                // Group lectures by day of week
                val lecturesByDay =
                    lectures.groupBy { it.start.dayOfWeek }.mapValues { (_, dayLectures) ->
                        // Sort lectures by start time within each day
                        dayLectures.sortedBy { it.start }
                    }

                // Find the earliest and latest hours to set timeline bounds
                val startHour = lectures.minOfOrNull { it.start.hour }?.coerceAtMost(8) ?: 8
                val endHour = lectures.maxOfOrNull { it.end.hour }?.coerceAtLeast(18) ?: 18
                val hourHeight = 80f

                val scrollState = rememberScrollState()
                val coroutineScope = rememberCoroutineScope()

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .then(
                            if (!isMobilePlatform()) {
                                // Add drag-to-scroll for desktop
                                Modifier.pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        coroutineScope.launch {
                                            scrollState.scrollBy(-dragAmount.y)
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Timeline on the left
                    TimelineView(
                        startHour = startHour, endHour = endHour, hourHeight = hourHeight
                    )


                    // Days of the week (Monday to Friday)
                    Row(
                        modifier = Modifier.weight(1f)
                            .onGloballyPositioned { coordinates ->
                                rowWidth = with(density) { coordinates.size.width.toDp() }
                            }
                    ) {
                        if (rowWidth > 0.dp) {
                            val dayColumnWidth = rowWidth / 5
                            listOf(
                                DayOfWeek.MONDAY,
                                DayOfWeek.TUESDAY,
                                DayOfWeek.WEDNESDAY,
                                DayOfWeek.THURSDAY,
                                DayOfWeek.FRIDAY
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