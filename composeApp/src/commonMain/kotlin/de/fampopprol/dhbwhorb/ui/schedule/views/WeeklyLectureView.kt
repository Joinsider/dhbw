package de.fampopprol.dhbwhorb.ui.schedule.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.no_lectures_this_week
import de.fampopprol.dhbwhorb.ui.schedule.models.LectureModel
import org.jetbrains.compose.resources.stringResource
import de.fampopprol.dhbwhorb.ui.schedule.modules.week.DayColumn
import de.fampopprol.dhbwhorb.ui.schedule.modules.week.TimelineView
import kotlinx.datetime.DayOfWeek
import androidx.compose.ui.platform.testTag
import de.fampopprol.dhbwhorb.resources.loading_week_from_dualis

@Composable
fun WeeklyLecturesView(
    lectures: List<LectureModel> = emptyList(),
    onLectureClick: (LectureModel) -> Unit = {},
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var rowWidth by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val availableHeightDp = maxHeight

        val startHour = lectures.minOfOrNull { it.start.hour }?.coerceAtMost(8) ?: 8
        val endHour = lectures.maxOfOrNull { it.end.hour }?.coerceAtLeast(19) ?: 19
        val totalHours = endHour - startHour
        val hoursForCalculation = totalHours + 2
        val minHourHeightDp = 40.dp

        val hourHeight = if (availableHeightDp >= (minHourHeightDp * hoursForCalculation)) {
            (availableHeightDp / hoursForCalculation).value
        } else {
            minHourHeightDp.value
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = availableHeightDp + 1.dp)
                ) {
                    TimelineView(startHour = startHour, endHour = endHour, hourHeight = hourHeight)

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                rowWidth = with(density) { coordinates.size.width.toDp() }
                            }
                    ) {
                        if (rowWidth > 0.dp) {
                            val dayColumnWidth = rowWidth / 5
                            listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).forEach { day ->
                                DayColumn(
                                    dayOfWeek = day,
                                    lectures = lectures.filter { it.start.dayOfWeek == day }.sortedBy { it.start },
                                    startHour = startHour,
                                    endHour = endHour,
                                    hourHeight = hourHeight,
                                    modifier = Modifier.padding(bottom = 16.dp).width(dayColumnWidth),
                                    width = dayColumnWidth,
                                    onLectureClick = onLectureClick,
                                    isSkeleton = isRefreshing && lectures.isEmpty()
                                )
                            }
                        }
                    }
                }
            }

            if (isRefreshing || lectures.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val text = if (isRefreshing) stringResource(Res.string.loading_week_from_dualis) else stringResource(Res.string.no_lectures_this_week)
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .then(if (!isRefreshing) Modifier.testTag("noLecturesMessage") else Modifier)
                    ) {
                        Text(text = text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
