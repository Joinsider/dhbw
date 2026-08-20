package de.fampopprol.dhbwhorb.ui

import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import de.fampopprol.dhbwhorb.ui.grades.viewModels.GradesViewModel
import de.fampopprol.dhbwhorb.ui.documents.viewModels.DocumentsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ViewModelCleanupTest {

    @Test
    fun testTimetableViewModelCleanup() {
        val job = Job()
        val scope = CoroutineScope(job)
        val viewModel = TimetableViewModel(
            lectureService = null,
            lecturerDao = null,
            lectureLecturerCrossRefDao = null,
            coroutineScope = scope
        )
        
        viewModel.cleanup()
        assertTrue(job.isCancelled, "TimetableViewModel coroutine scope should be cancelled after cleanup")
    }

    @Test
    fun testCoroutineCancellationOnCleanup() = runTest {
        val job = Job()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(job + dispatcher)
        
        var operationCancelled = false
        val viewModel = TimetableViewModel(
            lectureService = null,
            lecturerDao = null,
            lectureLecturerCrossRefDao = null,
            coroutineScope = scope
        )
        
        scope.launch {
            try {
                delay(10000)
            } catch (e: CancellationException) {
                operationCancelled = true
            }
        }
        
        // Let the coroutine start
        testScheduler.runCurrent()
        
        viewModel.cleanup()
        
        // Let cancellation propagate
        testScheduler.advanceUntilIdle()
        
        assertTrue(job.isCancelled, "Scope should be cancelled")
        assertTrue(operationCancelled, "In-flight coroutine should be cancelled when ViewModel is cleaned up")
    }

    @Test
    fun testRaceConditionOnNavigationDuringLoading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(Job() + dispatcher)
        
        val viewModel = TimetableViewModel(
            lectureService = null,
            lecturerDao = null,
            lectureLecturerCrossRefDao = null,
            coroutineScope = scope
        )
        
        // Starts loading in init
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.isLoadingWeeks.contains(0), "Initially loading week 0")
        
        // Simulating user navigating away (calling back button / close)
        viewModel.cleanup()
        
        // Even with time advancing, the state shouldn't change to "Services not ready" 
        // because the coroutine should be cancelled
        testScheduler.advanceUntilIdle()
        
        // If it was NOT cancelled, it would eventually fail after 5s and set error
        // But since it's cancelled, the error should remain null or it should not update UI
        assertTrue(scope.coroutineContext.job.isCancelled, "Scope should be cancelled")
    }

    @Test
    fun testGradesViewModelCleanup() {
        val job = Job()
        val scope = CoroutineScope(job)
        val viewModel = GradesViewModel(
            gradeService = null,
            gradeDao = null,
            coroutineScope = scope
        )
        
        viewModel.cleanup()
        assertTrue(job.isCancelled, "GradesViewModel coroutine scope should be cancelled after cleanup")
    }

    @Test
    fun testDocumentsViewModelCleanup() {
        val job = Job()
        val scope = CoroutineScope(job)
        val viewModel = DocumentsViewModel(
            dualisDocumentService = null,
            coroutineScope = scope
        )
        
        viewModel.cleanup()
        assertTrue(job.isCancelled, "DocumentsViewModel coroutine scope should be cancelled after cleanup")
    }
}
