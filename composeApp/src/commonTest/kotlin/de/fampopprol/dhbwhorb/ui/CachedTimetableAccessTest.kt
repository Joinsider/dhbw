package de.fampopprol.dhbwhorb.ui

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisPageGateway
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.repository.TimetableRepositoryImpl
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.testutil.MockAppDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CachedTimetableAccessTest {

    /**
     * The widget refreshes from a background worker, where there may be no session and no
     * network. Reading the cache must therefore never reach for Dualis — the arrangement this
     * replaced had the widget's repository interface implemented by the network-backed lecture
     * service, one binding away from a background fetch on every widget tick.
     */
    @Test
    fun cachedLectures_neverReachForTheNetwork() = runTest {
        var fetchCount = 0

        val httpClient = HttpClient { }
        val sessionManager = SessionManager(FakeSecureStorage())
        val apiClient = DualisApiClient(httpClient)
        val authService = AuthenticationService(sessionManager, httpClient)
        val reAuthenticator = ReAuthenticator(sessionManager, authService)
        val mockDb = MockAppDatabase()

        class CountingLectureService : DualisLectureService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            gateway = DualisPageGateway(apiClient, sessionManager, reAuthenticator),
            lectureEventDao = mockDb.lectureDao(),
            lecturerDao = mockDb.lecturerDao(),
            lectureLecturerCrossRefDao = mockDb.lectureLecturerCrossRefDao()
        ) {
            override suspend fun getWeeklyLecturesForWeek(
                start: LocalDateTime,
                end: LocalDateTime
            ): Outcome<List<LectureEventEntity>> {
                fetchCount++
                return Outcome.Ok(emptyList())
            }

            override suspend fun getWeeklySkeletonForWeek(
                start: LocalDateTime,
                end: LocalDateTime
            ): Outcome<List<LectureEventEntity>> {
                fetchCount++
                return Outcome.Ok(emptyList())
            }
        }

        val repository = TimetableRepositoryImpl(
            lectureService = CountingLectureService(),
            lectureEventDao = mockDb.lectureDao(),
            syncMetadataDao = mockDb.syncMetadataDao(),
            scope = backgroundScope
        )

        val monday = LocalDate(2026, 3, 2).let { LocalDateTime(it.year, it.month, it.day, 0, 0) }
        val sunday = LocalDate(2026, 3, 8).let { LocalDateTime(it.year, it.month, it.day, 23, 59) }

        val result = repository.getCachedLectures(monday, sunday)

        assertTrue(result is Outcome.Ok, "Reading the cache must succeed even with nothing in it")
        assertEquals(0, fetchCount, "Reading the cache must not trigger a Dualis request")
    }
}
