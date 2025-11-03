package de.joinside.dhbw.ui.schedule.views

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.no_lectures_this_week
import de.joinside.dhbw.ui.schedule.modules.EventModule
import de.joinside.dhbw.ui.schedule.modules.LectureModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun WeeklyLecturesView(
    lectures: List<LectureModel> = emptyList(), modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        if (lectures.isEmpty()) {

            Text(
                text = stringResource(Res.string.no_lectures_this_week)
            )

        } else {

            for (lecture in lectures) {

                // TODO: Get Weekday from lecture date and then display then display the lecture on that day
                // TODO: Add a weekly view with all weekdays and their lectures
                // TODO: Group lectures by day and display them under the respective day
                // TODO: Order the lectures on each day by their start time and display them accordingly
                // TODO: Add a timeline to the left side showing the hours of the day and position the lectures accordingly

                EventModule(
                    lecture,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

            }
        }
    }
}