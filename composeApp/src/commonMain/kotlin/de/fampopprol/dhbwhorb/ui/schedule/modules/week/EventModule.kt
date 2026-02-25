package de.fampopprol.dhbwhorb.ui.schedule.modules.week

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.lecturers
import de.fampopprol.dhbwhorb.resources.room
import de.fampopprol.dhbwhorb.ui.schedule.models.LectureModel
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(FormatStringsInDatetimeFormats::class)
val EventTimeFormatter = LocalTime.Format {
    byUnicodePattern("HH:mm")
}


fun formatEventTime(dateTime: LocalDateTime): String {
    return dateTime.time.format(EventTimeFormatter) // Extract LocalTime part and format
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Preview
fun EventModule(
    lecture: LectureModel,
    modifier: Modifier = Modifier,
    smallFont: Boolean = false,
    onClick: () -> Unit = {}
) {
    val color = if(lecture.isTest) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(end = 2.dp, bottom = 2.dp)
            .background(color, shape = RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        if(!smallFont) {
            Text(
                text = "${formatEventTime(lecture.start)} - ${formatEventTime(lecture.end)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                overflow = TextOverflow.Clip
            )
        }

        Text(
            text = if(smallFont) lecture.shortName else lecture.name,
            style = if(smallFont) MaterialTheme.typography.labelSmallEmphasized else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimary,
            overflow = TextOverflow.MiddleEllipsis
        )

        if(lecture.lecturers.isNotEmpty() && !smallFont) {
            Text(
                text = stringResource(Res.string.lecturers) + ": ${lecture.lecturers.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                overflow = TextOverflow.Ellipsis
            )
        }

        if(lecture.location.isNotEmpty()) {
            Text(
                text = if (smallFont) lecture.location else stringResource(Res.string.room) + ": ${lecture.location}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}