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
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.ModuleDetailsFixtures
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeCacheMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeCacheMetadata
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.domain.model.Semester
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
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private class InMemoryGradeDao(private val failOn: Set<String> = emptySet()) : GradeDao {
    val stored = mutableListOf<GradeEntity>()
    var deleteCallCount = 0

    override suspend fun insert(grade: GradeEntity) {
        if ("insert" in failOn) throw IOException("insert failed")
        stored.add(grade)
    }

    override suspend fun insertAll(grades: List<GradeEntity>) {
        if ("insertAll" in failOn) throw IOException("insertAll failed")
        stored.addAll(grades)
    }

    override suspend fun getGradesForSemester(studentId: String, semesterId: String): List<GradeEntity> {
        if ("read" in failOn) throw IOException("read failed")
        return stored.filter { it.studentId == studentId && it.semesterId == semesterId }
    }

    override suspend fun deleteGradesForSemester(studentId: String, semesterId: String) {
        deleteCallCount++
        stored.removeAll { it.studentId == studentId && it.semesterId == semesterId }
    }

    override suspend fun deleteAll() {
        stored.clear()
    }
}

private class InMemoryGradeCacheMetadataDao(private val failOn: Set<String> = emptySet()) : GradeCacheMetadataDao {
    val stored = mutableMapOf<String, GradeCacheMetadata>()

    override suspend fun insert(metadata: GradeCacheMetadata) {
        if ("insert" in failOn) throw IOException("metadata insert failed")
        stored[metadata.key] = metadata
    }

    override suspend fun getMetadata(studentId: String, semesterId: String): GradeCacheMetadata? {
        if ("read" in failOn) throw IOException("metadata read failed")
        return stored.values.find { it.studentId == studentId && it.semesterId == semesterId }
    }

    override suspend fun deleteMetadata(studentId: String, semesterId: String) {
        stored.values.removeAll { it.studentId == studentId && it.semesterId == semesterId }
    }

    override suspend fun deleteAll() {
        stored.clear()
    }
}

class DualisGradeServiceTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var sessionManager: SessionManager
    private lateinit var gradeDao: InMemoryGradeDao
    private lateinit var metadataDao: InMemoryGradeCacheMetadataDao

    @BeforeTest
    fun setup() {
        try {
            Napier.base(DebugAntilog())
        } catch (e: Exception) {
            // Already initialized
        }
        fakeSecureStorage = FakeSecureStorage()
        sessionManager = SessionManager(fakeSecureStorage)
        gradeDao = InMemoryGradeDao()
        metadataDao = InMemoryGradeCacheMetadataDao()
        sessionManager.storeCredentials("student@dhbw.de", "password")
        sessionManager.storeAuthData(AuthData(sessionId = "session-1", authToken = "token-1"))
    }

    @AfterTest
    fun teardown() {
        Napier.takeLogarithm()
    }

    private fun serviceWithMockEngine(
        mockEngine: MockEngine,
        gDao: GradeDao = gradeDao,
        mDao: GradeCacheMetadataDao = metadataDao
    ): Pair<DualisGradeService, HttpClient> {
        val client = HttpClient(mockEngine) {
            expectSuccess = false
            install(HttpCookies)
        }
        val apiClient = DualisApiClient(client)
        val authService = AuthenticationService(sessionManager, client)
        val reAuthenticator = ReAuthenticator(sessionManager, authService)
        val service = DualisGradeService(
            gateway = DualisPageGateway(apiClient, sessionManager, reAuthenticator),
            sessionManager = sessionManager,
            gradeDao = gDao,
            gradeCacheMetadataDao = mDao
        )
        return service to client
    }

    // ── getSemesters ─────────────────────────────────────────────────────────

    @Test
    fun getSemesters_fromNetwork_parsesTheDropdown() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(DualisFixtures.Grades.SEMESTER_DROPDOWN),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }
        val (service, client) = serviceWithMockEngine(mockEngine)

        val result = service.getSemesters()

        val semesters = assertIs<Outcome.Ok<List<Semester>>>(result).value
        assertEquals(3, semesters.size)
        assertEquals("WiSe 2025/26", semesters.first().name)
        client.close()
    }

    @Test
    fun getSemesters_whenTheGatewayFails_propagatesTheError() = runTest {
        val mockEngine = MockEngine { throw IOException("offline") }
        val (service, client) = serviceWithMockEngine(mockEngine)

        val result = service.getSemesters()

        assertEquals(Outcome.Err(AppError.Offline), result)
        client.close()
    }

    // ── getGradesForSemester ─────────────────────────────────────────────────

    private val semester = Semester(id = "000000015168000", name = "WiSe 2025/26")

    // isValidGradePage requires the semester dropdown marker; the raw results table alone
    // (as GradeParserTest exercises it directly) is not "a page", just a table fragment.
    private val validGradesPage = DualisFixtures.Grades.SEMESTER_DROPDOWN + DualisFixtures.Grades.SEMESTER_TABLE

    @Test
    fun getGradesForSemester_withAFreshCache_neverTouchesTheNetwork() = runTest {
        var requestCount = 0
        val mockEngine = MockEngine {
            requestCount++
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        }
        gradeDao.stored.add(
            GradeEntity(
                studentId = "student@dhbw.de",
                semesterId = semester.id,
                semesterName = semester.name,
                moduleNumber = "T3INF1001",
                moduleName = "Mathematik I",
                grade = "1,7",
                credits = 5.0,
                status = "bestanden"
            )
        )
        metadataDao.stored["k"] = GradeCacheMetadata(
            key = "k",
            lastUpdatedTimestamp = nowMillis(),
            studentId = "student@dhbw.de",
            semesterId = semester.id
        )

        val (service, client) = serviceWithMockEngine(mockEngine)
        val result = service.getGradesForSemester(semester)

        val grades = assertIs<Outcome.Ok<List<GradeEntity>>>(result).value
        assertEquals(1, grades.size)
        assertEquals(0, requestCount, "a fresh cache must not hit the network")
        client.close()
    }

    @Test
    fun getGradesForSemester_withAStaleCache_fetchesFromNetwork() = runTest {
        gradeDao.stored.add(
            GradeEntity(
                studentId = "student@dhbw.de",
                semesterId = semester.id,
                semesterName = semester.name,
                moduleNumber = "STALE",
                moduleName = "Stale Module",
                grade = "5,0",
                credits = 1.0,
                status = "bestanden"
            )
        )
        metadataDao.stored["k"] = GradeCacheMetadata(
            key = "k",
            // Far older than the one-hour validity window.
            lastUpdatedTimestamp = nowMillis() - 2 * 60 * 60 * 1000L,
            studentId = "student@dhbw.de",
            semesterId = semester.id
        )
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(validGradesPage),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }

        val (service, client) = serviceWithMockEngine(mockEngine)
        val result = service.getGradesForSemester(semester)

        val grades = assertIs<Outcome.Ok<List<GradeEntity>>>(result).value
        assertEquals(3, grades.size, "a stale cache must be refreshed from the network")
        assertTrue(grades.none { it.moduleNumber == "STALE" })
        client.close()
    }

    @Test
    fun getGradesForSemester_withForceRefresh_skipsAFreshCache() = runTest {
        gradeDao.stored.add(
            GradeEntity(
                studentId = "student@dhbw.de",
                semesterId = semester.id,
                semesterName = semester.name,
                moduleNumber = "CACHED",
                moduleName = "Cached Module",
                grade = "1,0",
                credits = 1.0,
                status = "bestanden"
            )
        )
        metadataDao.stored["k"] = GradeCacheMetadata(
            key = "k",
            lastUpdatedTimestamp = nowMillis(),
            studentId = "student@dhbw.de",
            semesterId = semester.id
        )
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(validGradesPage),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }

        val (service, client) = serviceWithMockEngine(mockEngine)
        val result = service.getGradesForSemester(semester, forceRefresh = true)

        val grades = assertIs<Outcome.Ok<List<GradeEntity>>>(result).value
        assertEquals(3, grades.size)
        assertTrue(grades.none { it.moduleNumber == "CACHED" })
        client.close()
    }

    @Test
    fun getGradesForSemester_whenCacheReadThrows_fallsBackToNetwork() = runTest {
        val throwingMetadataDao = InMemoryGradeCacheMetadataDao(failOn = setOf("read"))
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(validGradesPage),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }

        val (service, client) = serviceWithMockEngine(mockEngine, mDao = throwingMetadataDao)
        val result = service.getGradesForSemester(semester)

        val grades = assertIs<Outcome.Ok<List<GradeEntity>>>(result).value
        assertEquals(3, grades.size, "a broken cache must not block the network fallback")
        client.close()
    }

    @Test
    fun getGradesForSemester_withNoCacheEntry_fetchesAndThenCaches() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(validGradesPage),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }

        val (service, client) = serviceWithMockEngine(mockEngine)
        val result = service.getGradesForSemester(semester)

        val grades = assertIs<Outcome.Ok<List<GradeEntity>>>(result).value
        assertEquals(3, grades.size)
        assertEquals(3, gradeDao.stored.size, "the fetched grades must be written to the cache")
        assertEquals(1, metadataDao.stored.size, "cache metadata must be recorded")
        assertEquals(1, gradeDao.deleteCallCount, "old entries for the semester are cleared first")
        client.close()
    }

    @Test
    fun getGradesForSemester_whenCacheWriteFails_stillReturnsTheFetchedGrades() = runTest {
        val throwingGradeDao = InMemoryGradeDao(failOn = setOf("insertAll"))
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(validGradesPage),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }

        val (service, client) = serviceWithMockEngine(mockEngine, gDao = throwingGradeDao)
        val result = service.getGradesForSemester(semester)

        val grades = assertIs<Outcome.Ok<List<GradeEntity>>>(result).value
        assertEquals(3, grades.size, "a cache-write failure must not fail the request")
        client.close()
    }

    @Test
    fun getGradesForSemester_whenTheNetworkFails_propagatesTheError() = runTest {
        val mockEngine = MockEngine { throw IOException("offline") }
        val (service, client) = serviceWithMockEngine(mockEngine)

        val result = service.getGradesForSemester(semester)

        assertEquals(Outcome.Err(AppError.Offline), result)
        client.close()
    }

    @Test
    fun getGradesForSemester_demoMode_ignoresTheCacheAndTheNetwork() = runTest {
        sessionManager.setDemoMode(true)
        var requestCount = 0
        val mockEngine = MockEngine {
            requestCount++
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        }

        val (service, client) = serviceWithMockEngine(mockEngine)
        val demoSemester = Semester(id = "demo-semester-3", name = "SoSe 2026")
        val result = service.getGradesForSemester(demoSemester)

        val grades = assertIs<Outcome.Ok<List<GradeEntity>>>(result).value
        assertTrue(grades.isNotEmpty())
        assertEquals(0, requestCount)
        client.close()
    }

    // ── getModuleDetails ─────────────────────────────────────────────────────

    @Test
    fun getModuleDetails_fromNetwork_parsesTheAttemptTable() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(ModuleDetailsFixtures.compilerbau),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }
        val (service, client) = serviceWithMockEngine(mockEngine)

        val result = service.getModuleDetails("demo-result-T4INF4211")

        val details = assertIs<Outcome.Ok<de.fampopprol.dhbwhorb.domain.model.ModuleResultDetails>>(result).value
        assertEquals("T4INF4211", details.moduleNumber)
        assertEquals("Compilerbau", details.moduleName)
        client.close()
    }

    @Test
    fun getModuleDetails_whenTheGatewayFails_propagatesTheError() = runTest {
        val mockEngine = MockEngine { throw IOException("offline") }
        val (service, client) = serviceWithMockEngine(mockEngine)

        val result = service.getModuleDetails("demo-result-T4INF4211")

        assertEquals(Outcome.Err(AppError.Offline), result)
        client.close()
    }

    @Test
    fun getModuleDetails_demoMode_forAKnownResult_returnsIt() = runTest {
        sessionManager.setDemoMode(true)
        val (service, client) = serviceWithMockEngine(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })

        val result = service.getModuleDetails("demo-result-T3INF3001")

        assertIs<Outcome.Ok<*>>(result)
        client.close()
    }

    @Test
    fun getModuleDetails_demoMode_forAnUnknownResult_reportsAParseFailure() = runTest {
        sessionManager.setDemoMode(true)
        val (service, client) = serviceWithMockEngine(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })

        val result = service.getModuleDetails("demo-result-DOES-NOT-EXIST")

        val error = assertIs<AppError.Parse>(assertIs<Outcome.Err>(result).error)
        assertTrue(error.hint.contains("no result recorded"))
        client.close()
    }

    @OptIn(ExperimentalTime::class)
    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
