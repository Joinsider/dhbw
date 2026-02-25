package de.fampopprol.dhbwhorb.ui.schedule.modules.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.resources.Res
import de.fampopprol.dhbwhorb.resources.close
import de.fampopprol.dhbwhorb.resources.date
import de.fampopprol.dhbwhorb.resources.end_time
import de.fampopprol.dhbwhorb.resources.lecture_details
import de.fampopprol.dhbwhorb.resources.lecturer
import de.fampopprol.dhbwhorb.resources.lecturers
import de.fampopprol.dhbwhorb.resources.room
import de.fampopprol.dhbwhorb.resources.rooms
import de.fampopprol.dhbwhorb.resources.start_time
import de.fampopprol.dhbwhorb.resources.subject
import de.fampopprol.dhbwhorb.resources.test_exam
import de.fampopprol.dhbwhorb.ui.schedule.models.LectureModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.jetbrains.compose.resources.stringResource

@OptIn(FormatStringsInDatetimeFormats::class)
val DateFormatter = LocalDate.Format {
    byUnicodePattern("dd.MM.yyyy")
}

@OptIn(FormatStringsInDatetimeFormats::class)
val TimeFormatter = LocalTime.Format {
    byUnicodePattern("HH:mm")
}

/**
 * Dialog displaying detailed information about a lecture event.
 * Shows full name, date, time, location, and lecturer information.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LectureDetailsDialog(
    lecture: LectureModel,
    onDismiss: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.lecture_details),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Subject name (use full name, fallback to short name)
                DetailRow(
                    label = stringResource(Res.string.subject),
                    value = lecture.name
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Date
                DetailRow(
                    label = stringResource(Res.string.date),
                    value = lecture.start.date.format(DateFormatter)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time range
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        DetailRow(
                            label = stringResource(Res.string.start_time),
                            value = lecture.start.time.format(TimeFormatter)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        DetailRow(
                            label = stringResource(Res.string.end_time),
                            value = lecture.end.time.format(TimeFormatter)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Location
                if (lecture.location.isNotEmpty()) {
                    val roomCount = lecture.location.count { it == ',' } + 1
                    DetailRow(
                        label = if(roomCount > 1) stringResource(Res.string.rooms) else stringResource(Res.string.room),
                        value = lecture.location
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Lecturer
                if (lecture.lecturers.isNotEmpty() && !lecture.lecturers.all { it == "Unknown" }) {
                    DetailRow(
                        label = if (lecture.lecturers.size > 1) stringResource(Res.string.lecturers) else stringResource(Res.string.lecturer),
                        // value should be one lecturer per line for better readability
                        value = lecture.lecturers.joinToString("\n")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Test/Exam indicator
                if (lecture.isTest) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "⚠️ " + stringResource(Res.string.test_exam),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            ) {
                Text(stringResource(Res.string.close))
            }
        }
    )
}

/**
 * Helper composable to display a label-value pair.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

