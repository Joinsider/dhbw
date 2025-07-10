package de.fampopprol.dhbwhorb.dualis.models.timetable

data class TimetableDay(
    val date: String,
    val events: List<TimetableEvent>
)
