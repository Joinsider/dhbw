package de.fampopprol.dhbwhorb.dualis.models

data class DualisUrl(
    var mainPageUrl: String? = null,
    var logoutUrl: String? = null,
    var studentResultsUrl: String? = null,
    var courseResultUrl: String? = null,
    var monthlyScheduleUrl: String? = null,
    val semesterCourseResultUrls: MutableMap<String, String> = mutableMapOf(),
)