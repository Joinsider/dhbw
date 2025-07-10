package de.fampopprol.dhbwhorb.dualis.models

data class TimetableDay(
    val date: String,
    val events: List<TimetableEvent>
)