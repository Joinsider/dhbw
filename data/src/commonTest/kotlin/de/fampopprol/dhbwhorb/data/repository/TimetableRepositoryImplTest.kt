/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisPageGateway
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.database.dao.SyncMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.SyncMetadataEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.testutil.MockAppDatabase
import de.fampopprol.dhbwhorb.testutil.MockLectureEventDao
import io.ktor.client.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableRepositoryImplTest {

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────

    private val httpClient = HttpClient { }
    private val sessionManager = SessionManager(FakeSecureStorage())
    private val apiClient = DualisApiClient(httpClient)
    private val authService = AuthenticationService(sessionManager, httpClient)
    private val database = MockAppDatabase()

    private fun lecture(id: Long, subject: String = "Mathematik 1") = LectureEventEntity(
        lectureId = id,
        shortSubjectName = subject,
        fullSubjectName = subject,
        startTime = LocalDateTime(2026, 3, 2, 8, 0),
        endTime = LocalDateTime(2026, 3, 2, 9, 30),
        location = "HOR-100",
    )

    /** Overrides only the three network-facing calls the repository drives. */
    private class FakeLectureService(
        private val skeleton: Outcome<List<LectureEventEntity>> = Outcome.Ok(emptyList()),
        private val full: Outcome<List<LectureEventEntity>> = Outcome.Ok(emptyList()),
        private val saveResult: (List<LectureEventEntity>) -> Outcome<List<LectureEventEntity>> = { Outcome.Ok(it) },
        private val beforeFull: suspend () -> Unit = {},
        apiClient: DualisApiClient,
        sessionManager: SessionManager,
        authService: AuthenticationService,
        database: MockAppDatabase,
    ) : DualisLectureService(
        apiClient = apiClient,
        sessionManager = sessionManager,
        gateway = DualisPageGateway(apiClient, sessionManager, ReAuthenticator(sessionManager, authService)),
        lectureEventDao = database.lectureDao(),
        lecturerDao = database.lecturerDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao(),
    ) {
        var savedLectures: List<LectureEventEntity>? = null
        var fullFetches = 0

        override suspend fun getWeeklySkeletonForWeek(
            start: LocalDateTime,
            end: LocalDateTime
        ): Outcome<List<LectureEventEntity>> = skeleton

        override suspend fun getWeeklyLecturesForWeek(
            start: LocalDateTime,
            end: LocalDateTime
        ): Outcome<List<LectureEventEntity>> {
            beforeFull()
            fullFetches++
            return full
        }

        override suspend fun saveLecturesToDatabase(
            lectures: List<LectureEventEntity>,
            weekStart: LocalDateTime,
            weekEnd: LocalDateTime
        ): Outcome<List<LectureEventEntity>> {
            savedLectures = lectures
            return saveResult(lectures)
        }
    }

    /** Returns [cached] from every read regardless of the requested range. */
    private class FixedCacheDao(private val cached: List<LectureWithLecturers>) : MockLectureEventDao() {
        override suspend fun getAllWithLecturers(): List<LectureWithLecturers> = cached
    }

    private class ThrowingCacheDao : MockLectureEventDao() {
        override suspend fun getAllWithLecturers(): List<LectureWithLecturers> =
            throw IllegalStateException("database is closed")
    }

    private class ThrowingSyncMetadataDao(
        private val throwOnRead: Boolean = false,
        private val throwOnInsert: Boolean = false,
    ) : SyncMetadataDao {
        override suspend fun insert(syncMetadataEntity: SyncMetadataEntity) {
            if (throwOnInsert) throw IllegalStateException("database is closed")
        }
        override suspend fun insertAll(syncMetadataEntities: List<SyncMetadataEntity>) = Unit
        override suspend fun update(syncMetadataEntity: SyncMetadataEntity) = Unit
        override suspend fun delete(syncMetadataEntity: SyncMetadataEntity) = Unit
        override suspend fun getSyncMetadata(key: String): SyncMetadataEntity? {
            if (throwOnRead) throw IllegalStateException("database is closed")
            return null
        }
        override suspend fun clearAllSyncMetadata() = Unit
        override suspend fun getAllSyncMetadata(): List<SyncMetadataEntity> = emptyList()
        override suspend fun deleteByKey(key: String) = Unit
    }

    private class FakeSyncMetadataDao(private var metadata: SyncMetadataEntity? = null) : SyncMetadataDao {
        var inserted: SyncMetadataEntity? = null
        override suspend fun insert(syncMetadataEntity: SyncMetadataEntity) {
            inserted = syncMetadataEntity
            metadata = syncMetadataEntity
        }
        override suspend fun insertAll(syncMetadataEntities: List<SyncMetadataEntity>) = Unit
        override suspend fun update(syncMetadataEntity: SyncMetadataEntity) = Unit
        override suspend fun delete(syncMetadataEntity: SyncMetadataEntity) = Unit
        override suspend fun getSyncMetadata(key: String): SyncMetadataEntity? = metadata
        override suspend fun clearAllSyncMetadata() { metadata = null }
        override suspend fun getAllSyncMetadata(): List<SyncMetadataEntity> = listOfNotNull(metadata)
        override suspend fun deleteByKey(key: String) { metadata = null }
    }

    private fun TestScope.repository(
        service: DualisLectureService = FakeLectureService(
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        ),
        lectureEventDao: MockLectureEventDao = MockLectureEventDao(),
        syncMetadataDao: SyncMetadataDao = FakeSyncMetadataDao(),
    ) = TimetableRepositoryImpl(
        lectureService = service,
        lectureEventDao = lectureEventDao,
        syncMetadataDao = syncMetadataDao,
        scope = this as CoroutineScope,
    )

    // ── getWeek: empty cache ────────────────────────────────────────────────────────────────

    @Test
    fun getWeek_emptyCache_returnsAPartialWeekFromTheSkeleton() = runTest {
        val service = FakeLectureService(
            skeleton = Outcome.Ok(listOf(lecture(1))),
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )

        val result = repository(service = service).getWeek(weekOffset = 0)

        val week = assertIs<Outcome.Ok<TimetableWeek>>(result).value
        assertTrue(week.isPartial)
        assertEquals(false, week.fromCache)
        assertEquals(1, week.lectures.size)
        assertEquals("Mathematik 1", week.lectures.first().shortName)
    }

    @Test
    fun getWeek_emptyCache_propagatesASkeletonFailure() = runTest {
        val error = AppError.Offline
        val service = FakeLectureService(
            skeleton = Outcome.Err(error),
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )

        val result = repository(service = service).getWeek(weekOffset = 0)

        assertEquals(Outcome.Err(error), result)
    }

    @Test
    fun getWeek_emptyCache_startsABackgroundFullFetchEvenOnSkeletonSuccess() = runTest {
        val service = FakeLectureService(
            skeleton = Outcome.Ok(emptyList()),
            full = Outcome.Ok(listOf(lecture(2))),
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )

        repository(service = service).awaitFullWeek(weekOffset = 0)

        assertEquals(1, service.fullFetches)
        assertEquals(listOf(lecture(2)), service.savedLectures)
    }

    // ── getWeek: cache hit ──────────────────────────────────────────────────────────────────

    /** A lecture that falls inside the *actual* current week — [TimetableRepositoryImpl] filters
     * the cache read to the requested range, so a fixture from an arbitrary fixed date would be
     * filtered back out regardless of what the fake DAO returns. */
    private fun currentWeekLecture(id: Long) = TimeHelper.getWeekDatesRelativeToCurrentWeek(0).let { (start, _) ->
        lecture(id).copy(startTime = start, endTime = start)
    }

    @Test
    fun getWeek_cacheHit_andNotStale_returnsCachedWithoutRefreshing() = runTest {
        val cachedDao = FixedCacheDao(listOf(cachedLecture(currentWeekLecture(3))))
        val service = FakeLectureService(
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )
        // "Not stale": no sync metadata recorded at all reads as never-stale in isStale().
        val result = repository(
            service = service,
            lectureEventDao = cachedDao,
            syncMetadataDao = FakeSyncMetadataDao(metadata = null),
        ).getWeek(weekOffset = 0)

        val week = assertIs<Outcome.Ok<TimetableWeek>>(result).value
        assertTrue(week.fromCache)
        assertEquals(false, week.isPartial)
        assertEquals(1, week.lectures.size)
        assertEquals(0, service.fullFetches, "a fresh cache must not trigger a background refresh")
    }

    @Test
    fun getWeek_cacheHit_butStale_stillReturnsCachedAndRefreshesInTheBackground() = runTest {
        val cachedDao = FixedCacheDao(listOf(cachedLecture(currentWeekLecture(4))))
        val service = FakeLectureService(
            full = Outcome.Ok(listOf(lecture(4))),
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )
        val staleMetadata = SyncMetadataEntity(
            key = "timetable",
            lastSyncTimestamp = LocalDateTime(2000, 1, 1, 0, 0),
        )

        val result = repository(
            service = service,
            lectureEventDao = cachedDao,
            syncMetadataDao = FakeSyncMetadataDao(metadata = staleMetadata),
        ).getWeek(weekOffset = 0)
        // getWeek fires the refresh in the background rather than joining it — give the scope's
        // launched coroutine a chance to run before checking that it did.
        runCurrent()

        val week = assertIs<Outcome.Ok<TimetableWeek>>(result).value
        assertTrue(week.fromCache)
        assertEquals(1, service.fullFetches, "a stale cache must trigger exactly one background refresh")
    }

    // ── readCachedLectures error path ──────────────────────────────────────────────────────

    @Test
    fun getCachedLectures_whenTheDaoThrows_returnsAStorageError() = runTest {
        val result = repository(lectureEventDao = ThrowingCacheDao()).getCachedLectures(
            start = LocalDateTime(2026, 3, 2, 0, 0),
            end = LocalDateTime(2026, 3, 8, 0, 0),
        )

        val error = assertIs<Outcome.Err>(result).error
        assertIs<AppError.Storage>(error)
    }

    @Test
    fun getWeek_whenTheCacheReadThrows_propagatesTheStorageError() = runTest {
        val result = repository(lectureEventDao = ThrowingCacheDao()).getWeek(weekOffset = 0)

        val error = assertIs<Outcome.Err>(result).error
        assertIs<AppError.Storage>(error)
    }

    @Test
    fun getWeek_whenReadingTheSyncTimestampThrows_stillReturnsTheCachedWeek() = runTest {
        // Not knowing when the cache last synced is not a reason to refuse serving it.
        val cachedDao = FixedCacheDao(listOf(cachedLecture(currentWeekLecture(9))))

        val result = repository(
            lectureEventDao = cachedDao,
            syncMetadataDao = ThrowingSyncMetadataDao(throwOnRead = true),
        ).getWeek(weekOffset = 0)

        val week = assertIs<Outcome.Ok<TimetableWeek>>(result).value
        assertTrue(week.fromCache)
        assertEquals(1, week.lectures.size)
    }

    // ── refreshWeek: dedup ──────────────────────────────────────────────────────────────────

    @Test
    fun refreshWeek_concurrentCalls_shareTheSameInFlightFetch() = runTest {
        // Held open so the second call is guaranteed to see the first fetch still in flight —
        // sequential (non-overlapping) calls would each start their own, proving nothing.
        val gate = CompletableDeferred<Unit>()
        val service = FakeLectureService(
            full = Outcome.Ok(listOf(lecture(5))),
            beforeFull = { gate.await() },
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )
        val repo = repository(service = service)

        val firstDeferred = async { repo.refreshWeek(weekOffset = 1) }
        runCurrent()
        val secondDeferred = async { repo.refreshWeek(weekOffset = 1) }
        runCurrent()
        gate.complete(Unit)

        assertEquals(firstDeferred.await(), secondDeferred.await())
        assertEquals(1, service.fullFetches, "a second call must join the first fetch, not start its own")
    }

    @Test
    fun refreshWeek_whenSavingFails_propagatesTheError() = runTest {
        val error = AppError.Storage("disk full")
        val service = FakeLectureService(
            full = Outcome.Ok(listOf(lecture(6))),
            saveResult = { Outcome.Err(error) },
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )

        val result = repository(service = service).refreshWeek(weekOffset = 0)

        assertEquals(Outcome.Err(error), result)
    }

    @Test
    fun refreshWeek_whenTheFullFetchFails_propagatesTheError() = runTest {
        val error = AppError.Offline
        val service = FakeLectureService(
            full = Outcome.Err(error),
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )

        val result = repository(service = service).refreshWeek(weekOffset = 0)

        assertEquals(Outcome.Err(error), result)
    }

    @Test
    fun refreshWeek_whenRecordingTheSyncTimestampThrows_stillSucceeds() = runTest {
        // Losing the timestamp only costs an extra refresh next time, not this one's result.
        val service = FakeLectureService(
            full = Outcome.Ok(listOf(lecture(7))),
            apiClient = apiClient, sessionManager = sessionManager, authService = authService, database = database,
        )

        val result = repository(
            service = service,
            syncMetadataDao = ThrowingSyncMetadataDao(throwOnInsert = true),
        ).refreshWeek(weekOffset = 0)

        assertEquals(1, assertIs<Outcome.Ok<TimetableWeek>>(result).value.lectures.size)
    }

    private fun cachedLecture(entity: LectureEventEntity) = LectureWithLecturers(
        lecture = entity,
        lecturers = emptyList(),
    )
}
