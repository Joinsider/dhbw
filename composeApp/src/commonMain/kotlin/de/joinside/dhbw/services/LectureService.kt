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
 */
class LectureService(
    private val database: AppDatabase,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val dualisLectureService: DualisLectureService
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
     * @param forceRefresh Boolean - If true, forces a fresh fetch from Dualis API regardless of cache age
     * @return List<LectureEventEntity>
     */
    suspend fun getLecturesForWeek(week: Int, forceRefresh: Boolean = false): List<LectureEventEntity> {
        Napier.d("Getting lectures for week $week (forceRefresh: $forceRefresh)")
        val (start, end) = TimeHelper.getWeekDatesRelativeToCurrentWeek(week)

        return getLecturesForDateRange(start, end, forceRefresh)
    }

    /**
     * Get lectures from database within a given date range. e.G. for a given week.
     * First checks the database. If empty, fetches from Dualis.
     * If database has data but it's older than 3 days, returns cached data but triggers background refresh.
     *
     * @param startDate LocalDateTime - Start of the date range
     * @param endDate LocalDateTime - End of the date range
     * @param forceRefresh Boolean - If true, forces a fresh fetch from Dualis API regardless of cache age
     * @return List<LectureEventEntity> - Lectures within the specified date range
     */
    suspend fun getLecturesForDateRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        forceRefresh: Boolean = false
    ): List<LectureEventEntity> {
        Napier.d("Getting lectures for date range: $startDate to $endDate (forceRefresh: $forceRefresh)")

        // If forceRefresh is true, fetch fresh data from Dualis
        if (forceRefresh) {
            Napier.d("Force refresh requested, fetching from Dualis")
            return fetchAndStoreLecturesFromDualis(startDate, endDate)
        }

        // Check if we need to fetch from Dualis first
        val lectures = getLecturesForWeekFromDatabase(startDate, endDate)
        if (lectures.isEmpty()) {
            Napier.d("No lectures in database, fetching from Dualis")
            return fetchAndStoreLecturesFromDualis(startDate, endDate)
        }

        // Check if data is stale (older than 3 days)
        checkAndRefreshIfStale(startDate, endDate)
        return lectures
    }

    /**
     * Get lectures from database for a specific week.
     */
    private suspend fun getLecturesForWeekFromDatabase(
        monday: LocalDateTime,
        sunday: LocalDateTime
    ): List<LectureEventEntity> {
        Napier.d("Getting lectures for week from database")
        return database.lectureDao().getAll().filter { lecture ->
            lecture.startTime >= monday && lecture.endTime <= sunday
        }
    }

    /**
     * Fetch lectures from Dualis API for a specific date range and store them in database.
     *
     * Flow:
     * 1. Fetch from Dualis (gets temp models, DualisLectureService converts and saves to DB)
     * 2. If successful, delete old entries from the database
     * 3. Update sync metadata
     * 4. Return new lectures for UI update
     *
     * @param startDate LocalDateTime - Start of the date range
     * @param endDate LocalDateTime - End of the date range
     * @return List<LectureEventEntity>
     */
    private suspend fun fetchAndStoreLecturesFromDualis(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<LectureEventEntity> {
        Napier.d("Fetching lectures from Dualis for range: $startDate to $endDate")

        try {
            // Step 1: Fetch from Dualis (this also saves to database with associations)
            val result = dualisLectureService.getWeeklyLecturesForWeek(startDate, endDate)

            return when {
                result.isSuccess -> {
                    val newLectures = result.getOrNull() ?: emptyList()

                    if (newLectures.isNotEmpty()) {
                        // Step 2: Delete old entries now that we have new ones
                        // Get the IDs of newly inserted lectures to avoid deleting them
                        val newLectureIds = newLectures.map { it.lectureId }.toSet()
                        deleteOldLecturesInRange(startDate, endDate, excludeIds = newLectureIds)

                        // Step 3: Update sync metadata
                        updateSyncMetadata()

                        Napier.d("Successfully fetched and stored ${newLectures.size} lectures from Dualis")
                    } else {
                        Napier.w("Dualis returned no lectures for the requested range")
                    }

                    // Step 4: Return new lectures for UI update
                    newLectures
                }
                result.isFailure -> {
                    val error = result.exceptionOrNull()
                    Napier.e("Failed to fetch lectures from Dualis: ${error?.message}", error)
                    emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Napier.e("Exception while fetching lectures from Dualis: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Delete old lectures in the specified date range, excluding the newly inserted ones.
     * This is called AFTER fetching new lectures to clean up old data.
     *
     * @param startDate Start of the date range
     * @param endDate End of the date range
     * @param excludeIds Set of lecture IDs to exclude from deletion (the newly inserted ones)
     */
    private suspend fun deleteOldLecturesInRange(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        excludeIds: Set<Long>
    ) {
        Napier.d("Deleting old lectures in range: $startDate to $endDate (excluding ${excludeIds.size} new lectures)")

        val existingLectures = database.lectureDao().getAll().filter { lecture ->
            lecture.startTime >= startDate &&
            lecture.endTime <= endDate &&
            lecture.lectureId !in excludeIds
        }

        existingLectures.forEach { lecture ->
            database.lectureDao().delete(lecture)
        }

        Napier.d("Deleted ${existingLectures.size} old lectures")
    }

    /**
     * Update sync metadata after lectures have been fetched.
     */
    private suspend fun updateSyncMetadata() {
        val currentTime = TimeHelper.now()
        val syncMetadata = SyncMetadataEntity(
            key = SYNC_KEY_TIMETABLE,
            lastSyncTimestamp = currentTime
        )
        database.syncMetadataDao().insert(syncMetadata)
        Napier.d("Updated sync metadata")
    }

    /**
     * Fetch lectures from Dualis API for current week.
     *
     * @return List<LectureEventEntity>
     */
    private suspend fun fetchLecturesFromDualisForCurrentWeek(): List<LectureEventEntity> {
        Napier.d("Fetching current week lectures from Dualis")

        try {
            val result = dualisLectureService.getWeeklyLecturesForCurrentWeek()

            return when {
                result.isSuccess -> {
                    val lectures = result.getOrNull() ?: emptyList()
                    Napier.d("Successfully fetched ${lectures.size} lectures from Dualis")
                    lectures
                }
                result.isFailure -> {
                    val error = result.exceptionOrNull()
                    Napier.e("Failed to fetch current week lectures from Dualis: ${error?.message}", error)
                    emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Napier.e("Exception while fetching current week lectures from Dualis: ${e.message}", e)
            return emptyList()
        }
    }


    /**
     * Check if data is stale and trigger background refresh if needed.
     * Does not block the current thread.
     *
     * @param startDate LocalDateTime - Start of the date range
     * @param endDate LocalDateTime - End of the date range
     */
    private suspend fun checkAndRefreshIfStale(startDate: LocalDateTime, endDate: LocalDateTime) {
        val syncMetadata = database.syncMetadataDao().getSyncMetadata(SYNC_KEY_TIMETABLE)
        if (syncMetadata != null && TimeHelper.isDataStale(
                syncMetadata.lastSyncTimestamp,
                SYNC_THRESHOLD_DAYS
            )
        ) {
            Napier.d("Lectures are stale (last sync: ${syncMetadata.lastSyncTimestamp}), refreshing in background")
            refreshLecturesInBackground(startDate, endDate)
        }
    }

    /**
     * Refresh lectures in background without blocking the current thread.
     * Fetches from Dualis and updates the database.
     *
     * @param startDate LocalDateTime - Start of the date range
     * @param endDate LocalDateTime - End of the date range
     */
    private fun refreshLecturesInBackground(startDate: LocalDateTime, endDate: LocalDateTime) {
        Napier.d("Triggering background refresh of lectures for range: $startDate to $endDate")
        coroutineScope.launch {
            try {
                val dualisLectures = fetchAndStoreLecturesFromDualis(startDate, endDate)
                if (dualisLectures.isNotEmpty()) {
                    Napier.d("Background refresh completed successfully with ${dualisLectures.size} lectures")
                } else {
                    Napier.w("Background refresh returned no lectures")
                }
            } catch (e: Exception) {
                Napier.e("Error during background refresh: ${e.message}", e)
            }
        }
    }
}

