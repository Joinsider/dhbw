/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Login against a mock engine.
 *
 * Every failing case asserts *which* [AppError] comes back, not just that something failed:
 * telling "wrong password" from "no connection" from "Dualis answered with something new" is the
 * whole point of the classified error channel.
 */
class AuthenticationServiceTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var sessionManager: SessionManager

    @BeforeTest
    fun setup() {
        // Initialize Napier for logging in tests
        Napier.base(DebugAntilog())

        // Initialize fake storage and session manager
        fakeSecureStorage = FakeSecureStorage()
        sessionManager = SessionManager(fakeSecureStorage)
    }

    @AfterTest
    fun teardown() {
        // Clean up Napier
        Napier.takeLogarithm()
    }

    @Test
    fun login_withDemoCredentials_succeedsWithoutTouchingTheNetwork() = runTest {
        // Given
        val mockClient = HttpClient(MockEngine {
            // This should never be called for demo credentials
            respond(ByteReadChannel("Should not reach here"), HttpStatusCode.InternalServerError)
        }) {
            expectSuccess = false
        }
        val service = AuthenticationService(sessionManager, mockClient)
        val username = "demo@hb.dhbw-stuttgart.de"
        val password = "demo123"

        // When
        val result = service.login(username, password)

        // Then
        val session = assertIs<Outcome.Ok<*>>(result, "Demo credentials must log in").value
        assertTrue((session as de.fampopprol.dhbwhorb.domain.model.Session).isDemo)
        service.close()
    }

    @Test
    fun login_withInvalidCredentials_reportsInvalidCredentials() = runTest {
        // Given
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""
                    <html>
                    <body>
                    <h1>LOGINCHECK failed</h1>
                    </body>
                    </html>
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headers {
                    append(HttpHeaders.ContentType, "text/html")
                }
            )
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "invalid@dhbw.de"
        val password = "wrongpassword"

        // When
        val result = service.login(username, password)

        // Then
        assertEquals(Outcome.Err(AppError.InvalidCredentials), result)
        service.close()
    }

    @Test
    fun login_withServerError_reportsTheStatusCode() = runTest {
        // Given
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("Internal Server Error"),
                status = HttpStatusCode.InternalServerError,
                headers = headers {
                    append(HttpHeaders.ContentType, "text/html")
                }
            )
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "user@dhbw.de"
        val password = "password"

        // When
        val result = service.login(username, password)

        // Then
        assertEquals(Outcome.Err(AppError.Http(500)), result)
        service.close()
    }

    @Test
    fun login_withoutAConnection_reportsOffline() = runTest {
        // Given
        val mockEngine = MockEngine {
            throw IOException("Network is unreachable")
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "user@dhbw.de"
        val password = "password"

        // When
        val result = service.login(username, password)

        // Then
        assertEquals(
            Outcome.Err(AppError.Offline),
            result,
            "A transport failure is 'you are offline', not an unknown error"
        )
        service.close()
    }

    @Test
    fun login_withoutARedirectHeader_reportsAParseFailure() = runTest {
        // Given
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("""
                    <html>
                    <body>
                    <h1>Login response without redirect</h1>
                    </body>
                    </html>
                """.trimIndent()),
                status = HttpStatusCode.OK,
                headers = headers {
                    append(HttpHeaders.ContentType, "text/html")
                }
            )
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)
        val username = "user@dhbw.de"
        val password = "password"

        // When
        val result = service.login(username, password)

        // Then
        // A 200 that is neither the login form nor a redirect means Dualis changed its handshake.
        assertIs<AppError.Parse>(assertIs<Outcome.Err>(result).error)
        service.close()
    }

    @Test
    fun isAuthenticated_returnsTrueWhenDemoMode() = runTest {
        // Given
        val mockClient = HttpClient(MockEngine {
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        }) {
            expectSuccess = false
        }
        val service = AuthenticationService(sessionManager, mockClient)
        sessionManager.setDemoMode(true)

        // When
        val isAuth = service.isAuthenticated()

        // Then
        assertTrue(isAuth, "Should be authenticated in demo mode")
        service.close()
    }

    @Test
    fun logout_clearsSessionData() = runTest {
        // Given
        val mockClient = HttpClient(MockEngine {
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        }) {
            expectSuccess = false
        }
        val service = AuthenticationService(sessionManager, mockClient)
        sessionManager.storeCredentials("test@dhbw.de", "password")
        sessionManager.setDemoMode(true)

        // When
        service.logout()

        // Then
        assertTrue(!service.isAuthenticated(), "Should not be authenticated after logout")
        service.close()
    }

    @Test
    fun login_fullSuccessfulFlow_withNameFound_storesSessionAndFullName() = runTest {
        // Given: the login POST is accepted and redirects (with an ARGUMENTS auth token) straight
        // to a main page that carries the welcome banner.
        val mockEngine = MockEngine { request ->
            if (request.method == io.ktor.http.HttpMethod.Post) {
                respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(
                            "refresh",
                            "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH&ARGUMENTS=-N123456789012345,-N000000000000000,-N000000000000000"
                        )
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel(
                        "<html><body><h1>Herzlich willkommen, Max Mustermann!</h1></body></html>"
                    ),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        val session = assertIs<Outcome.Ok<de.fampopprol.dhbwhorb.domain.model.Session>>(result).value
        assertEquals("Max Mustermann", session.userFullName)
        assertTrue(!session.isDemo)
        assertEquals("123456789012345", sessionManager.getAuthData()?.sessionId)
        service.close()
    }

    @Test
    fun login_fullSuccessfulFlow_withoutNameInPage_stillSucceedsWithNullName() = runTest {
        // Given: the main page is reachable (STARTPAGE indicator) but never carries the welcome
        // banner used to extract the user's name.
        val mockEngine = MockEngine { request ->
            if (request.method == io.ktor.http.HttpMethod.Post) {
                respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(
                            "refresh",
                            "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH&ARGUMENTS=-N999888777666555,-N000000000000000"
                        )
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel("<html><body>STARTPAGE reached, no welcome banner here</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        val session = assertIs<Outcome.Ok<de.fampopprol.dhbwhorb.domain.model.Session>>(result).value
        assertNull(session.userFullName)
        assertTrue(!session.isDemo)
        service.close()
    }

    @Test
    fun login_bodyReadThrows_reportsError() = runTest {
        // Given: the login response's body stream fails while being read.
        val mockEngine = MockEngine {
            val brokenChannel = ByteChannel()
            brokenChannel.cancel(IOException("body read failed"))
            respond(
                content = brokenChannel,
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        assertIs<Outcome.Err>(result)
        service.close()
    }

    @Test
    fun login_exceedingMaxRedirectDepth_reportsAParseFailure() = runTest {
        // Given: the login POST succeeds, then every follow-up GET keeps handing back another
        // interstitial redirect page, forcing the recursion past MAX_REDIRECT_DEPTH (10).
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            if (request.method == io.ktor.http.HttpMethod.Post) {
                respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append("refresh", "0; URL=/scripts/mgrqispi.dll?step=0")
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel(
                        """<html><head><meta http-equiv="refresh" content="0; URL=/scripts/mgrqispi.dll?step=$requestCount"></head><body>please wait</body></html>"""
                    ),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        val error = assertIs<AppError.Parse>(assertIs<Outcome.Err>(result).error)
        assertTrue(
            error.hint.contains("redirects"),
            "Expected the redirect-depth-limit hint, got: ${error.hint}"
        )
        // One login POST plus MAX_REDIRECT_DEPTH (10) follow-up GETs.
        assertEquals(11, requestCount)
        service.close()
    }

    @Test
    fun login_redirectGetThrows_reportsError() = runTest {
        // Given: the login POST succeeds, but the very first follow-up GET fails at the
        // transport level (not just the body read).
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            if (request.method == io.ktor.http.HttpMethod.Post) {
                respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(
                            "refresh",
                            "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH&ARGUMENTS=-N123456789012345,-N000000000000000"
                        )
                    }
                )
            } else {
                throw IOException("connection reset while following redirect")
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        assertEquals(Outcome.Err(AppError.Offline), result)
        service.close()
    }

    @Test
    fun login_followsOneInterstitialRedirectPage_thenReachesMainPage() = runTest {
        // Given: login POST -> one interstitial redirect page -> the real main page.
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            when {
                request.method == io.ktor.http.HttpMethod.Post -> respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(
                            "refresh",
                            "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH&ARGUMENTS=-N123456789012345,-N000000000000000"
                        )
                    }
                )

                requestCount == 2 -> respond(
                    content = ByteReadChannel(
                        """<html><head><meta http-equiv="refresh" content="0; URL=/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE"></head><body>please wait</body></html>"""
                    ),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )

                else -> respond(
                    content = ByteReadChannel(
                        "<html><body><h1>Herzlich willkommen, Erika Musterfrau!</h1></body></html>"
                    ),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        val session = assertIs<Outcome.Ok<de.fampopprol.dhbwhorb.domain.model.Session>>(result).value
        assertEquals("Erika Musterfrau", session.userFullName)
        assertEquals(3, requestCount, "login POST + interstitial GET + main-page GET")
        service.close()
    }

    @Test
    fun login_redirectPageWithNoFollowUpUrl_reportsAParseFailure() = runTest {
        // Given: the interstitial page is recognized as a redirect page, but its meta refresh
        // tag has no "URL=" segment for extractRedirectUrlFromHtml to find.
        val mockEngine = MockEngine { request ->
            if (request.method == io.ktor.http.HttpMethod.Post) {
                respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(
                            "refresh",
                            "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH&ARGUMENTS=-N123456789012345,-N000000000000000"
                        )
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel(
                        """<html><head><meta http-equiv="refresh" content="5"></head><body>please wait</body></html>"""
                    ),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        val error = assertIs<AppError.Parse>(assertIs<Outcome.Err>(result).error)
        assertTrue(error.hint.contains("no follow-up URL"))
        service.close()
    }

    @Test
    fun login_pageAfterRedirectIsNeitherMainNorRedirect_reportsSessionExpired() = runTest {
        // Given: the page reached after the redirect chain is a 200 that is neither the main
        // page nor another redirect page - Dualis' way of saying the session was rejected.
        val mockEngine = MockEngine { request ->
            if (request.method == io.ktor.http.HttpMethod.Post) {
                respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(
                            "refresh",
                            "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH&ARGUMENTS=-N123456789012345,-N000000000000000"
                        )
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel("<html><head><title>Session rejected</title></head><body>unexpected</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        assertEquals(Outcome.Err(AppError.SessionExpired), result)
        service.close()
    }

    @Test
    fun login_withoutAuthToken_fallsBackToSessionCookie() = runTest {
        // Given: the redirect URL has no ARGUMENTS (so extractAuthToken returns null), but the
        // main page response sets a JSESSIONID cookie for dualis.dhbw.de.
        val mockEngine = MockEngine { request ->
            if (request.method == io.ktor.http.HttpMethod.Post) {
                respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(
                            "refresh",
                            "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH"
                        )
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel(
                        "<html><body><h1>Herzlich willkommen, Cookie Fallback!</h1></body></html>"
                    ),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(HttpHeaders.SetCookie, "JSESSIONID=cookie-session-id; Path=/; Domain=dualis.dhbw.de")
                    }
                )
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        val session = assertIs<Outcome.Ok<de.fampopprol.dhbwhorb.domain.model.Session>>(result).value
        assertEquals("Cookie Fallback", session.userFullName)
        assertEquals("cookie-session-id", sessionManager.getAuthData()?.sessionId)
        service.close()
    }

    @Test
    fun login_withoutAuthTokenOrCookie_reportsAParseFailure() = runTest {
        // Given: no ARGUMENTS in the redirect URL and no session cookie is ever set, so neither
        // sessionId source produces a usable value.
        val mockEngine = MockEngine { request ->
            if (request.method == io.ktor.http.HttpMethod.Post) {
                respond(
                    content = ByteReadChannel("<html><body>redirecting...</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append(HttpHeaders.ContentType, "text/html")
                        append(
                            "refresh",
                            "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=STARTPAGE_DISPATCH"
                        )
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel("<html><body>STARTPAGE, no cookie, no name</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }

        val service = createAuthenticationServiceWithMockEngine(mockEngine)

        // When
        val result = service.login("user@dhbw.de", "password")

        // Then
        val error = assertIs<AppError.Parse>(assertIs<Outcome.Err>(result).error)
        assertTrue(error.hint.contains("neither an auth token nor a session cookie"))
        service.close()
    }

    // Helper function to create AuthenticationService with mock engine
    private fun createAuthenticationServiceWithMockEngine(mockEngine: MockEngine): AuthenticationService {
        val mockClient = HttpClient(mockEngine) {
            expectSuccess = false
            install(HttpCookies)
        }
        return AuthenticationService(sessionManager, client = mockClient)
    }
}
