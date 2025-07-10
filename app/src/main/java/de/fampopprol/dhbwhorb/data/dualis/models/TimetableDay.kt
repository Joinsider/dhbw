package de.fampopprol.dhbwhorb.data.dualis.models

data class TimetableDay(
    val date: String,
    val events: List<TimetableEvent>
)