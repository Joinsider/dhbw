/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.core.hash.Sha256
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.models.AuthData
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisDocumentService
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DownloadFixtures
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisPageGateway
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.documents.DocumentCache
import de.fampopprol.dhbwhorb.testutil.InMemoryCachedDocumentDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * The repository's half of the cache: what reaches the network and what does not.
 *
 * Driven through a mock HTTP engine rather than a fake service, so "the second download makes no
 * request" is a statement about requests actually not being made.
 */
class DocumentRepositoryCacheTest {

    private val document = DualisDocument(
        title = "Studienbescheinigung",
        date = "25.03.26",
        time = "09:40",
        downloadUrl = "/scripts/filetransfer.exe?token123"
    )
    private val pdf = "%PDF-1.4 bescheinigung".encodeToByteArray()

    private val dao = InMemoryCachedDocumentDao()
    private var clock = 5_000_000L

    private var requests = 0

    private fun repository(answer: ByteArray = pdf): DocumentRepositoryImpl {
        val engine = MockEngine {
            requests++
            respond(
                content = ByteReadChannel(answer),
                status = HttpStatusCode.OK,
                headers = headers { append(HttpHeaders.ContentType, "application/pdf") }
            )
        }
        val client = HttpClient(engine) { install(HttpCookies) }
        val apiClient = DualisApiClient(client)
        val storage = FakeSecureStorage()
        val sessionManager = SessionManager(storage)
        sessionManager.storeAuthData(AuthData(sessionId = "session123", cookie = "cookie123"))

        val authClient = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val reAuthenticator = ReAuthenticator(sessionManager, AuthenticationService(sessionManager, authClient))
        val service = DualisDocumentService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            reAuthenticator = reAuthenticator,
            gateway = DualisPageGateway(apiClient, sessionManager, reAuthenticator)
        )

        return DocumentRepositoryImpl(service, DocumentCache(dao) { clock })
    }

    @Test
    fun theFirstDownloadIsFetchedAndCached() = runTest {
        val result = repository().downloadDocument(document)

        assertContentEquals(pdf, assertIs<Outcome.Ok<ByteArray>>(result).value)
        assertEquals(1, requests)
        assertEquals(1, dao.size)
    }

    @Test
    fun theSecondDownloadMakesNoRequest() = runTest {
        val repository = repository()
        repository.downloadDocument(document)

        val again = repository.downloadDocument(document)

        assertContentEquals(pdf, assertIs<Outcome.Ok<ByteArray>>(again).value)
        assertEquals(1, requests, "the bytes were already on the device")
    }

    @Test
    fun afterFourWeeksItIsFetchedAgain() = runTest {
        val repository = repository()
        repository.downloadDocument(document)

        clock += DocumentCache.MAX_AGE_MS

        val again = repository.downloadDocument(document)

        assertContentEquals(pdf, assertIs<Outcome.Ok<ByteArray>>(again).value)
        assertEquals(2, requests)
    }

    @Test
    fun theStoredHashIsTheHashOfWhatWasDownloaded() = runTest {
        repository().downloadDocument(document)

        val stored = assertNotNull(dao.get(document.downloadUrl))
        assertEquals(Sha256.hex(pdf), stored.contentHash)
        assertContentEquals(pdf, stored.content)
    }

    @Test
    fun aTimeoutPageIsNeitherReturnedNorCached() = runTest {
        // Dualis answers an expired session with a page and HTTP 200. The old code handed those
        // bytes to the viewer as a PDF; the cache then kept them for four weeks.
        val repository = repository(answer = DownloadFixtures.SESSION_TIMEOUT_PAGE)

        val result = repository.downloadDocument(document)

        // Which error depends on how the re-authentication it triggers goes — with no stored
        // credentials it cannot go anywhere. What matters here is that the page is not the answer.
        assertIs<Outcome.Err>(result)
        assertEquals(0, dao.size, "a page must never take a document's place in the cache")
    }
}
