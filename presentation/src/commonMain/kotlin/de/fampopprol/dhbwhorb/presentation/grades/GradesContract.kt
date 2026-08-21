/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.grades

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Semester

/**
 * The grades screen.
 *
 * One state object with one set of loading flags. `GradesViewModel` kept the same information
 * twice — a `mutableStateOf` state *and* three separate `StateFlow`s for loading, data and
 * refreshing — and the two could report different things at the same moment.
 */
data class GradesState(
    val semesters: List<Semester> = emptyList(),
    /** Null until the semester list has arrived; [Semester.All] for the combined view. */
    val selectedSemester: Semester? = null,
    val grades: List<GradeEntry> = emptyList(),
    val isLoadingSemesters: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Set only while the combined view is selected. */
    val overallGpa: Double? = null,
    /** Set only while a single semester is selected. */
    val semesterGpa: Double? = null,
    val totalCreditsEarned: Double = 0.0,
    val error: AppError? = null,
    /** The user has to log in first; not an error, so the screen shows a prompt. */
    val requiresLogin: Boolean = false,
    /**
     * Whether a load has ever come back.
     *
     * The screen re-enters the composition on every tab switch, so it needs a way to ask for a
     * load *if one is needed* rather than unconditionally — otherwise the store survives the
     * switch but the page refetches anyway, which is the reload this phase removes.
     */
    val hasLoaded: Boolean = false
) {
    val isShowingAllSemesters: Boolean
        get() = selectedSemester?.let { Semester.isAll(it) } == true

    val modulesCompleted: Int get() = grades.count { it.grade != null }
}

sealed interface GradesIntent {
    /** Load the semester list and then the combined view. Also the retry action. */
    data object Load : GradesIntent

    /** Load only if nothing has been loaded yet. Dispatched when the screen appears. */
    data object EnsureLoaded : GradesIntent
    data class SemesterSelected(val semester: Semester) : GradesIntent
    data object Refresh : GradesIntent
}

sealed interface GradesMsg {
    data object LoadingSemesters : GradesMsg
    data class SemestersLoaded(val semesters: List<Semester>) : GradesMsg

    data class LoadStarted(val isRefresh: Boolean) : GradesMsg
    data class SemesterSelected(val semester: Semester) : GradesMsg
    data class GradesLoaded(
        val grades: List<GradeEntry>,
        val average: Double?,
        val earnedCredits: Double,
        val forAllSemesters: Boolean
    ) : GradesMsg
    data class Failed(val error: AppError) : GradesMsg
    data object LoginRequired : GradesMsg
    data object LoadFinished : GradesMsg
}

sealed interface GradesEffect {
    /** A pull-to-refresh failed while grades are still on screen. */
    data class RefreshFailed(val error: AppError) : GradesEffect
}
