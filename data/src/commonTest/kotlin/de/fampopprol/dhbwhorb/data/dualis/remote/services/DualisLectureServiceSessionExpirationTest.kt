package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.testutil.MockAppDatabase
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DualisLectureServiceSessionExpirationTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var sessionManager: SessionManager
    private lateinit var mockDatabase: MockAppDatabase

    @BeforeTest
    fun setup() {
        // Initialize Napier for logging in tests
        try {
            Napier.base(DebugAntilog())
        } catch (e: Exception) {
            // Already initialized
        }

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
            
            // Check if it's a login request (POST with LOGINCHECK in body or URL)
            val isLoginRequest = url.contains("LOGINCHECK") || url.contains("login") || 
                                (request.method == HttpMethod.Post && url.contains("mgrqispi.dll"))
            
            when {
                // Login request
                isLoginRequest && !url.contains("SCHEDULER") -> {
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
                                    <div class="weekday">Monday</div>
                                    <h1>Stundenplan</h1>
                                    <div class="appointment">Lecture 1</div>
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

        val reAuthenticator = ReAuthenticator(sessionManager, authService)

        val service = DualisLectureService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            gateway = DualisPageGateway(apiClient, sessionManager, reAuthenticator),
            lectureEventDao = mockDatabase.lectureDao(),
            lecturerDao = mockDatabase.lecturerDao(),
            lectureLecturerCrossRefDao = mockDatabase.lectureLecturerCrossRefDao()
        )

        // When
        val date = LocalDate(2024, 1, 15)
        val result = service.getWeeklyLecturesForDate(date)

        // Then
        assertIs<Outcome.Ok<*>>(result, "Should fetch lectures after re-authentication, got $result")
        assertEquals(2, callCount, "Should make two calls: first fails, second succeeds after re-auth")
        assertTrue(loginCallCount >= 1, "Should perform at least one re-authentication")

        httpClient.close()
    }
}
