package de.joinside.dhbw.ui.schedule.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.joinside.dhbw.data.storage.database.dao.timetable.LecturerDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.services.LectureService
import de.joinside.dhbw.ui.schedule.models.LectureModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
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
    private val lectureService: LectureService,
    private val lecturerDao: LecturerDao,
    private val lectureLecturerCrossRefDao: LectureLecturerCrossRefDao,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "TimetableViewModel"
    }

    var uiState by mutableStateOf(TimetableUiState())
        private set

    private var currentWeekOffset = 0

    init {
        loadLecturesForCurrentWeek()
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
     */
    fun refreshLectures() {
        val refreshWeekOffset = currentWeekOffset
        uiState = uiState.copy(isRefreshing = true)

        coroutineScope.launch {
            try {
                Napier.d("Refreshing lectures for week offset: $refreshWeekOffset", tag = TAG)

                // Force fetch from API
                val lectureEntities = lectureService.getLecturesForWeek(refreshWeekOffset, forceRefresh = true)
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
            }
        }
    }

    /**
     * Load lectures for a specific week offset from current week.
     */
    private fun loadLecturesForWeek(weekOffset: Int) {
        // Stage 1: set reloading to true, but keep weekly view visible
        uiState = uiState.copy(isLoading = true, error = null)

        coroutineScope.launch {
            try {
                Napier.d("Loading lectures (staged) for week offset: $weekOffset", tag = TAG)

                val (lectures, isReloading) = lectureService.getLecturesForWeekStaged(weekOffset)
                val lectureModels = lectures.map { entity -> entity.toLectureModel() }
                val weekLabelData = generateWeekLabelData(weekOffset)

                // Show whatever we have (cache or skeleton), disable navigation via isLoading flag
                uiState = uiState.copy(
                    lectures = lectureModels,
                    weekLabelData = weekLabelData,
                    currentWeekOffset = weekOffset,
                    isLoading = isReloading,
                    error = null
                )

                // If still reloading in background, poll/update once background fetch likely finished
                if (isReloading) {
                    // Simple follow-up: try fetching from DB after background refresh completes implicitly
                    // Reuse existing service which returns DB data when available
                    val fullLectures = lectureService.getLecturesForWeek(weekOffset, forceRefresh = false)
                    val fullModels = fullLectures.map { it.toLectureModel() }

                    // Only update if we're still on the same week
                    if (currentWeekOffset == weekOffset) {
                        uiState = uiState.copy(
                            lectures = fullModels,
                            isLoading = false,
                            error = null
                        )
                    }
                }

                Napier.d("Staged load complete for week $weekOffset (lectures: ${uiState.lectures.size})", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Error loading lectures (staged): ${e.message}", e, tag = TAG)
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Failed to load lectures: ${e.message}"
                )
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
            val crossRefs = lectureLecturerCrossRefDao.getByLectureId(lectureId)
            crossRefs.mapNotNull { crossRef ->
                lecturerDao.getById(crossRef.lecturerId)?.lecturerName
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
