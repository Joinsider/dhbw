package de.fampopprol.dhbwhorb.dualis.models.timetable

data class TimetableEvent(
    val title: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val lecturer: String
)