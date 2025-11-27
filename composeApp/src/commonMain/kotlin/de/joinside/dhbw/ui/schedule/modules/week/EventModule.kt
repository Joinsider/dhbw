package de.joinside.dhbw.ui.schedule.modules.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.joinside.dhbw.resources.Res
import de.joinside.dhbw.resources.lecturers
import de.joinside.dhbw.resources.room
import de.joinside.dhbw.ui.schedule.models.LectureModel
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
            .clickable(onClick = onClick)
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
            style = if(smallFont) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
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