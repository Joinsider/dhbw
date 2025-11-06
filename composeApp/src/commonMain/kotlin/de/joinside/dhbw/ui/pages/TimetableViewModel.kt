package de.joinside.dhbw.ui.pages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.services.LectureService
import de.joinside.dhbw.ui.schedule.modules.LectureModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * ViewModel for TimetablePage.
 * Manages lecture data fetching and state.
 */
class TimetableViewModel(
    private val lectureService: LectureService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "TimetableViewModel"

        // Color palette for lectures
        private val lectureColors = listOf(
            Color(0xFF6200EE), // Purple
            Color(0xFF03DAC5), // Teal
            Color(0xFFFF6F00), // Orange
            Color(0xFF2196F3), // Blue
            Color(0xFF4CAF50), // Green
            Color(0xFFE91E63), // Pink
            Color(0xFF9C27B0), // Deep Purple
            Color(0xFF00BCD4), // Cyan
        )
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
     * Load lectures for a specific week offset from current week.
     */
    private fun loadLecturesForWeek(weekOffset: Int) {
        uiState = uiState.copy(isLoading = true, error = null)

        coroutineScope.launch {
            try {
                Napier.d("Loading lectures for week offset: $weekOffset", tag = TAG)

                val lectureEntities = lectureService.getLecturesForWeek(weekOffset)
                val lectureModels = lectureEntities.mapIndexed { index, entity ->
                    entity.toLectureModel(lectureColors[index % lectureColors.size])
                }

                val weekLabel = generateWeekLabel(weekOffset)

                uiState = uiState.copy(
                    lectures = lectureModels,
                    weekLabel = weekLabel,
                    currentWeekOffset = weekOffset,
                    isLoading = false,
                    error = null
                )

                Napier.d("Successfully loaded ${lectureModels.size} lectures for week $weekOffset", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Error loading lectures: ${e.message}", e, tag = TAG)
                uiState = uiState.copy(
                    isLoading = false,
                    error = "Failed to load lectures: ${e.message}"
                )
            }
        }
    }

    /**
     * Generate a week label based on the offset from current week.
     */
    @OptIn(ExperimentalTime::class)
    private fun generateWeekLabel(weekOffset: Int): String {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        // Calculate the week number (ISO 8601 week numbering)
        // This is a simplified version - you might want to use a proper week calculation library
        val currentWeekNumber = getWeekNumber(now)
        val targetWeekNumber = currentWeekNumber + weekOffset

        return when (weekOffset) {
            0 -> "This Week (Week $targetWeekNumber)"
            -1 -> "Last Week (Week $targetWeekNumber)"
            1 -> "Next Week (Week $targetWeekNumber)"
            else -> "Week $targetWeekNumber"
        }
    }

    /**
     * Simple week number calculation (simplified ISO 8601).
     * You might want to use a proper library for accurate week numbers.
     */
    private fun getWeekNumber(dateTime: kotlinx.datetime.LocalDateTime): Int {
        val dayOfYear = dateTime.dayOfYear
        val dayOfWeek = dateTime.dayOfWeek.ordinal + 1 // Monday = 1, Sunday = 7

        // Simplified week calculation: (dayOfYear + dayOfWeek) / 7
        return ((dayOfYear + dayOfWeek - 1) / 7) + 1
    }

    /**
     * Convert LectureEventEntity to LectureModel for UI.
     */
    private fun LectureEventEntity.toLectureModel(color: Color): LectureModel {
        return LectureModel(
            name = fullSubjectName ?: shortSubjectName,
            color = color,
            start = startTime,
            end = endTime,
            lecturer = lecturerId?.toString() ?: "Unknown", // TODO: Fetch actual lecturer name
            location = location
        )
    }
}

/**
 * UI State for TimetablePage.
 */
data class TimetableUiState(
    val lectures: List<LectureModel> = emptyList(),
    val weekLabel: String = "This Week",
    val currentWeekOffset: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

