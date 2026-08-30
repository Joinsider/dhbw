package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.DocumentParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DownloadFixtures
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DualisFixtures
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
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
import io.ktor.utils.io.ByteReadChannel
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DualisDocumentServiceTest {

    private lateinit var fakeSecureStorage: FakeSecureStorage
    private lateinit var sessionManager: SessionManager
    private lateinit var htmlParser: HtmlParser
    private lateinit var documentParser: DocumentParser

    @BeforeTest
    fun setup() {
        Napier.base(DebugAntilog())
        fakeSecureStorage = FakeSecureStorage()
        sessionManager = SessionManager(fakeSecureStorage)
        htmlParser = HtmlParser()
        documentParser = DocumentParser()
    }

    @AfterTest
    fun teardown() {
        Napier.takeLogarithm()
    }

    private fun createService(mockEngine: MockEngine, authService: AuthenticationService): DualisDocumentService {
        val client = HttpClient(mockEngine) {
            expectSuccess = false
            install(HttpCookies)
        }
        val apiClient = DualisApiClient(client)
        val reAuthenticator = ReAuthenticator(sessionManager, authService)
        return DualisDocumentService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            reAuthenticator = reAuthenticator,
            gateway = DualisPageGateway(apiClient, sessionManager, reAuthenticator)
        )
    }

    @Test
    fun fetchDocuments_success() = runTest {
        val documentHtml = """
            <table class="tb">
                <tr>
                    <td class="tbdata">Studienbescheinigung</td>
                    <td class="tbdata">25.03.26</td>
                    <td class="tbdata">09:40</td>
                    <td class="tbdata"></td>
                    <td class="tbdata">
                        <a class="img download" href="/scripts/filetransfer.exe?token123">Download</a>
                    </td>
                </tr>
            </table>
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(documentHtml),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }

        sessionManager.storeAuthData(AuthData(sessionId = "session123", cookie = "cookie123"))
        
        // Mock Auth Service (not used in success case if authenticated)
        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val authService = AuthenticationService(sessionManager, authClient)

        val service = createService(mockEngine, authService)
        val result = service.fetchDocuments()

        val documents = assertIs<Outcome.Ok<List<de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument>>>(result).value
        assertEquals(1, documents.size)
        assertEquals("Studienbescheinigung", documents.first().title)
        assertEquals("/scripts/filetransfer.exe?token123", documents.first().downloadUrl)
    }

    @Test
    fun fetchDocuments_reauthenticates_whenSessionExpired() = runTest {
        var requestCount = 0
        val loginPageHtml = DualisFixtures.SESSION_EXPIRED
        val documentHtml = """
            <table class="tb">
                <tr>
                    <td class="tbdata">Studienbescheinigung</td>
                    <td class="tbdata">25.03.26</td>
                    <td class="tbdata">09:40</td>
                    <td class="tbdata"></td>
                    <td class="tbdata">
                        <a class="img download" href="/scripts/filetransfer.exe?token123">Download</a>
                    </td>
                </tr>
            </table>
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            requestCount++
            if (requestCount == 1) {
                // First request returns login page (expired session)
                respond(
                    content = ByteReadChannel(loginPageHtml),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            } else {
                // Second request (after re-auth) returns documents
                respond(
                    content = ByteReadChannel(documentHtml),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }

        // Setup session and credentials
        sessionManager.storeCredentials("user", "pass")
        sessionManager.storeAuthData(AuthData(sessionId = "old_session", cookie = "old_cookie"))
        

        // Mock Auth Service for re-authentication
        var authRequestCount = 0
        val authMockEngine = MockEngine { request ->
            authRequestCount++
            if (authRequestCount == 1) {
                // 1. Login form submission
                respond(
                    content = ByteReadChannel("Redirecting..."),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append("refresh", "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=MLSSTART&ARGUMENTS=-Nnew_session")
                        append(HttpHeaders.SetCookie, "cnsc=new_cookie")
                    }
                )
            } else {
                // 2. Following the redirect
                respond(
                    content = ByteReadChannel("<html><body>Herzlich willkommen, Test User! Home Notenspiegel</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }
        val authClient = HttpClient(authMockEngine) {
            install(HttpCookies)
            expectSuccess = false
        }
        val authService = AuthenticationService(sessionManager, authClient)

        val service = createService(mockEngine, authService)
        val result = service.fetchDocuments()

        val documents = assertIs<Outcome.Ok<List<de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument>>>(
            result,
            "Should succeed after re-authentication, got $result"
        ).value
        assertEquals(2, requestCount, "Should have made 2 requests to Dualis")
        assertEquals(1, documents.size)
    }

    @Test
    fun fetchDocuments_fails_afterMaxRetries() = runTest {
        var requestCount = 0
        val loginPageHtml = DualisFixtures.SESSION_EXPIRED

        val mockEngine = MockEngine { request ->
            requestCount++
            respond(
                content = ByteReadChannel(loginPageHtml),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }

        sessionManager.storeCredentials("user", "pass")
        sessionManager.storeAuthData(AuthData(sessionId = "old_session", cookie = "old_cookie"))
        

        // Mock Auth Service for re-authentication (always returns success but session remains "invalid" in main mock)
        var authRequestCount = 0
        val authMockEngine = MockEngine { request ->
            authRequestCount++
            if (authRequestCount % 2 != 0) {
                respond(
                    content = ByteReadChannel("Redirecting..."),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append("refresh", "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=MLSSTART&ARGUMENTS=-Nnew_session")
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel("<html><body>Herzlich willkommen, Test User! Home Notenspiegel</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }
        val authClient = HttpClient(authMockEngine) {
            install(HttpCookies)
            expectSuccess = false
        }
        val authService = AuthenticationService(sessionManager, authClient)

        val service = createService(mockEngine, authService)
        val result = service.fetchDocuments()

        // The login page is an error page, so an exhausted retry means the account cannot reach
        // the content — not that Dualis changed its markup.
        assertEquals(
            Outcome.Err(AppError.SessionExpired),
            result,
            "Dualis' session-expired page must be reported as such, not as an empty document list"
        )
        // One request, one re-authentication, one more request. A second fresh login would be
        // asking the same question again.
        assertEquals(2, requestCount, "Should have attempted twice")
    }
    
    @Test
    fun fetchDocuments_withoutAConnection_reportsOffline() = runTest {
        val mockEngine = MockEngine { request ->
            throw IOException("Network is unreachable")
        }

        sessionManager.storeAuthData(AuthData(sessionId = "session123", cookie = "cookie123"))
        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val authService = AuthenticationService(sessionManager, authClient)

        val service = createService(mockEngine, authService)
        val result = service.fetchDocuments()

        assertEquals(Outcome.Err(AppError.Offline), result)
    }

    /**
     * The login flow a re-authentication needs, as a mock engine: form post, then the redirect.
     */
    private fun workingAuthService(): AuthenticationService {
        var authRequestCount = 0
        val authMockEngine = MockEngine {
            authRequestCount++
            if (authRequestCount == 1) {
                respond(
                    content = ByteReadChannel("Redirecting..."),
                    status = HttpStatusCode.OK,
                    headers = headers {
                        append("refresh", "0; URL=https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=MLSSTART&ARGUMENTS=-Nnew_session")
                        append(HttpHeaders.SetCookie, "cnsc=new_cookie")
                    }
                )
            } else {
                respond(
                    content = ByteReadChannel("<html><body>Herzlich willkommen, Test User! Home Notenspiegel</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headers { append(HttpHeaders.ContentType, "text/html") }
                )
            }
        }
        val authClient = HttpClient(authMockEngine) {
            install(HttpCookies)
            expectSuccess = false
        }
        return AuthenticationService(sessionManager, authClient)
    }

    @Test
    fun downloadDocument_retriesWhenDualisAnswersWithItsTimeoutPage() = runTest {
        // The download endpoint reports an expired session with HTTP 200 and a page, so the
        // status code cannot be what decides. Before this, the page itself was handed on as the
        // document and shown to the user as a PDF that no viewer could open.
        var downloads = 0
        val mockEngine = MockEngine {
            downloads++
            val body = if (downloads == 1) DownloadFixtures.SESSION_TIMEOUT_PAGE else pdfBytes
            respond(content = ByteReadChannel(body), status = HttpStatusCode.OK)
        }

        sessionManager.storeCredentials("user", "pass")
        sessionManager.storeAuthData(AuthData(sessionId = "old_session", cookie = "old_cookie"))

        val service = createService(mockEngine, workingAuthService())
        val result = service.downloadDocument("/scripts/filetransfer.exe?token123")

        assertContentEquals(pdfBytes, assertIs<Outcome.Ok<ByteArray>>(result).value)
        assertEquals(2, downloads, "the download is retried once the session is fresh")
    }

    @Test
    fun downloadDocument_givesUpWhenThePageComesBackAgain() = runTest {
        var downloads = 0
        val mockEngine = MockEngine {
            downloads++
            respond(content = ByteReadChannel(DownloadFixtures.SESSION_TIMEOUT_PAGE), status = HttpStatusCode.OK)
        }

        sessionManager.storeCredentials("user", "pass")
        sessionManager.storeAuthData(AuthData(sessionId = "old_session", cookie = "old_cookie"))

        val service = createService(mockEngine, workingAuthService())
        val result = service.downloadDocument("/scripts/filetransfer.exe?token123")

        assertEquals(AppError.SessionExpired, assertIs<Outcome.Err>(result).error)
        assertEquals(2, downloads, "one retry, not an endless loop")
    }

    private val pdfBytes = DownloadFixtures.PDF_HEADER

    // ── downloadDocument: demo mode ─────────────────────────────────────────

    @Test
    fun downloadDocument_demoMode_forAKnownDocument_returnsItsBytes() = runTest {
        sessionManager.setDemoMode(true)
        val mockEngine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }
        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val service = createService(mockEngine, AuthenticationService(sessionManager, authClient))

        val documents = assertIs<Outcome.Ok<List<de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument>>>(
            service.fetchDocuments()
        ).value
        val result = service.downloadDocument(documents.first().downloadUrl)

        val bytes = assertIs<Outcome.Ok<ByteArray>>(result).value
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun downloadDocument_demoMode_forAnUnknownUrl_reportsUnsupported() = runTest {
        sessionManager.setDemoMode(true)
        val mockEngine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }
        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val service = createService(mockEngine, AuthenticationService(sessionManager, authClient))

        val result = service.downloadDocument("/scripts/filetransfer.exe?not-a-demo-document")

        assertIs<AppError.Unsupported>(assertIs<Outcome.Err>(result).error)
    }

    // ── downloadDocument: authentication edge cases ─────────────────────────

    @Test
    fun downloadDocument_withNoSessionAndNoStoredCredentials_reportsNoCredentials() = runTest {
        // Neither demo mode, nor an existing session, nor anything to log in with.
        val mockEngine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }
        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val service = createService(mockEngine, AuthenticationService(sessionManager, authClient))

        val result = service.downloadDocument("/scripts/filetransfer.exe?token123")

        assertEquals(Outcome.Err(AppError.NoCredentials), result)
    }

    @Test
    fun downloadDocument_whenReauthenticationItselfFails_propagatesThatFailure() = runTest {
        sessionManager.storeCredentials("user", "wrong-password")
        // Not authenticated (no auth data stored), so downloadWithRetry must re-authenticate first.
        val mockEngine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) }
        val rejectingAuthClient = HttpClient(MockEngine {
            respond(
                content = ByteReadChannel("<html><body><h1>LOGINCHECK failed</h1></body></html>"),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "text/html") }
            )
        }) { expectSuccess = false }
        val authService = AuthenticationService(sessionManager, rejectingAuthClient)
        val service = createService(mockEngine, authService)

        val result = service.downloadDocument("/scripts/filetransfer.exe?token123")

        assertEquals(Outcome.Err(AppError.InvalidCredentials), result)
    }

    @Test
    fun downloadDocument_withAnEmptySessionId_reportsSessionExpiredWithoutARequest() = runTest {
        sessionManager.storeAuthData(AuthData(sessionId = "", cookie = null))
        var downloadAttempts = 0
        val mockEngine = MockEngine {
            downloadAttempts++
            respond(ByteReadChannel(pdfBytes), HttpStatusCode.OK)
        }
        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val service = createService(mockEngine, AuthenticationService(sessionManager, authClient))

        val result = service.downloadDocument("/scripts/filetransfer.exe?token123")

        assertEquals(Outcome.Err(AppError.SessionExpired), result)
        assertEquals(0, downloadAttempts, "an empty session id must not even try the request")
    }

    @Test
    fun downloadDocument_onANonSessionError_doesNotRetry() = runTest {
        sessionManager.storeAuthData(AuthData(sessionId = "session123", cookie = "cookie123"))
        var downloadAttempts = 0
        val mockEngine = MockEngine {
            downloadAttempts++
            respond(ByteReadChannel("server error"), HttpStatusCode.InternalServerError)
        }
        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val service = createService(mockEngine, AuthenticationService(sessionManager, authClient))

        val result = service.downloadDocument("/scripts/filetransfer.exe?token123")

        assertEquals(Outcome.Err(AppError.Http(500)), result)
        assertEquals(1, downloadAttempts, "a plain server error is not a session problem, so no retry")
    }
}
