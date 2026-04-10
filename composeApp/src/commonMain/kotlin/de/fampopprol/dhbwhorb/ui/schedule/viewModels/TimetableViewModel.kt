package de.fampopprol.dhbwhorb.ui.schedule.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LecturerDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.services.LectureService
import de.fampopprol.dhbwhorb.ui.schedule.models.LectureModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Mutex
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.collections.emptyList
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * ViewModel for TimetablePage.
 * Manages lecture data fetching and state.
 */
class TimetableViewModel(
    private val lectureService: LectureService?,
    private val lecturerDao: LecturerDao?,
    private val lectureLecturerCrossRefDao: LectureLecturerCrossRefDao?,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "TimetableViewModel"
    }

    var uiState by mutableStateOf(TimetableUiState())
        private set

    private var currentWeekOffset = 0

    // Race condition prevention: Mutex serializes all load operations
    // Prevents concurrent initial fetch + refresh from race-conditioning data updates
    private val loadMutex = Mutex()

    // Three separate StateFlows for deterministic loading state management
    // - isLoading: true during initial data fetch (skeleton data shown)
    // - data: current data (empty initially, skeleton data, then full data)
    // - isRefreshing: true during pull-to-refresh or background sync
    private val _isLoading = MutableStateFlow<Boolean>(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _data = MutableStateFlow<List<LectureModel>>(emptyList())
    val data: StateFlow<List<LectureModel>> = _data

    private val _isRefreshing = MutableStateFlow<Boolean>(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        loadLecturesForCurrentWeek()
    }

    /**
     * Retries a database or network operation for up to 5 seconds.
     * This ensures that if services are still initializing in the background,
     * the ViewModel will eventually get the data once they are ready.
     * 
     * Retry Strategy:
     * - Max attempts: 5
     * - Delay between attempts: 1 second
     * - Total duration: ~5 seconds
     */
    private suspend fun <T> getDataWithRetry(
        actionName: String,
        block: suspend () -> T?
    ): T? {
        val maxAttempts = 5
        val delayMillis = 1000L
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                // Check if services are ready (not null)
                // This is specifically for Task 1.2 and 2.3 requirements
                val result = block()
                if (result != null) return result
                
                Napier.d("Attempt $attempt for $actionName returned null (service might not be ready), retrying...", tag = TAG)
            } catch (e: Exception) {
                lastException = e
                Napier.w("Attempt $attempt for $actionName failed: ${e.message}", tag = TAG)
            }

            if (attempt < maxAttempts) {
                delay(delayMillis)
            }
        }

        Napier.e("All $maxAttempts attempts failed for $actionName. Last error: ${lastException?.message}", tag = TAG)
        return null
    }

    /**
     * Cleanup resources and cancel coroutine scope.
     */
    fun cleanup() {
        Napier.d("Cleaning up TimetableViewModel", tag = TAG)
        coroutineScope.cancel()
    }

    /**
     * Load lectures for the current week.
     */
    fun loadLecturesForCurrentWeek() {
        currentWeekOffset = 0
        loadLecturesForWeek(currentWeekOffset)
    }

    /**
     * Navigate to the previous week.
     */
    fun goToPreviousWeek() {
        currentWeekOffset--
        loadLecturesForWeek(currentWeekOffset)
    }

    /**
     * Navigate to the next week.
     */
    fun goToNextWeek() {
        currentWeekOffset++
        loadLecturesForWeek(currentWeekOffset)
    }

    /**
     * Refresh lectures for the current week from the API.
     * This forces a fresh fetch and only updates if new data is received.
     * Uses Mutex to prevent concurrent operations from race-conditioning data updates.
     */
    fun refreshLectures() {
        val refreshWeekOffset = currentWeekOffset
        coroutineScope.launch {
            loadMutex.withLock {
                _isRefreshing.value = true
                try {
                    Napier.d("Refreshing lectures for week offset: $refreshWeekOffset", tag = TAG)

                    // Force fetch from API with retry
                    val lectureEntities = getDataWithRetry("Refresh Lectures") {
                        if (lectureService == null) {
                            Napier.w("LectureService not ready during refresh", tag = TAG)
                            return@getDataWithRetry null
                        }
                        lectureService.getLecturesForWeek(refreshWeekOffset, forceRefresh = true)
                    } ?: emptyList()

                    val lectureModels = lectureEntities.map { entity ->
                        entity.toLectureModel()
                    }

                    val weekLabelData = generateWeekLabelData(refreshWeekOffset)

                    if (currentWeekOffset == refreshWeekOffset) {
                        uiState = uiState.copy(
                            lectures = lectureModels,
                            weekLabelData = weekLabelData,
                            currentWeekOffset = refreshWeekOffset,
                            isRefreshing = false,
                            error = null
                        )
                        _data.value = lectureModels
                    } else {
                        Napier.d("Ignored refresh result because week offset changed from $refreshWeekOffset to $currentWeekOffset", tag = TAG)
                        uiState = uiState.copy(isRefreshing = false)
                    }

                    Napier.d("Successfully refreshed ${lectureModels.size} lectures", tag = TAG)
                } catch (e: Exception) {
                    Napier.e("Error refreshing lectures: ${e.message}", e, tag = TAG)
                    uiState = uiState.copy(
                        isRefreshing = false,
                        error = "Failed to refresh lectures: ${e.message}"
                    )
                } finally {
                    _isRefreshing.value = false
                }
            }
        }
    }

    /**
     * Load lectures for a specific week offset from current week.
     * Uses Mutex to prevent concurrent loads from race-conditioning data updates.
     */
    private fun loadLecturesForWeek(weekOffset: Int) {
        // Immediately clear lectures and update week label when switching weeks
        // This shows the empty grid while loading instead of the old week's data
        val weekLabelData = generateWeekLabelData(weekOffset)
        uiState = uiState.copy(
            lectures = emptyList(),
            weekLabelData = weekLabelData,
            currentWeekOffset = weekOffset,
            isLoading = true,
            error = null
        )

        coroutineScope.launch {
            loadMutex.withLock {
                _isLoading.value = true
                try {
                    Napier.d("Loading lectures (staged) for week offset: $weekOffset", tag = TAG)

                    // Staged fetch with retry logic
                    val stagedResult = getDataWithRetry("Load Lectures Staged") {
                        if (lectureService == null) {
                            Napier.w("LectureService not ready during staged load", tag = TAG)
                            return@getDataWithRetry null
                        }
                        lectureService.getLecturesForWeekStaged(weekOffset)
                    }

                    if (stagedResult != null) {
                        val (lectures, isReloading) = stagedResult
                        val lectureModels = lectures.map { entity -> entity.toLectureModel() }

                        // Only update if we're still on the same week (user didn't navigate away)
                        if (currentWeekOffset == weekOffset) {
                            uiState = uiState.copy(
                                lectures = lectureModels,
                                isLoading = isReloading,
                                error = null
                            )
                            _data.value = lectureModels
                        }

                        // If still reloading in background, poll/update once background fetch likely finished
                        if (isReloading) {
                            // Simple follow-up: try fetching from DB after background refresh completes implicitly
                            // Reuse existing service which returns DB data when available
                            val fullLectures = getDataWithRetry("Load Full Lectures") {
                                lectureService?.getLecturesForWeek(weekOffset, forceRefresh = false)
                            } ?: emptyList()

                            val fullModels = fullLectures.map { it.toLectureModel() }

                            // Only update if we're still on the same week
                            if (currentWeekOffset == weekOffset) {
                                uiState = uiState.copy(
                                    lectures = fullModels,
                                    isLoading = false,
                                    error = null
                                )
                                _data.value = fullModels
                            }
                        }
                    } else {
                        uiState = uiState.copy(
                            isLoading = false,
                            error = "Services not ready after 5 seconds"
                        )
                    }

                    Napier.d("Staged load complete for week $weekOffset (lectures: ${uiState.lectures.size})", tag = TAG)
                } catch (e: Exception) {
                    Napier.e("Error loading lectures (staged): ${e.message}", e, tag = TAG)
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "Failed to load lectures: ${e.message}"
                    )
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Generate week label data for formatting in the UI layer.
     * Returns the Monday-Friday date range information.
     */
    @OptIn(ExperimentalTime::class)
    private fun generateWeekLabelData(weekOffset: Int): WeekLabelData {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentDate = now.date

        // Calculate days to add to get to Monday of the target week
        // In kotlinx.datetime, DayOfWeek.MONDAY.ordinal = 0, SUNDAY.ordinal = 6
        val currentDayOfWeek = currentDate.dayOfWeek.ordinal // Monday = 0, Sunday = 6
        val daysToMonday = -currentDayOfWeek + (weekOffset * 7)

        // Get Monday and Friday of the target week (always show full week)
        val monday = currentDate.plus(daysToMonday, DateTimeUnit.DAY)
        val friday = monday.plus(4, DateTimeUnit.DAY)

        Napier.d("Current date: $currentDate, Day of week: ${currentDate.dayOfWeek} (ordinal: $currentDayOfWeek)", tag = TAG)
        Napier.d("Week offset: $weekOffset, Days to Monday: $daysToMonday", tag = TAG)
        Napier.d("Monday: $monday, Friday: $friday", tag = TAG)

        return WeekLabelData(
            mondayDay = monday.day,
            mondayMonth = monday.month,
            fridayDay = friday.day,
            fridayMonth = friday.month
        )
    }

    /**
     * Convert LectureEventEntity to LectureModel for UI.
     * Uses primary purple color for regular lectures and red for tests/exams.
     * Fetches the actual lecturer names from the database via the junction table.
     */
    private suspend fun LectureEventEntity.toLectureModel(): LectureModel {
        // Fetch lecturer names from database via junction table
        val lecturerNames = try {
            if (lectureLecturerCrossRefDao == null || lecturerDao == null) {
                Napier.w("Database DAOs not ready for toLectureModel", tag = TAG)
                emptyList()
            } else {
                val crossRefs = lectureLecturerCrossRefDao.getByLectureId(lectureId)
                crossRefs.mapNotNull { crossRef ->
                    lecturerDao.getById(crossRef.lecturerId)?.lecturerName
                }
            }
        } catch (e: Exception) {
            Napier.w("Failed to fetch lecturer names for lecture ID $lectureId: ${e.message}", tag = TAG)
            emptyList()
        }

        return LectureModel(
            name = fullSubjectName ?: shortSubjectName,
            shortName = shortSubjectName,
            isTest = isTest,
            start = startTime,
            end = endTime,
            lecturers = lecturerNames,
            location = location
        )
    }
}

/**
 * Data class containing week label information for formatting in the UI.
 */
data class WeekLabelData(
    val mondayDay: Int,
    val mondayMonth: Month,
    val fridayDay: Int,
    val fridayMonth: Month
)

/**
 * UI State for TimetablePage.
 */
data class TimetableUiState(
    val lectures: List<LectureModel> = emptyList(),
    val weekLabelData: WeekLabelData? = null,
    val currentWeekOffset: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)
