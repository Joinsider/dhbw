package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.DocumentParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
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
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
            install(HttpCookies)
        }
        val apiClient = DualisApiClient(client)
        return DualisDocumentService(
            apiClient,
            sessionManager,
            authService
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

        assertTrue(result.isSuccess)
        val documents = result.getOrNull()
        assertEquals(1, documents?.size)
        assertEquals("Studienbescheinigung", documents?.first()?.title)
        assertEquals("/scripts/filetransfer.exe?token123", documents?.first()?.downloadUrl)
    }

    @Test
    fun fetchDocuments_reauthenticates_whenSessionExpired() = runTest {
        var requestCount = 0
        val loginPageHtml = "<html><title>Login</title><body>Please login</body></html>"
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

        assertTrue(result.isSuccess, "Should succeed after re-authentication. Error: ${result.exceptionOrNull()?.message}")
        assertEquals(2, requestCount, "Should have made 2 requests to Dualis")
        val documents = result.getOrNull()
        assertEquals(1, documents?.size)
    }

    @Test
    fun fetchDocuments_fails_afterMaxRetries() = runTest {
        var requestCount = 0
        val loginPageHtml = "<html><title>Login</title><body>Please login</body></html>"

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

        assertTrue(result.isFailure, "Should fail after max retries")
        // Initial attempt (1) + Retry 1 (2) + Retry 2 (3) = 3 attempts total if MAX_RETRY_ATTEMPTS is 2
        assertEquals(3, requestCount, "Should have attempted 3 times")
    }
    
    @Test
    fun fetchDocuments_networkError() = runTest {
        val mockEngine = MockEngine { request ->
            throw Exception("Network error")
        }

        sessionManager.storeAuthData(AuthData(sessionId = "session123", cookie = "cookie123"))
        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val authService = AuthenticationService(sessionManager, authClient)

        val service = createService(mockEngine, authService)
        val result = service.fetchDocuments()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Network error") == true)
    }
}
