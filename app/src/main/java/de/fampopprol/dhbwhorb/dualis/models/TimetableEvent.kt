package de.fampopprol.dhbwhorb.dualis.models

data class TimetableEvent(
    val title: String,
    val startTime: String,
    val endTime: String,
    val room: String,
    val lecturer: String
)