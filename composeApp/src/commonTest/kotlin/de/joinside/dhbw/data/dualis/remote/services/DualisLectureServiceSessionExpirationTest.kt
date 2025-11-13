/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.data.dualis.remote.services

import de.joinside.dhbw.data.dualis.remote.DualisApiClient
import de.joinside.dhbw.data.dualis.remote.models.AuthData
import de.joinside.dhbw.data.dualis.remote.parser.HtmlParser
import de.joinside.dhbw.data.dualis.remote.parser.TimetableParser
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.storage.credentials.FakeSecureStorage
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureEventDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LecturerDao
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureWithLecturers
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
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for session expiration handling in DualisLectureService.
 * These tests verify that the service properly detects expired sessions
 * and re-authenticates automatically.
 */
class DualisLectureServiceSessionExpirationTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var sessionManager: SessionManager
    private lateinit var mockDatabase: MockAppDatabase

    @BeforeTest
    fun setup() {
        // Initialize Napier for logging in tests
        Napier.base(DebugAntilog())

        // Initialize fake storage and session manager
        fakeSecureStorage = FakeSecureStorage()
        sessionManager = SessionManager(fakeSecureStorage)
        
        // Initialize mock database
        mockDatabase = MockAppDatabase()
        
        // Set up initial authentication state
        sessionManager.storeCredentials("test@dhbw.de", "password")
        sessionManager.storeAuthData(AuthData("session123", "token456"))
    }

    @AfterTest
    fun teardown() {
        // Clean up Napier
        Napier.takeLogarithm()
    }

    @Test
    fun getWeeklyLecturesForDate_withExpiredSession_reauthenticatesAndRetries() = runTest {
        // Given
        var callCount = 0
        var loginCallCount = 0
        
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            
            when {
                // Login request
                url.contains("LOGINCHECK") || url.contains("login") -> {
                    loginCallCount++
                    respond(
                        content = ByteReadChannel("""
                            <html>
                            <head>
                                <meta http-equiv="refresh" content="0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=MLSSTART&ARGUMENTS=-Ntest-token">
                            </head>
                            </html>
                        """.trimIndent()),
                        status = HttpStatusCode.OK,
                        headers = headers {
                            append(HttpHeaders.ContentType, "text/html")
                            append("refresh", "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=MLSSTART&ARGUMENTS=-Ntest-token")
                        }
                    )
                }
                // Main page after login
                url.contains("MLSSTART") -> {
                    respond(
                        content = ByteReadChannel("""
                            <html>
                            <head><title>Dualis - Main Page</title></head>
                            <body>
                                <h1>Herzlich willkommen, Test User!</h1>
                            </body>
                            </html>
                        """.trimIndent()),
                        status = HttpStatusCode.OK,
                        headers = headers {
                            append(HttpHeaders.ContentType, "text/html")
                        }
                    )
                }
                // Weekly timetable request
                url.contains("SCHEDULER") -> {
                    callCount++
                    if (callCount == 1) {
                        // First call: return error page (expired session)
                        respond(
                            content = ByteReadChannel("""
                                <html>
                                <head><title>Dualis - Error</title></head>
                                <body class="access_denied">
                                    <h1>Zugang verweigert</h1>
                                    <p>Ihre Sitzung ist abgelaufen.</p>
                                </body>
                                </html>
                            """.trimIndent()),
                            status = HttpStatusCode.OK,
                            headers = headers {
                                append(HttpHeaders.ContentType, "text/html")
                            }
                        )
                    } else {
                        // Second call: return valid timetable
                        respond(
                            content = ByteReadChannel("""
                                <html>
                                <head><title>Dualis - Timetable</title></head>
                                <body>
                                    <h1>Stundenplan</h1>
                                    <!-- Empty timetable for test -->
                                </body>
                                </html>
                            """.trimIndent()),
                            status = HttpStatusCode.OK,
                            headers = headers {
                                append(HttpHeaders.ContentType, "text/html")
                            }
                        )
                    }
                }
                // Default Dualis requests (catch-all for authentication)
                url.contains("dualis.dhbw.de") -> {
                    respond(
                        content = ByteReadChannel("""
                            <html>
                            <head>
                                <meta http-equiv="refresh" content="0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=MLSSTART&ARGUMENTS=-Ntest-token">
                            </head>
                            </html>
                        """.trimIndent()),
                        status = HttpStatusCode.OK,
                        headers = headers {
                            append(HttpHeaders.ContentType, "text/html")
                            append("refresh", "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=MLSSTART&ARGUMENTS=-Ntest-token")
                        }
                    )
                }
                else -> {
                    respond(
                        content = ByteReadChannel("Not found"),
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }

        val httpClient = HttpClient(mockEngine) {
            expectSuccess = false
            install(HttpCookies)
        }

        val apiClient = DualisApiClient(httpClient)
        val authService = AuthenticationService(sessionManager, httpClient)
        val htmlParser = HtmlParser()
        val timetableParser = TimetableParser()

        val service = DualisLectureService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            authenticationService = authService,
            timetableParser = timetableParser,
            htmlParser = htmlParser,
            lectureEventDao = mockDatabase.lectureDao(),
            lecturerDao = mockDatabase.lecturerDao(),
            lectureLecturerCrossRefDao = mockDatabase.lectureLecturerCrossRefDao()
        )

        // When
        val date = LocalDate(2024, 1, 15)
        val result = service.getWeeklyLecturesForDate(date)

        // Then
        assertTrue(result.isSuccess, "Should successfully fetch lectures after re-authentication")
        assertEquals(2, callCount, "Should make two calls: first fails, second succeeds after re-auth")
        assertEquals(1, loginCallCount, "Should perform one re-authentication")

        httpClient.close()
    }
}

