package de.fampopprol.dhbwhorb.ui

import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeoutFallbackTest {

    @Test
    fun testTimetableViewModelTimeoutFallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        
        // lectureService is null, so it will retry and eventually fail after 5s
        val viewModel = TimetableViewModel(
            lectureService = null,
            lecturerDao = null,
            lectureLecturerCrossRefDao = null,
            coroutineScope = scope
        )
        
        // Initial state
        assertTrue(viewModel.uiState.isLoading, "Initially should be loading")
        
        // Advance time by 2 seconds
        advanceTimeBy(2000)
        assertTrue(viewModel.uiState.isLoading, "Still loading after 2s")
        
        // Advance time by 4 seconds (total 6s)
        advanceTimeBy(4000)
        
        // It should have failed after 5 attempts (5s)
        assertFalse(viewModel.uiState.isLoading, "Should stop loading after timeout")
        assertEquals("Services not ready after 5 seconds", viewModel.uiState.error)
    }
}
