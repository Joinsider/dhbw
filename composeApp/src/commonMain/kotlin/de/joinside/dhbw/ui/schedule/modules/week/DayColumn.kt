package de.joinside.dhbw.ui.schedule.modules.week

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.ui.schedule.models.LectureModel
import kotlinx.datetime.DayOfWeek


@Composable
fun DayColumn(
    dayOfWeek: DayOfWeek,
    lectures: List<LectureModel>,
    startHour: Int = 8,
    endHour: Int = 18,
    hourHeight: Float = 80f,
    modifier: Modifier = Modifier,
    width: Dp,
    onLectureClick: (LectureModel) -> Unit = {}
) {
    Column(
        modifier = modifier
    ) {
        var short = true
        if(width > 100.dp) {
            short = false
        }
        // Day header
        Text(
            text = getDayName(dayOfWeek, short),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )

        HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

        // Lectures container with timeline
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Background grid lines for each hour
            Column {
                repeat(endHour - startHour + 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hourHeight.dp)
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // Lectures positioned by time
            Box(modifier = Modifier.fillMaxWidth()) {
                lectures.forEach { lecture ->
                    val startMinutes = lecture.start.hour * 60 + lecture.start.minute
                    val endMinutes = lecture.end.hour * 60 + lecture.end.minute
                    val startHourMinutes = startHour * 60

                    val offsetMinutes = startMinutes - startHourMinutes
                    val durationMinutes = endMinutes - startMinutes

                    val offsetDp = (offsetMinutes / 60f * hourHeight).dp
                    val heightDp = (durationMinutes / 60f * hourHeight).dp

                    Box(
                        modifier = Modifier
                            .padding(top = offsetDp)
                            .height(heightDp)
                            .fillMaxWidth()
                    ) {
                        EventModule(
                            lecture = lecture,
                            modifier = Modifier
                                .padding(2.dp)
                                .height(heightDp),
                            smallFont = width < 100.dp,
                            onClick = { onLectureClick(lecture) }
                        )
                    }
                }
            }
        }
    }
}