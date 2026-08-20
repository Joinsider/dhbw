/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui

import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Documents the current retry behaviour of [TimetableViewModel] when its dependencies are
 * unavailable (null), i.e. when the UI renders before service initialisation has finished.
 *
 * Note: the retry loop is a workaround for the missing dependency injection and is scheduled
 * for removal in P2 (Koin). This test is expected to be deleted together with it.
 */
class TimeoutFallbackTest {

    @Test
    fun timetableViewModel_withoutServices_stopsLoadingAfterRetriesAreExhausted() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))

        val viewModel = TimetableViewModel(
            lectureService = null,
            lecturerDao = null,
            lectureLecturerCrossRefDao = null,
            coroutineScope = scope
        )

        // init { loadLecturesForWeek(0) } launches the load; let it reach the loading marker.
        testScheduler.runCurrent()
        assertTrue(
            viewModel.uiState.isLoadingWeeks.contains(0),
            "Week 0 should be marked as loading while the retry loop runs"
        )

        // Retry loop: 5 attempts with 1s delay in between (4s total).
        testScheduler.advanceUntilIdle()

        assertTrue(
            viewModel.uiState.isLoadingWeeks.isEmpty(),
            "Loading marker must be cleared once the retries are exhausted"
        )
        assertEquals(emptyList(), viewModel.uiState.lectures, "No lectures can be loaded without a service")

        // Known gap: the exhausted retry loop surfaces no error at all — the user sees an empty
        // week instead of a cause. Fixed in P3 via the Outcome/AppError model, which is when this
        // assertion is expected to change.
        assertNull(viewModel.uiState.error, "Current behaviour: failure is silent, no error is set")
    }
}
