package de.joinside.dhbw.ui.schedule.modules

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.LocalDateTime

data class LectureModel(
    val name: String,
    val color: Color,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val lecturer: String,
    val location: String
)
