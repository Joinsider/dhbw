/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisDocumentService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisPageGateway
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.database.dao.documents.CachedDocumentDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.documents.CachedDocumentEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.documents.CachedDocumentHead
import de.fampopprol.dhbwhorb.data.storage.documents.DocumentCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DocumentRepositoryImpl.listDocuments] and the failure path of [DocumentRepositoryImpl.purgeExpiredDocuments].
 *
 * The download path (with its cache hit/miss/expiry behaviour) is covered separately in
 * [DocumentRepositoryCacheTest]; this file exercises what that one does not.
 */
class DocumentRepositoryImplTest {

    private fun demoService(): DualisDocumentService {
        val sessionManager = SessionManager(FakeSecureStorage())
        sessionManager.setDemoMode(true)
        sessionManager.storeCredentials(SessionManager.DEMO_EMAIL, SessionManager.DEMO_PASSWORD)

        val client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val apiClient = DualisApiClient(client)
        val authService = AuthenticationService(sessionManager, client)
        val reAuthenticator = ReAuthenticator(sessionManager, authService)

        return DualisDocumentService(
            apiClient = apiClient,
            sessionManager = sessionManager,
            reAuthenticator = reAuthenticator,
            gateway = DualisPageGateway(apiClient, sessionManager, reAuthenticator)
        )
    }

    /** A DAO whose every call blows up, to exercise the cache-failure paths that must be ignored. */
    private class ThrowingCachedDocumentDao : CachedDocumentDao {
        override suspend fun insert(document: CachedDocumentEntity) = throw IllegalStateException("db gone")
        override suspend fun get(downloadUrl: String): CachedDocumentEntity = throw IllegalStateException("db gone")
        override suspend fun delete(downloadUrl: String) = throw IllegalStateException("db gone")
        override suspend fun deleteCachedAtOrBefore(cutoffTimestamp: Long) = throw IllegalStateException("db gone")
        override suspend fun heads(headLength: Int): List<CachedDocumentHead> = throw IllegalStateException("db gone")
        override suspend fun deleteAll() = throw IllegalStateException("db gone")
    }

    @Test
    fun listDocuments_purgesExpiredDocumentsThenReturnsTheServicesList() = runTest {
        var purges = 0
        val cache = object : CachedDocumentDao by ThrowingCachedDocumentDao() {
            override suspend fun deleteCachedAtOrBefore(cutoffTimestamp: Long): Int {
                purges++
                return 0
            }
            override suspend fun heads(headLength: Int): List<CachedDocumentHead> = emptyList()
        }
        val repository = DocumentRepositoryImpl(demoService(), DocumentCache(cache))

        val result = repository.listDocuments()

        assertTrue(assertIs<Outcome.Ok<List<DualisDocument>>>(result).value.isNotEmpty())
        assertEquals(1, purges, "opening the list must purge expired documents first")
    }

    @Test
    fun purgeExpiredDocuments_ignoresACacheFailureAndReportsZero() = runTest {
        val repository = DocumentRepositoryImpl(demoService(), DocumentCache(ThrowingCachedDocumentDao()))

        val purged = repository.purgeExpiredDocuments()

        assertEquals(0, purged, "a broken cache must not crash the caller, nor claim a purge happened")
    }
}
