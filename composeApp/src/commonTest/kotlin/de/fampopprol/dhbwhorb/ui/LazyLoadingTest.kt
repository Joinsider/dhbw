package de.fampopprol.dhbwhorb.ui

import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.services.LectureService
import de.fampopprol.dhbwhorb.ui.documents.viewModels.DocumentsViewModel
import de.fampopprol.dhbwhorb.testutil.MockAppDatabase
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import kotlinx.coroutines.test.*
import io.ktor.client.HttpClient
import de.fampopprol.dhbwhorb.testutil.testKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LazyLoadingTest {

    private val koin = testKoin()

    @Test
    fun testLectureServiceLazyLoadsDualisService() = runTest {
        var factoryCallCount = 0
        
        val httpClient = HttpClient { }
        val secureStorage = FakeSecureStorage()
        val sessionManager = SessionManager(secureStorage)
        val apiClient = DualisApiClient(httpClient)
        val authService = AuthenticationService(sessionManager, httpClient)
        val mockDb = MockAppDatabase()
        
        // Use a proper mock subclass since DualisLectureService is now open
        class MockDualisService : DualisLectureService(
            apiClient, 
            sessionManager, 
            authService, 
            mockDb.lectureDao(), 
            mockDb.lecturerDao(), 
            mockDb.lectureLecturerCrossRefDao()
        ) {
            override suspend fun getWeeklySkeletonForWeek(start: kotlinx.datetime.LocalDateTime, end: kotlinx.datetime.LocalDateTime) = Result.success(emptyList<de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity>())
        }

        val lectureService = LectureService(
            database = mockDb,
            dualisLectureServiceFactory = {
                factoryCallCount++
                MockDualisService()
            }
        )
        
        assertEquals(0, factoryCallCount, "Factory should not be called upon LectureService creation")
        
        // Accessing the lazy property should trigger the factory
        lectureService.getLecturesForWeekStaged(0)
        
        assertEquals(1, factoryCallCount, "Factory should be called exactly once when service is first needed")
    }

    @Test
    fun documentsViewModel_withoutSession_endsInLoginRequired() = runTest {
        // backgroundScope, not `this`: the ViewModel's stateIn job never completes, so a test-scope
        // child would keep runTest waiting forever.
        val viewModel = DocumentsViewModel(
            dualisDocumentService = koin.get(),
            coroutineScope = backgroundScope
        )

        // uiState is a stateIn(WhileSubscribed) flow: without an active collector its value stays
        // at the initial default, no matter what the ViewModel does. So subscribe first.
        backgroundScope.launch { viewModel.uiState.collect { } }
        runCurrent()

        // The service is always present now, so init { loadDocuments() } runs straight through
        // instead of polling for it. Without a stored session it stops at "login required".
        testScheduler.advanceUntilIdle()
        testScheduler.runCurrent()

        assertFalse(viewModel.uiState.value.isLoading, "Loading has to finish")
        assertTrue(
            viewModel.uiState.value.requiresLogin,
            "Without a session the user must be asked to log in, not shown an error"
        )
        assertNull(viewModel.uiState.value.error, "A missing session is not an error condition")
    }
}