/**
 * Mock implementation of AppDatabase for testing.
 */
private class MockAppDatabase {
    private val mockLectureDao = MockLectureEventDao()
    private val mockLecturerDao = MockLecturerDao()
    private val mockCrossRefDao = MockLectureLecturerCrossRefDao()

    fun lectureDao() = mockLectureDao
    fun lecturerDao() = mockLecturerDao
    fun lectureLecturerCrossRefDao() = mockCrossRefDao
}

/**
 * Mock implementation of LectureEventDao for testing.
 */
private class MockLectureEventDao : LectureEventDao {
    private val lectures = mutableListOf<LectureEventEntity>()
    private var nextId = 1L
    
    override suspend fun insert(lecture: LectureEventEntity): Long {
        val id = nextId++
        lectures.add(lecture.copy(lectureId = id))
        return id
    }
    
    override suspend fun insertAll(lectures: List<LectureEventEntity>) {
        lectures.forEach { insert(it) }
    }
    
    override suspend fun update(lecture: LectureEventEntity) {
        val index = lectures.indexOfFirst { it.lectureId == lecture.lectureId }
        if (index != -1) {
            lectures[index] = lecture
        }
    }
    
    override suspend fun getAll(): List<LectureEventEntity> = lectures
    
    override fun getAllFlow() = throw NotImplementedError("Flow not needed for this test")
    
    override suspend fun delete(lecture: LectureEventEntity) {
        lectures.remove(lecture)
    }
    
    override suspend fun deleteById(id: Long) {
        val toRemove = lectures.filter { it.lectureId == id }
        lectures.removeAll(toRemove)
    }
    
    override suspend fun deleteAll() {
        lectures.clear()
    }
    
    override suspend fun getById(id: Long): LectureEventEntity? {
        return lectures.find { it.lectureId == id }
    }

    override suspend fun getByIdWithLecturers(id: Long): LectureWithLecturers? {
        throw NotImplementedError("Not needed for this test")
    }

    override suspend fun getAllWithLecturers(): List<LectureWithLecturers> {
        throw NotImplementedError("Not needed for this test")
    }

    override fun getAllWithLecturersFlow(): Flow<List<LectureWithLecturers>> {
        throw NotImplementedError("Flow not needed for this test")
    }
}

/**
 * Mock implementation of LecturerDao for testing.
 */
private class MockLecturerDao : LecturerDao {
    private val lecturers = mutableListOf<LecturerEntity>()
    private var nextId = 1L
    
    override suspend fun insert(lecturer: LecturerEntity): Long {
        val id = nextId++
        lecturers.add(lecturer.copy(lecturerId = id))
        return id
    }
    
    override suspend fun insertAll(lecturers: List<LecturerEntity>) {
        lecturers.forEach { insert(it) }
    }
    
    override suspend fun update(lecturer: LecturerEntity) {
        val index = lecturers.indexOfFirst { it.lecturerId == lecturer.lecturerId }
        if (index != -1) {
            lecturers[index] = lecturer
        }
    }
    
    override suspend fun searchByName(searchQuery: String): List<LecturerEntity> {
        return lecturers.filter { it.lecturerName.contains(searchQuery, ignoreCase = true) }
    }
    
    override suspend fun getAll(): List<LecturerEntity> = lecturers
    
    override fun getAllFlow() = throw NotImplementedError("Flow not needed for this test")
    
    override suspend fun getById(id: Long): LecturerEntity? {
        return lecturers.find { it.lecturerId == id }
    }
    
    override suspend fun delete(lecturer: LecturerEntity) {
        lecturers.remove(lecturer)
    }
    
    override suspend fun deleteById(id: Long) {
        val toRemove = lecturers.filter { it.lecturerId == id }
        lecturers.removeAll(toRemove)
    }
    
    override suspend fun deleteAll() {
        lecturers.clear()
    }
}

/**
 * Mock implementation of LectureLecturerCrossRefDao for testing.
 */
private class MockLectureLecturerCrossRefDao : LectureLecturerCrossRefDao {
    private val crossRefs = mutableListOf<LectureLecturerCrossRef>()

    override suspend fun insert(crossRef: LectureLecturerCrossRef) {
        crossRefs.add(crossRef)
    }

    override suspend fun insertAll(crossRefs: List<LectureLecturerCrossRef>) {
        this.crossRefs.addAll(crossRefs)
    }

    override suspend fun delete(crossRef: LectureLecturerCrossRef) {
        crossRefs.remove(crossRef)
    }

    override suspend fun deleteByLectureId(lectureId: Long) {
        val toRemove = crossRefs.filter { it.lectureId == lectureId }
        crossRefs.removeAll(toRemove)
    }

    override suspend fun deleteByLecturerId(lecturerId: Long) {
        val toRemove = crossRefs.filter { it.lecturerId == lecturerId }
        crossRefs.removeAll(toRemove)
    }

    override suspend fun deleteAll() {
        crossRefs.clear()
    }

    override suspend fun getByLectureId(lectureId: Long): List<LectureLecturerCrossRef> {
        return crossRefs.filter { it.lectureId == lectureId }
    }

    override suspend fun getByLecturerId(lecturerId: Long): List<LectureLecturerCrossRef> {
        return crossRefs.filter { it.lecturerId == lecturerId }
    }
}
