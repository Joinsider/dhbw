package de.fampopprol.dhbwhorb.dualis.models

data class TimetableEvent(
    val title: String,
    val time: String,
    val location: String,
    val lecturer: String
)

data class TimetableDay(
    val date: String,
    val events: List<TimetableEvent>
)