package de.joinside.dhbw.ui.schedule.modules

import kotlinx.datetime.LocalDateTime

data class LectureModel(
    val name: String,
    val shortName: String = name, // Short name for better overflow handling
    val isTest: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val lecturer: String,
    val location: String
)
