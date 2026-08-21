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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Cache to hold lectures for different week offsets
    private val lectureCache = mutableMapOf<Int, List<LectureModel>>()
    private val weekLabelCache = mutableMapOf<Int, WeekLabelData>()

    private val loadMutex = Mutex()

    init {
        loadLecturesForWeek(0)
    }


    fun cleanup() {
        coroutineScope.cancel()
    }

    /**
     * Load lectures for a specific week offset.
     * This is called by the HorizontalPager when a new page is settled.
     */
    fun loadLecturesForWeek(weekOffset: Int) {
        // Generate week label data if not in cache
        val weekLabelData = weekLabelCache.getOrPut(weekOffset) { generateWeekLabelData(weekOffset) }
        
        // Update current offset in UI state
        uiState = uiState.copy(
            currentWeekOffset = weekOffset,
            weekLabelData = weekLabelData
        )

        // If already in cache, update immediate UI
        if (lectureCache.containsKey(weekOffset)) {
            uiState = uiState.copy(lectures = lectureCache[weekOffset] ?: emptyList())
        } else {
            uiState = uiState.copy(lectures = emptyList())
        }

        coroutineScope.launch {
            loadMutex.withLock {
                if (uiState.isLoadingWeeks.contains(weekOffset)) return@withLock
                
                uiState = uiState.copy(isLoadingWeeks = uiState.isLoadingWeeks + weekOffset)
                
                try {
                    val (lectures, isReloading) = lectureService.getLecturesForWeekStaged(weekOffset)
                    val lectureModels = lectures.map { it.toLectureModel() }
                    
                    lectureCache[weekOffset] = lectureModels
                    
                    // Update UI if this is still the active week
                    if (uiState.currentWeekOffset == weekOffset) {
                        uiState = uiState.copy(
                            lectures = lectureModels,
                            error = null
                        )
                    }

                    if (isReloading) {
                        val fullLectures = lectureService.getLecturesForWeek(weekOffset, forceRefresh = false)
                        val fullModels = fullLectures.map { it.toLectureModel() }
                        lectureCache[weekOffset] = fullModels
                        if (uiState.currentWeekOffset == weekOffset) {
                            uiState = uiState.copy(lectures = fullModels)
                        }
                    }
                } catch (e: Exception) {
                    Napier.e("Error loading week $weekOffset", e, tag = TAG)
                } finally {
                    uiState = uiState.copy(isLoadingWeeks = uiState.isLoadingWeeks - weekOffset)
                }
            }
        }
    }

    /**
     * Refresh lectures for a specific week offset.
     */
    fun refreshLectures(weekOffset: Int) {
        coroutineScope.launch {
            loadMutex.withLock {
                uiState = uiState.copy(isRefreshingWeeks = uiState.isRefreshingWeeks + weekOffset)
                try {
                    val lectureEntities = lectureService.getLecturesForWeek(weekOffset, forceRefresh = true)

                    val lectureModels = lectureEntities.map { it.toLectureModel() }
                    lectureCache[weekOffset] = lectureModels

                    if (uiState.currentWeekOffset == weekOffset) {
                        uiState = uiState.copy(
                            lectures = lectureModels,
                            error = null
                        )
                    }
                } catch (e: Exception) {
                    uiState = uiState.copy(error = "Failed to refresh: ${e.message}")
                } finally {
                    uiState = uiState.copy(isRefreshingWeeks = uiState.isRefreshingWeeks - weekOffset)
                }
            }
        }
    }

    /**
     * Get lectures for a specific week from cache (if available)
     */
    fun getLecturesForWeekSync(weekOffset: Int): List<LectureModel> {
        return lectureCache[weekOffset] ?: emptyList()
    }
    
    fun isWeekLoading(weekOffset: Int): Boolean = uiState.isLoadingWeeks.contains(weekOffset)
    fun isWeekRefreshing(weekOffset: Int): Boolean = uiState.isRefreshingWeeks.contains(weekOffset)

    @OptIn(ExperimentalTime::class)
    private fun generateWeekLabelData(weekOffset: Int): WeekLabelData {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentDate = now.date
        val currentDayOfWeek = currentDate.dayOfWeek.ordinal 
        val daysToMonday = -currentDayOfWeek + (weekOffset * 7)
        val monday = currentDate.plus(daysToMonday, DateTimeUnit.DAY)
        val friday = monday.plus(4, DateTimeUnit.DAY)
        return WeekLabelData(monday.day, monday.month, friday.day, friday.month)
    }

    private suspend fun LectureEventEntity.toLectureModel(): LectureModel {
        val lecturerNames = try {
            val crossRefs = lectureLecturerCrossRefDao.getByLectureId(lectureId)
            crossRefs.mapNotNull { lecturerDao.getById(it.lecturerId)?.lecturerName }
        } catch (e: Exception) { emptyList() }

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

data class WeekLabelData(
    val mondayDay: Int,
    val mondayMonth: Month,
    val fridayDay: Int,
    val fridayMonth: Month
)

data class TimetableUiState(
    val lectures: List<LectureModel> = emptyList(),
    val weekLabelData: WeekLabelData? = null,
    val currentWeekOffset: Int = 0,
    val isLoadingWeeks: Set<Int> = emptySet(),
    val isRefreshingWeeks: Set<Int> = emptySet(),
    val error: String? = null
)
