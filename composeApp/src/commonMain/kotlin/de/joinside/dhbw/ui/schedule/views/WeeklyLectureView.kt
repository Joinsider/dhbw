package de.joinside.dhbw.ui.schedule.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.no_lectures_this_week
import de.joinside.dhbw.ui.schedule.modules.LectureModel
import androidx.compose.ui.platform.testTag
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import de.joinside.dhbw.ui.schedule.modules.DayColumn
import de.joinside.dhbw.ui.schedule.modules.TimelineView
import de.joinside.dhbw.ui.schedule.modules.WeekNavigationBar


@Composable
@Preview
fun WeeklyLecturesView(
    lectures: List<LectureModel> = emptyList(),
    weekLabel: String = "Week 42",
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onWeekLabelClick: () -> Unit = {},
    onLectureClick: (LectureModel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        WeekNavigationBar(
            weekLabel = weekLabel,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onWeekLabelClick = onWeekLabelClick,
            modifier = Modifier.padding(8.dp)
        )

        if (lectures.isEmpty()) {
            Text(
                text = stringResource(Res.string.no_lectures_this_week),
                modifier = modifier.padding(16.dp).testTag("noLecturesMessage")
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

            val rowWidth = remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current

            Row(
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
                // Timeline on the left
                TimelineView(
                    startHour = startHour, endHour = endHour, hourHeight = hourHeight
                )


                // Days of the week (Monday to Friday)
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            rowWidth.value = with(density) { coordinates.size.width.toDp() }
                        }) {
                    val dayColumnWidth = rowWidth.value / 5
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
                            modifier = Modifier.padding(bottom = 16.dp).width(dayColumnWidth),
                            width = dayColumnWidth,
                            onLectureClick = onLectureClick
                        )
                    }
                }
            }
        }
    }
}