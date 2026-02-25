package de.fampopprol.dhbwhorb.ui.schedule.models

import kotlinx.datetime.LocalDateTime

data class LectureModel(
    val name: String,
    val shortName: String = name, // Short name for better overflow handling
    val isTest: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val lecturers: List<String>,
    val location: String
)