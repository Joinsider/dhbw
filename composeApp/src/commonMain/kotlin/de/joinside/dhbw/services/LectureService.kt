package de.joinside.dhbw.services

import de.joinside.dhbw.data.dualis.remote.services.DualisLectureService
import de.joinside.dhbw.data.helpers.TimeHelper
import de.joinside.dhbw.data.storage.database.AppDatabase
import de.joinside.dhbw.data.storage.database.entities.SyncMetadataEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

/**
 * The lecture service handles the business logic for lectures.
 * It checks the database for lectures and returns them.
 * If no lectures are found, it will fetch the dualis API, return the lectures and store them in the database for later use.
 *
 * If lectures are found in the database, but the stored fetchDate is older than 3 days ago,
 * it will fetch the dualis API again in the background and return the new lectures but in the meantime it should still return the stored lectures.
 * That way the user won't have to wait until dualis is fetched successfully.
 *
 */
class LectureService(
    private val database: AppDatabase,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val SYNC_KEY_TIMETABLE = "timetable"
        private const val SYNC_THRESHOLD_DAYS = 3
    }

    /**
     * Get lectures for current week.
     * First checks the database. If empty, fetches from Dualis.
     * If database has data but it's older than 3 days, returns cached data but triggers background refresh.
     *
     * @return List<LectureEventEntity>
     */
    suspend fun getLecturesForCurrentWeek(): List<LectureEventEntity> {
        val (monday, sunday) = TimeHelper.getCurrentWeekDates()
        return getLecturesForDateRange(monday, sunday)
    }

    /**
     * Get lectures for a given week.
     * It returns the lectures from the database.
     * And if the fetched lectures are older than 3 days ago, it will fetch them from the dualis api.
     * This fetching should not block the UI. But update the database in the background.
     * If the database update was successful it will return the new lectures and update the UI.
     *
     * @param week Int - The number of weeks relative to the current week. So -1 is the last week, 0 is the current week, 1 is the next week, etc.
     * @return List<LectureEventEntity>
     */
    suspend fun getLecturesForWeek(week: Int): List<LectureEventEntity> {
        Napier.d("Getting lectures for week $week")
        val (startDate, endDate) = TimeHelper.getCurrentWeekDates()

        // TODO: Get the start and end date of the given week relative to the current week with the number


        return getLecturesForDateRange(startDate, endDate)
    }

    /**
     * Get lectures from database within a given date range. e.G. for a given week.
     * First checks the database. If empty, fetches from Dualis.
     * If database has data but it's older than 3 days, returns cached data but triggers background refresh.
     *
     * @param startDate LocalDateTime - Start of the date range
     * @param endDate LocalDateTime - End of the date range
     * @return List<LectureEventEntity> - Lectures within the specified date range
     */
    suspend fun getLecturesForDateRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<LectureEventEntity> {
        Napier.d("Getting lectures for date range: $startDate to $endDate")

        // Check if we need to fetch from Dualis first
        val lectures = getLecturesForWeekFromDatabase(startDate, endDate)
        if (lectures.isEmpty()) {
            Napier.d("No lectures in database, fetching from Dualis")
            val dualisLectures = DualisLectureService.getWeeklyLecturesForWeek(startDate, endDate)
            storeLecturesInDatabase(dualisLectures)
            return dualisLectures
        }
        // Check if data is stale (older than 3 days)
        // TODO: Only check the dates within this week not every event in the db as this would result in a lot of false positives
        checkAndRefreshIfStale()
        return lectures
    }

    private suspend fun getLecturesForWeekFromDatabase(
        monday: LocalDateTime,
        sunday: LocalDateTime
    ): List<LectureEventEntity> {
        Napier.d("Getting lectures for week from database")
        return database.lectureDao().getAll().filter(
            { lecture ->
                lecture.startTime >= monday && lecture.endTime <= sunday
            }
        )
    }

    /**
     * Get lectures from database.
     *
     * @return List<LectureEventEntity>
     */
    private suspend fun getAllLecturesFromDatabase(): List<LectureEventEntity> {
        Napier.d("Getting lectures from database")
        return database.lectureDao().getAll()
    }

    /**
     * Get lectures from dualis api.
     *
     * @return List<LectureEventEntity>
     */
    private fun getAllLecturesFromDualis(): List<LectureEventEntity> {
        Napier.d("Getting lectures from dualis")
        // TODO: Implement Dualis API integration
        // TODO: 1. Use DualisLectureService to fetch lectures from Dualis
        // TODO: 2. Parse the response and convert to LectureEventEntity list
        // TODO: 3. Handle authentication errors and network errors
        // TODO: 4. Return the parsed lectures
        return emptyList()
    }

    /**
     * Store lectures in database.
     * Clears existing lectures and inserts new ones, then updates sync metadata.
     *
     * @param lectures List<LectureEventEntity>
     */
    private suspend fun storeLecturesInDatabase(lectures: List<LectureEventEntity>) {
        Napier.d("Storing ${lectures.size} lectures in database")

        // Delete all existing lectures before inserting new ones
        // TODO: Consider implementing a more sophisticated sync strategy that only updates changed lectures
        val existingLectures = database.lectureDao().getAll()
        existingLectures.forEach { lecture ->
            database.lectureDao().delete(lecture)
        }

        // Insert new lectures
        if (lectures.isNotEmpty()) {
            database.lectureDao().insertAll(lectures)
        }

        // Update sync metadata with current timestamp
        val currentTime = TimeHelper.now()
        val syncMetadata = SyncMetadataEntity(
            key = SYNC_KEY_TIMETABLE,
            lastSyncTimestamp = currentTime
        )
        database.syncMetadataDao().insert(syncMetadata)

        Napier.d("Successfully stored lectures and updated sync metadata")
    }

    /**
     * Check if data is stale and trigger background refresh if needed.
     * Does not block the current thread.
     */
    private suspend fun checkAndRefreshIfStale() {
        val syncMetadata = database.syncMetadataDao().getSyncMetadata(SYNC_KEY_TIMETABLE)
        if (syncMetadata != null && TimeHelper.isDataStale(
                syncMetadata.lastSyncTimestamp,
                SYNC_THRESHOLD_DAYS
            )
        ) {
            Napier.d("Lectures are stale (last sync: ${syncMetadata.lastSyncTimestamp}), refreshing in background")
            refreshLecturesInBackground()
        }
    }

    /**
     * Refresh lectures in background without blocking the current thread.
     * Fetches from Dualis and updates the database.
     */
    private fun refreshLecturesInBackground() {
        Napier.d("Triggering background refresh of lectures")
        coroutineScope.launch {
            try {
                val dualisLectures = getAllLecturesFromDualis()
                if (dualisLectures.isNotEmpty()) {
                    storeLecturesInDatabase(dualisLectures)
                    Napier.d("Background refresh completed successfully")
                } else {
                    Napier.w("Background refresh returned no lectures")
                }
            } catch (e: Exception) {
                Napier.e("Error during background refresh: ${e.message}", e)
                // TODO: Consider implementing retry logic or error handling strategy
            }
        }
    }
}

