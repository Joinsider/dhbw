// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import kotlinx.datetime.LocalDateTime

/**
 * Minimal repository interface for widget-related timetable access.
 * Keeping this narrow allows both [de.fampopprol.dhbwhorb.services.LectureService]
 * and lightweight fake implementations (for tests / widget providers) to satisfy it.
 */
interface WidgetLectureRepository {
    /**
     * Return all lectures whose start time falls within [start, end] (inclusive).
     */
    suspend fun getLecturesForDateRange(
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<LectureEventEntity>
}

