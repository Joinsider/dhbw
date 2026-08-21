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
import de.fampopprol.dhbwhorb.testutil.testKoin
import kotlin.test.Test
import kotlin.test.assertTrue

class ViewModelCleanupTest {

    private val koin = testKoin()

    @Test
    fun testTimetableViewModelCleanup() {
        val job = Job()
        val scope = CoroutineScope(job)
        val viewModel = TimetableViewModel(
            lectureService = koin.get(),
            lecturerDao = koin.get(),
            lectureLecturerCrossRefDao = koin.get(),
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
            lectureService = koin.get(),
            lecturerDao = koin.get(),
            lectureLecturerCrossRefDao = koin.get(),
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
            lectureService = koin.get(),
            lecturerDao = koin.get(),
            lectureLecturerCrossRefDao = koin.get(),
            coroutineScope = scope
        )
        
        // Navigating away before the load in init has run to completion.
        viewModel.cleanup()

        testScheduler.advanceUntilIdle()

        // The scope is cancelled, so the in-flight load cannot write to the state afterwards.
        assertTrue(scope.coroutineContext.job.isCancelled, "Scope should be cancelled")
        assertTrue(
            viewModel.uiState.isLoadingWeeks.isEmpty(),
            "A cancelled load must not leave the week marked as loading"
        )
    }

    @Test
    fun testGradesViewModelCleanup() {
        val job = Job()
        val scope = CoroutineScope(job)
        val viewModel = GradesViewModel(
            gradeService = koin.get(),
            gradeDao = koin.get(),
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
            dualisDocumentService = koin.get(),
            coroutineScope = scope
        )
        
        viewModel.cleanup()
        assertTrue(job.isCancelled, "DocumentsViewModel coroutine scope should be cancelled after cleanup")
    }
}
