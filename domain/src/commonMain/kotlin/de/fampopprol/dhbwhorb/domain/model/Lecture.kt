/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * A single lecture slot as the app talks about it.
 *
 * Distinct from the Room entity of the same data: this one carries its lecturers instead of
 * leaving them behind a cross-reference table, and it has no persistence annotations, so
 * `:domain` stays free of Room.
 *
 * @param id the local database id, or 0 for a lecture that has not been persisted yet
 * @param fullName the full course title; null while only the weekly skeleton has been loaded
 */
data class Lecture(
    val id: Long,
    val shortName: String,
    val fullName: String?,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String,
    val isTest: Boolean,
    val lecturers: List<String> = emptyList()
) {
    val displayName: String get() = fullName ?: shortName
}
