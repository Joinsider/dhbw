/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.grades

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.domain.model.GradeEntry

/** One semester's results, in the order [GradesState.sections] puts them. */
data class SemesterGrades(
    val semesterName: String,
    val grades: List<GradeEntry>
)

/**
 * The grades screen.
 *
 * There is one view: everything, grouped by semester. The semester picker is gone — it split the
 * same data into a second mode with its own average, its own loading flags and its own way of
 * being wrong, and nobody wanted to look at one semester at a time badly enough to pay for that.
 *
 * One state object with one set of loading flags. `GradesViewModel` kept the same information
 * twice — a `mutableStateOf` state *and* three separate `StateFlow`s for loading, data and
 * refreshing — and the two could report different things at the same moment.
 */
data class GradesState(
    /** Every semester's grades, oldest semester first, modules alphabetical within a semester. */
    val grades: List<GradeEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val overallGpa: Double? = null,
    val totalCreditsEarned: Double = 0.0,
    /**
     * How many modules those credits came from.
     *
     * Computed with the credits rather than derived from [grades] here: both answer "what
     * counts", and one repeated module counted differently in the two places is exactly the kind
     * of mismatch this screen is meant to be free of.
     */
    val modulesCompleted: Int = 0,
    val error: AppError? = null,
    /** The user has to log in first; not an error, so the screen shows a prompt. */
    val requiresLogin: Boolean = false,
    /**
     * Whether a load has ever come back.
     *
     * The screen re-enters the composition on every tab switch, so it needs a way to ask for a
     * load *if one is needed* rather than unconditionally — otherwise the store survives the
     * switch but the page refetches anyway, which is the reload P4 removed.
     */
    val hasLoaded: Boolean = false
) {
    /**
     * The grades grouped into the sections both UIs draw.
     *
     * Derived here rather than in each UI: two groupings of the same list are two chances to
     * order them differently, and the order is the thing this screen most recently got wrong.
     * [grades] arrives sorted, and grouping preserves that.
     */
    val sections: List<SemesterGrades>
        get() = grades.groupBy { it.semesterName }
            .map { (name, entries) -> SemesterGrades(name, entries) }
}

sealed interface GradesIntent {
    /** Load everything. Also the retry action. */
    data object Load : GradesIntent

    /** Load only if nothing has been loaded yet. Dispatched when the screen appears. */
    data object EnsureLoaded : GradesIntent
    data object Refresh : GradesIntent
}

sealed interface GradesMsg {
    data class LoadStarted(val isRefresh: Boolean) : GradesMsg
    data class GradesLoaded(
        val grades: List<GradeEntry>,
        val average: Double?,
        val earnedCredits: Double,
        val completedModules: Int
    ) : GradesMsg
    data class Failed(val error: AppError) : GradesMsg
    data object LoginRequired : GradesMsg
    data object LoadFinished : GradesMsg
}

sealed interface GradesEffect {
    /** A pull-to-refresh failed while grades are still on screen. */
    data class RefreshFailed(val error: AppError) : GradesEffect
}
