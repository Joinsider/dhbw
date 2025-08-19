/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.models

data class DualisUrl(
    var mainPageUrl: String? = null,
    var logoutUrl: String? = null,
    var studentResultsUrl: String? = null,
    var courseResultUrl: String? = null,
    var monthlyScheduleUrl: String? = null,
    var notificationsUrl: String? = null,
    val semesterCourseResultUrls: MutableMap<String, String> = mutableMapOf(),
)