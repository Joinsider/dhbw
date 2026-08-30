/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DualisFixtures
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LecturerDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * In-memory fakes for the three timetable DAOs, so writes can actually be observed and (when
 * [failOn] names them) made to throw — the shared `MockAppDatabase` doubles are stateless stubs,
 * which cannot show the "skip if already stored" or "roll back on failure" branches.
 */
private class InMemoryLectureEventDao(private val failOn: Set<String> = emptySet()) : LectureEventDao {
    val stored = mutableMapOf<Long, LectureEventEntity>()
    private var nextId = 1L

    override suspend fun insert(lecture: LectureEventEntity): Long {
        if ("insert" in failOn) throw IOException("insert failed")
        val id = if (lecture.lectureId != 0L) lecture.lectureId else nextId++
        stored[id] = lecture.copy(lectureId = id)
        return id
    }

    override suspend fun insertAll(lectures: List<LectureEventEntity>) {
        lectures.forEach { insert(it) }
    }

    override suspend fun update(lecture: LectureEventEntity) {
        stored[lecture.lectureId] = lecture
    }

    override suspend fun delete(lecture: LectureEventEntity) {
        stored.remove(lecture.lectureId)
    }

    override suspend fun getById(id: Long): LectureEventEntity? = stored[id]
    override fun getAllFlow(): Flow<List<LectureEventEntity>> = flowOf(stored.values.toList())
    override suspend fun getAll(): List<LectureEventEntity> = stored.values.toList()
    override suspend fun getByIdWithLecturers(id: Long): LectureWithLecturers? = null
    override suspend fun getAllWithLecturers(): List<LectureWithLecturers> = emptyList()
    override fun getAllWithLecturersFlow(): Flow<List<LectureWithLecturers>> = flowOf(emptyList())
    override suspend fun deleteById(id: Long) {
        stored.remove(id)
    }

    override suspend fun deleteAll() {
        stored.clear()
    }

    var deleteInRangeCallCount = 0
    override suspend fun deleteInRange(start: LocalDateTime, end: LocalDateTime) {
        deleteInRangeCallCount++
    }

    var deleteEndedBeforeCallCount = 0
    override suspend fun deleteEndedBefore(cutoff: LocalDateTime) {
        deleteEndedBeforeCallCount++
        if ("prune" in failOn) throw IOException("prune failed")
    }
}

private class InMemoryLecturerDao : LecturerDao {
    val stored = mutableMapOf<Long, LecturerEntity>()
    private var nextId = 1L

    override suspend fun insert(lecturer: LecturerEntity): Long {
        val id = if (lecturer.lecturerId != 0L) lecturer.lecturerId else nextId++
        stored[id] = lecturer.copy(lecturerId = id)
        return id
    }

    override suspend fun insertAll(lecturers: List<LecturerEntity>) {
        lecturers.forEach { insert(it) }
    }

    override suspend fun update(lecturer: LecturerEntity) {
        stored[lecturer.lecturerId] = lecturer
    }

    override suspend fun delete(lecturer: LecturerEntity) {
        stored.remove(lecturer.lecturerId)
    }

    override suspend fun getById(id: Long): LecturerEntity? = stored[id]
    override fun getAllFlow(): Flow<List<LecturerEntity>> = flowOf(stored.values.toList())
    override suspend fun getAll(): List<LecturerEntity> = stored.values.toList()
    override suspend fun searchByName(searchQuery: String): List<LecturerEntity> =
        stored.values.filter { it.lecturerName.contains(searchQuery) }

    override suspend fun deleteById(id: Long) {
        stored.remove(id)
    }

    override suspend fun deleteAll() {
        stored.clear()
    }
}

private class InMemoryLectureLecturerCrossRefDao : LectureLecturerCrossRefDao {
    val stored = mutableListOf<LectureLecturerCrossRef>()

    override suspend fun insert(crossRef: LectureLecturerCrossRef) {
        stored.add(crossRef)
    }

    override suspend fun insertAll(crossRefs: List<LectureLecturerCrossRef>) {
        stored.addAll(crossRefs)
    }

    override suspend fun delete(crossRef: LectureLecturerCrossRef) {
        stored.remove(crossRef)
    }

    override suspend fun deleteByLectureId(lectureId: Long) {
        stored.removeAll { it.lectureId == lectureId }
    }

    override suspend fun deleteByLecturerId(lecturerId: Long) {
        stored.removeAll { it.lecturerId == lecturerId }
    }

    override suspend fun deleteAll() {
        stored.clear()
    }

    override suspend fun getByLectureId(lectureId: Long): List<LectureLecturerCrossRef> =
        stored.filter { it.lectureId == lectureId }

    override suspend fun getByLecturerId(lecturerId: Long): List<LectureLecturerCrossRef> =
        stored.filter { it.lecturerId == lecturerId }
}

class DualisLectureServiceTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var sessionManager: SessionManager
    private lateinit var lectureDao: InMemoryLectureEventDao
    private lateinit var lecturerDao: InMemoryLecturerDao
    private lateinit var crossRefDao: InMemoryLectureLecturerCrossRefDao

    @BeforeTest
    fun setup() {
        try {
            Napier.base(DebugAntilog())
        } catch (e: Exception) {
            // Already initialized
        }
        fakeSecureStorage = FakeSecureStorage()
        sessionManager = SessionManager(fakeSecureStorage)
        lectureDao = InMemoryLectureEventDao()
        lecturerDao = InMemoryLecturerDao()
        crossRefDao = InMemoryLectureLecturerCrossRefDao()
    }

    @AfterTest
    fun teardown() {
        Napier.takeLogarithm()
    }

    private fun serviceWithMockEngine(mockEngine: MockEngine): Pair<DualisLectureService, HttpClient> {
        val client = HttpClient(mockEngine) {
            expectSuccess = false
            install(HttpCookies)
        }
        val apiClient = DualisApiClient(client)
        val authService = AuthenticationService(sessionManager, client)
        val reAuthenticator = ReAuthenticator(sessionManager, authService)
        val service = DualisLectureService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            gateway = DualisPageGateway(apiClient, sessionManager, reAuthenticator),
            lectureEventDao = lectureDao,
            lecturerDao = lecturerDao,
            lectureLecturerCrossRefDao = crossRefDao
        )
        return service to client
    }

    // ── demo mode ────────────────────────────────────────────────────────────

    @Test
    fun getWeeklyLecturesForDate_inDemoMode_seedsTheDatabaseOnce() = runTest {
        sessionManager.setDemoMode(true)
        val (service, client) = serviceWithMockEngine(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })

        val result = service.getWeeklyLecturesForDate(LocalDate(2026, 5, 4))

        val lectures = assertIs<Outcome.Ok<List<LectureEventEntity>>>(result).value
        assertEquals(16, lectures.size)
        assertEquals(16, lectureDao.stored.size, "demo lectures must be persisted")
        assertEquals(10, lecturerDao.stored.size, "demo lecturers must be persisted")
        assertTrue(crossRefDao.stored.isNotEmpty())
        client.close()
    }

    @Test
    fun getWeeklyLecturesForDate_inDemoMode_seedingIsIdempotent() = runTest {
        sessionManager.setDemoMode(true)
        val (service, client) = serviceWithMockEngine(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })

        service.getWeeklyLecturesForDate(LocalDate(2026, 5, 4))
        service.getWeeklyLecturesForDate(LocalDate(2026, 5, 4))

        // The "already exists" branches must skip re-inserting, not duplicate.
        assertEquals(16, lectureDao.stored.size)
        assertEquals(10, lecturerDao.stored.size)
        client.close()
    }

    @Test
    fun getWeeklySkeletonForWeek_inDemoMode_doesNotPersistAnything() = runTest {
        sessionManager.setDemoMode(true)
        val (service, client) = serviceWithMockEngine(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val monday = LocalDateTime(2026, 5, 4, 0, 0)

        val result = service.getWeeklySkeletonForWeek(monday, monday)

        val lectures = assertIs<Outcome.Ok<List<LectureEventEntity>>>(result).value
        assertEquals(16, lectures.size)
        assertTrue(lectureDao.stored.isEmpty(), "the skeleton path must not persist")
        assertTrue(lecturerDao.stored.isEmpty())
        client.close()
    }

    @Test
    fun getWeeklyLecturesForDate_inDemoMode_seedFailureIsSwallowed() = runTest {
        sessionManager.setDemoMode(true)
        val throwingLectureDao = InMemoryLectureEventDao(failOn = setOf("insert"))
        val client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }) {
            expectSuccess = false
            install(HttpCookies)
        }
        val apiClient = DualisApiClient(client)
        val service = DualisLectureService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            gateway = DualisPageGateway(apiClient, sessionManager, ReAuthenticator(sessionManager, AuthenticationService(sessionManager, client))),
            lectureEventDao = throwingLectureDao,
            lecturerDao = lecturerDao,
            lectureLecturerCrossRefDao = crossRefDao
        )

        // Seeding fails internally but the demo data is still returned to the caller.
        val result = service.getWeeklyLecturesForDate(LocalDate(2026, 5, 4))

        val lectures = assertIs<Outcome.Ok<List<LectureEventEntity>>>(result).value
        assertEquals(16, lectures.size)
        client.close()
    }

    // ── real network path ───────────────────────────────────────────────────

    @Test
    fun getWeeklyLecturesForDate_enrichesEveryLectureFromItsDetailPage() = runTest {
        sessionManager.storeAuthData(AuthData(sessionId = "session-1", authToken = "token-1"))
        sessionManager.storeCredentials("user@dhbw.de", "password")

        val mockEngine = MockEngine { request ->
            val arguments = request.url.parameters["ARGUMENTS"]
            when {
                request.url.parameters["PRGNAME"] == "SCHEDULER" -> respond(
                    content = ByteReadChannel(DualisFixtures.Timetable.WEEK_FULL),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )

                arguments == "-N1,-N2,-N3" -> respond(
                    content = ByteReadChannel(DualisFixtures.Timetable.INDIVIDUAL_PAGE),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )

                arguments == "-N4,-N5,-N6" -> respond(
                    content = ByteReadChannel("<html><body class=\"access_denied\"><h1>Zugang verweigert</h1></body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )

                arguments == "-N7,-N8,-N9" -> throw IOException("network dropped mid-fetch")

                else -> respond(ByteReadChannel("unexpected"), HttpStatusCode.NotFound)
            }
        }

        val (service, client) = serviceWithMockEngine(mockEngine)

        val result = service.getWeeklyLecturesForDate(LocalDate(2025, 11, 3))

        val lectures = assertIs<Outcome.Ok<List<LectureEventEntity>>>(result).value
        assertEquals(3, lectures.size)

        // First lecture: its detail page was fetched successfully and enriched the entity
        // (the HOR-* course code suffix is stripped by parseIndividualPage, unlike the grid title).
        assertEquals("Form. Sp+Autom.1+2 Gr. B", lectures[0].fullSubjectName)
        // The parser does not decode HTML entities, so "&uuml;" survives as-is.
        assertEquals(listOf("B.Sc. Julian Schmidt", "Prof. Dr. Anna M&uuml;ller"), lectures[0].lecturers)
        assertEquals("HOR-231, HOR-232", lectures[0].location)

        // Second lecture: an error page for its detail link, so the grid values are kept.
        assertEquals("Form. Sp+Autom.1+2 Gr. B  HOR-TINF2024", lectures[1].fullSubjectName)

        // Third lecture: the detail request threw, so the grid values are kept too.
        assertEquals("Klausur Mathematik II  HOR-TINF2024", lectures[2].fullSubjectName)

        client.close()
    }

    @Test
    fun getWeeklyLecturesForDate_whenNotAuthenticated_propagatesTheError() = runTest {
        // No auth data and no stored credentials: the gateway cannot re-authenticate and gives up.
        val (service, client) = serviceWithMockEngine(
            MockEngine { respond(ByteReadChannel("<html></html>"), HttpStatusCode.OK) }
        )

        val result = service.getWeeklyLecturesForDate(LocalDate(2026, 5, 4))

        assertIs<Outcome.Err>(result)
        client.close()
    }

    @Test
    fun getWeeklySkeletonForDate_whenNotAuthenticated_propagatesTheError() = runTest {
        val (service, client) = serviceWithMockEngine(
            MockEngine { respond(ByteReadChannel("<html></html>"), HttpStatusCode.OK) }
        )
        val monday = LocalDateTime(2026, 5, 4, 0, 0)

        val result = service.getWeeklySkeletonForWeek(monday, monday)

        assertIs<Outcome.Err>(result)
        client.close()
    }

    @Test
    fun getWeeklyLecturesForWeek_delegatesToTheStartDate() = runTest {
        sessionManager.setDemoMode(true)
        val (service, client) = serviceWithMockEngine(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val weekStart = LocalDateTime(2026, 5, 4, 0, 0)
        val weekEnd = LocalDateTime(2026, 5, 8, 23, 59)

        val result = service.getWeeklyLecturesForWeek(weekStart, weekEnd)

        val lectures = assertIs<Outcome.Ok<List<LectureEventEntity>>>(result).value
        assertEquals(16, lectures.size)
        client.close()
    }

    @Test
    fun getWeeklySkeletonForDate_doesNotFetchDetailPages() = runTest {
        sessionManager.storeAuthData(AuthData(sessionId = "session-1", authToken = "token-1"))
        sessionManager.storeCredentials("user@dhbw.de", "password")

        var detailRequestCount = 0
        val mockEngine = MockEngine { request ->
            if (request.url.parameters["PRGNAME"] == "SCHEDULER") {
                respond(
                    content = ByteReadChannel(DualisFixtures.Timetable.WEEK_FULL),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            } else {
                detailRequestCount++
                respond(ByteReadChannel(DualisFixtures.Timetable.INDIVIDUAL_PAGE), HttpStatusCode.OK)
            }
        }

        val (service, client) = serviceWithMockEngine(mockEngine)
        val monday = LocalDateTime(2025, 11, 3, 0, 0)

        val result = service.getWeeklySkeletonForWeek(monday, monday)

        val lectures = assertIs<Outcome.Ok<List<LectureEventEntity>>>(result).value
        assertEquals(3, lectures.size)
        assertEquals(0, detailRequestCount, "the skeleton must not fetch individual lecture pages")
        // Skeleton entities carry the grid's own location (already split by the parser), not the
        // detail page's - proving no detail request contributed to it.
        assertEquals("HOR-231, HOR-232", lectures[1].location)

        client.close()
    }

    // ── saveLecturesToDatabase ───────────────────────────────────────────────

    @Test
    fun saveLecturesToDatabase_persistsLecturesAndReusesAnExistingLecturer() = runTest {
        lecturerDao.stored[99L] = LecturerEntity(lecturerId = 99L, lecturerName = "Prof. Dr. Schmidt")
        val (service, client) = serviceWithMockEngine(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })

        val weekStart = LocalDateTime(2026, 5, 4, 0, 0)
        val weekEnd = LocalDateTime(2026, 5, 8, 23, 59)
        val lecture = LectureEventEntity(
            lectureId = 0,
            shortSubjectName = "PROG1",
            fullSubjectName = "Programmierung 1",
            startTime = LocalDateTime(2026, 5, 4, 8, 0),
            endTime = LocalDateTime(2026, 5, 4, 9, 30),
            location = "A101"
        ).apply { lecturers = listOf("Prof. Dr. Schmidt", "", "Dr. New Person") }

        val result = service.saveLecturesToDatabase(listOf(lecture), weekStart, weekEnd)

        val saved = assertIs<Outcome.Ok<List<LectureEventEntity>>>(result).value
        assertEquals(1, saved.size)
        assertTrue(saved.first().lectureId != 0L, "the saved lecture must carry the assigned id")
        assertEquals(1, lectureDao.deleteInRangeCallCount)
        assertEquals(1, lectureDao.deleteEndedBeforeCallCount, "pruning runs on every save")
        // One cross-ref for the existing lecturer, one for the newly created one - the blank name skipped.
        assertEquals(2, crossRefDao.stored.size)
        assertTrue(crossRefDao.stored.any { it.lecturerId == 99L })
        assertEquals(2, lecturerDao.stored.size, "one pre-existing lecturer plus one newly created")

        client.close()
    }

    @Test
    fun saveLecturesToDatabase_pruningFailureDoesNotFailTheSave() = runTest {
        val throwingLectureDao = InMemoryLectureEventDao(failOn = setOf("prune"))
        val client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }) {
            expectSuccess = false
            install(HttpCookies)
        }
        val apiClient = DualisApiClient(client)
        val service = DualisLectureService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            gateway = DualisPageGateway(apiClient, sessionManager, ReAuthenticator(sessionManager, AuthenticationService(sessionManager, client))),
            lectureEventDao = throwingLectureDao,
            lecturerDao = lecturerDao,
            lectureLecturerCrossRefDao = crossRefDao
        )
        val weekStart = LocalDateTime(2026, 5, 4, 0, 0)
        val weekEnd = LocalDateTime(2026, 5, 8, 23, 59)

        val result = service.saveLecturesToDatabase(emptyList(), weekStart, weekEnd)

        assertIs<Outcome.Ok<List<LectureEventEntity>>>(result)
        assertEquals(1, throwingLectureDao.deleteEndedBeforeCallCount)
        client.close()
    }

    @Test
    fun saveLecturesToDatabase_whenInsertThrows_reportsAStorageError() = runTest {
        val throwingLectureDao = InMemoryLectureEventDao(failOn = setOf("insert"))
        val client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }) {
            expectSuccess = false
            install(HttpCookies)
        }
        val apiClient = DualisApiClient(client)
        val service = DualisLectureService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            gateway = DualisPageGateway(apiClient, sessionManager, ReAuthenticator(sessionManager, AuthenticationService(sessionManager, client))),
            lectureEventDao = throwingLectureDao,
            lecturerDao = lecturerDao,
            lectureLecturerCrossRefDao = crossRefDao
        )
        val weekStart = LocalDateTime(2026, 5, 4, 0, 0)
        val weekEnd = LocalDateTime(2026, 5, 8, 23, 59)
        val lecture = LectureEventEntity(
            lectureId = 0,
            shortSubjectName = "PROG1",
            fullSubjectName = "Programmierung 1",
            startTime = LocalDateTime(2026, 5, 4, 8, 0),
            endTime = LocalDateTime(2026, 5, 4, 9, 30),
            location = "A101"
        )

        val result = service.saveLecturesToDatabase(listOf(lecture), weekStart, weekEnd)

        val error = assertIs<AppError.Storage>(assertIs<Outcome.Err>(result).error)
        assertTrue(error.hint.contains("saving the timetable"))
        client.close()
    }
}
