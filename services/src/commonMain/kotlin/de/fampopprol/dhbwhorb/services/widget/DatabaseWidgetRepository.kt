// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import kotlinx.datetime.LocalDateTime

/**
 * Lightweight [WidgetLectureRepository] that reads directly from the local Room cache
 * without triggering any network calls.
 *
 * Used in [de.fampopprol.dhbwhorb.DualisApplication] so background workers can refresh
 * widget data without rebuilding the full Ktor / auth stack.
 */
class DatabaseWidgetRepository(
    private val dao: LectureEventDao,
) : WidgetLectureRepository {

    override suspend fun getLecturesForDateRange(
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<LectureEventEntity> =
        dao.getAll().filter { lecture ->
            lecture.startTime >= start && lecture.endTime <= end
        }
}

