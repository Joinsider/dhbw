/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.timetable

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.domain.usecase.AwaitFullWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.GetWeekTimetable
import de.fampopprol.dhbwhorb.domain.usecase.RefreshTimetable
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import de.fampopprol.dhbwhorb.presentation.store.SessionScopedStore
import kotlinx.coroutines.CoroutineScope

/**
 * The timetable pager.
 *
 * Replaces `TimetableViewModel`, whose loading flags lived in a `mutableStateOf` state while its
 * lecture cache lived in a plain map beside it — two places that could disagree about the same
 * week.
 */
class TimetableStore(
    private val getWeekTimetable: GetWeekTimetable,
    private val awaitFullWeekTimetable: AwaitFullWeekTimetable,
    private val refreshTimetable: RefreshTimetable,
    scope: CoroutineScope
) : BaseStore<TimetableState, TimetableIntent, TimetableMsg, TimetableEffect>(
    initialState = TimetableState(),
    scope = scope
), SessionScopedStore {

    /**
     * One load per week at a time. Refresh shares the key with load, so pulling to refresh while
     * a week is still loading does not run both against the same week.
     *
     * Focusing and the dialog are not deduped: they are pure state changes that must always land.
     */
    override fun dedupeKey(intent: TimetableIntent): Any? = when (intent) {
        is TimetableIntent.LoadWeek -> "week-${intent.offset}"
        is TimetableIntent.Refresh -> "week-${intent.offset}"
        else -> null
    }

    override fun reduce(state: TimetableState, msg: TimetableMsg): TimetableState =
        reduceTimetable(state, msg)

    override suspend fun EffectScope<TimetableMsg, TimetableEffect>.handle(
        intent: TimetableIntent,
        state: TimetableState
    ) {
        when (intent) {
            is TimetableIntent.WeekFocused -> {
                // The offset-to-dates mapping reads the clock, so it belongs here and not in the
                // reducer; the reducer only ever sees the dates that came out of it.
                val (start, end) = TimeHelper.getWeekDatesRelativeToCurrentWeek(intent.offset)
                emit(TimetableMsg.WeekFocused(intent.offset, start, end))

                if (state.week(intent.offset).isUntouched) {
                    dispatch(TimetableIntent.LoadWeek(intent.offset))
                }
            }

            is TimetableIntent.LoadWeek -> loadWeek(intent.offset)

            is TimetableIntent.Refresh -> {
                emit(TimetableMsg.LoadStarted(intent.offset, isRefresh = true))
                try {
                    when (val result = refreshTimetable(intent.offset)) {
                        is Outcome.Ok -> emit(
                            TimetableMsg.WeekLoaded(
                                offset = intent.offset,
                                lectures = result.value.lectures,
                                isPartial = false
                            )
                        )
                        is Outcome.Err -> {
                            emit(TimetableMsg.LoadFailed(intent.offset, result.error))
                            // The week the user is looking at keeps its cached lectures, so the
                            // failure is worth saying out loud rather than only colouring a banner.
                            send(TimetableEffect.RefreshFailed(result.error))
                        }
                    }
                } finally {
                    emit(TimetableMsg.LoadFinished(intent.offset))
                }
            }

            is TimetableIntent.LectureOpened ->
                emit(TimetableMsg.LectureSelected(intent.lecture))

            TimetableIntent.LectureDismissed ->
                emit(TimetableMsg.LectureSelected(null))
        }
    }

    private suspend fun EffectScope<TimetableMsg, TimetableEffect>.loadWeek(offset: Int) {
        emit(TimetableMsg.LoadStarted(offset, isRefresh = false))
        try {
            when (val result = getWeekTimetable(offset)) {
                is Outcome.Err -> emit(TimetableMsg.LoadFailed(offset, result.error))
                is Outcome.Ok -> {
                    val week = result.value
                    emit(TimetableMsg.WeekLoaded(offset, week.lectures, isPartial = week.isPartial))

                    // A skeleton has no lecturers and no full course names. The complete week is
                    // already being fetched by the repository, so wait for that one rather than
                    // asking for a second.
                    if (week.isPartial) {
                        when (val full = awaitFullWeekTimetable(offset)) {
                            is Outcome.Ok -> emit(
                                TimetableMsg.WeekLoaded(offset, full.value.lectures, isPartial = false)
                            )
                            is Outcome.Err -> emit(TimetableMsg.LoadFailed(offset, full.error))
                        }
                    }
                }
            }
        } finally {
            emit(TimetableMsg.LoadFinished(offset))
        }
    }
}

/**
 * The timetable state after [msg].
 *
 * A top-level function, not a method: it has no access to a store, a repository or a scope, so its
 * purity is a property of where it lives rather than a promise in a comment. Its tests call it
 * directly — no store, no dispatcher, no `runTest`.
 */
fun reduceTimetable(state: TimetableState, msg: TimetableMsg): TimetableState = when (msg) {
    is TimetableMsg.WeekFocused -> state.copy(
        currentWeekOffset = msg.offset,
        weeks = state.weeks.update(msg.offset) { it.copy(start = msg.start, end = msg.end) }
    )

    is TimetableMsg.LoadStarted -> state.copy(
        weeks = state.weeks.update(msg.offset) {
            it.copy(
                isLoading = !msg.isRefresh,
                isRefreshing = msg.isRefresh,
                error = null
            )
        }
    )

    is TimetableMsg.WeekLoaded -> state.copy(
        weeks = state.weeks.update(msg.offset) {
            it.copy(
                lectures = msg.lectures,
                isPartial = msg.isPartial,
                error = null,
                hasLoaded = true
            )
        }
    )

    is TimetableMsg.LoadFailed -> state.copy(
        weeks = state.weeks.update(msg.offset) { it.copy(error = msg.error) }
    )

    is TimetableMsg.LoadFinished -> state.copy(
        weeks = state.weeks.update(msg.offset) {
            it.copy(isLoading = false, isRefreshing = false)
        }
    )

    is TimetableMsg.LectureSelected -> state.copy(selectedLecture = msg.lecture)
}

/** Replace one week's entry, starting from a default if it does not exist yet. */
private inline fun Map<Int, WeekState>.update(
    offset: Int,
    transform: (WeekState) -> WeekState
): Map<Int, WeekState> = this + (offset to transform(this[offset] ?: WeekState()))
