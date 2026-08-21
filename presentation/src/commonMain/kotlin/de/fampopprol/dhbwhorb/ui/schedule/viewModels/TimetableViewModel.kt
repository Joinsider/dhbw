/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.schedule.viewModels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.domain.usecase.AwaitFullWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.GetWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.RefreshTimetable
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
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * ViewModel for TimetablePage.
 *
 * Loading a week is two steps when the cache is cold: the repository answers with the weekly
 * skeleton straight away and this then waits for the complete week. Both come from the same
 * in-flight fetch, so the pair costs one round of requests rather than two.
 */
class TimetableViewModel(
    private val getWeekTimetable: GetWeekTimetable,
    private val awaitFullWeekTimetable: AwaitFullWeekTimetable,
    private val refreshTimetable: RefreshTimetable,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "TimetableViewModel"
    }

    var uiState by mutableStateOf(TimetableUiState())
        private set

    private val lectureCache = mutableMapOf<Int, List<LectureModel>>()
    private val weekLabelCache = mutableMapOf<Int, WeekLabelData>()

    private val loadMutex = Mutex()

    init {
        loadLecturesForWeek(0)
    }

    fun cleanup() {
        coroutineScope.cancel()
    }

    /** Called by the pager once a page settles. */
    fun loadLecturesForWeek(weekOffset: Int) {
        val weekLabelData = weekLabelCache.getOrPut(weekOffset) { generateWeekLabelData(weekOffset) }

        uiState = uiState.copy(
            currentWeekOffset = weekOffset,
            weekLabelData = weekLabelData,
            lectures = lectureCache[weekOffset] ?: emptyList()
        )

        coroutineScope.launch {
            loadMutex.withLock {
                if (uiState.isLoadingWeeks.contains(weekOffset)) return@withLock
                uiState = uiState.copy(isLoadingWeeks = uiState.isLoadingWeeks + weekOffset)

                try {
                    when (val week = getWeekTimetable(weekOffset)) {
                        is Outcome.Ok -> {
                            publish(week.value)
                            // A skeleton has no lecturers and no full course names; the complete
                            // week is already being fetched, so wait for it rather than asking again.
                            if (week.value.isPartial) {
                                when (val full = awaitFullWeekTimetable(weekOffset)) {
                                    is Outcome.Ok -> publish(full.value)
                                    is Outcome.Err -> fail(weekOffset, full.error)
                                }
                            }
                        }
                        is Outcome.Err -> fail(weekOffset, week.error)
                    }
                } finally {
                    uiState = uiState.copy(isLoadingWeeks = uiState.isLoadingWeeks - weekOffset)
                }
            }
        }
    }

    fun refreshLectures(weekOffset: Int) {
        coroutineScope.launch {
            loadMutex.withLock {
                uiState = uiState.copy(isRefreshingWeeks = uiState.isRefreshingWeeks + weekOffset)
                try {
                    when (val week = refreshTimetable(weekOffset)) {
                        is Outcome.Ok -> publish(week.value)
                        is Outcome.Err -> fail(weekOffset, week.error)
                    }
                } finally {
                    uiState = uiState.copy(isRefreshingWeeks = uiState.isRefreshingWeeks - weekOffset)
                }
            }
        }
    }

    /** Cache the week, and show it if the user has not paged away in the meantime. */
    private fun publish(week: TimetableWeek) {
        val models = week.lectures.map { it.toLectureModel() }
        lectureCache[week.weekOffset] = models

        if (uiState.currentWeekOffset == week.weekOffset) {
            uiState = uiState.copy(lectures = models, error = null)
        }
    }

    private fun fail(weekOffset: Int, error: AppError) {
        Napier.e("Loading week $weekOffset failed: $error", tag = TAG)
        // Only the week on screen may replace the visible state with an error.
        if (uiState.currentWeekOffset == weekOffset) {
            uiState = uiState.copy(error = error)
        }
    }

    /** The cached lectures for a week, for the pager's neighbouring pages. */
    fun getLecturesForWeekSync(weekOffset: Int): List<LectureModel> =
        lectureCache[weekOffset] ?: emptyList()

    fun isWeekLoading(weekOffset: Int): Boolean = uiState.isLoadingWeeks.contains(weekOffset)
    fun isWeekRefreshing(weekOffset: Int): Boolean = uiState.isRefreshingWeeks.contains(weekOffset)

    @OptIn(ExperimentalTime::class)
    private fun generateWeekLabelData(weekOffset: Int): WeekLabelData {
        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val daysToMonday = -currentDate.dayOfWeek.ordinal + (weekOffset * 7)
        val monday = currentDate.plus(daysToMonday, DateTimeUnit.DAY)
        val friday = monday.plus(4, DateTimeUnit.DAY)
        return WeekLabelData(monday.day, monday.month, friday.day, friday.month)
    }

    private fun Lecture.toLectureModel(): LectureModel = LectureModel(
        name = displayName,
        shortName = shortName,
        isTest = isTest,
        start = start,
        end = end,
        lecturers = lecturers,
        location = location
    )
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
    /** The classified reason the visible week could not be loaded, for the UI to phrase. */
    val error: AppError? = null
)
