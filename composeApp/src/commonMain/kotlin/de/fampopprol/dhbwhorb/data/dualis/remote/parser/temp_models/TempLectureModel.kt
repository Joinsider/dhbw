package de.fampopprol.dhbwhorb.data.dualis.remote.parser.temp_models

import kotlinx.datetime.LocalDateTime

data class TempLectureModel (
    val shortSubjectName: String? = null,
    val fullSubjectName: String? = null,
    val linkToIndividualPage: String? = null,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val location: String,
    val isTest: Boolean = false,
    val lecturers: List<String>? = null
)