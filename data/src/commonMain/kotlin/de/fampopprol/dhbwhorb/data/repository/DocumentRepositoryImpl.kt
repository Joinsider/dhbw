/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisDocumentService
import de.fampopprol.dhbwhorb.data.storage.documents.DocumentCache
import de.fampopprol.dhbwhorb.domain.repository.DocumentRepository
import io.github.aakira.napier.Napier

/** [DocumentRepository] on top of [DualisDocumentService], with [DocumentCache] in front of it. */
class DocumentRepositoryImpl(
    private val documentService: DualisDocumentService,
    private val cache: DocumentCache
) : DocumentRepository {

    private companion object {
        const val TAG = "DocumentRepository"
    }

    override suspend fun listDocuments(): Outcome<List<DualisDocument>> {
        // Opening the documents screen is the other moment expiry is enforced, next to app start:
        // a session that stays open for weeks would otherwise never reach the deadline.
        purgeExpiredDocuments()

        return documentService.fetchDocuments()
    }

    /**
     * The document's bytes, from the cache when it holds a valid copy.
     *
     * A cache that misbehaves must never cost the user the document: every cache call is allowed
     * to fail, and each failure only means the network answers instead.
     */
    override suspend fun downloadDocument(document: DualisDocument): Outcome<ByteArray> {
        val cached = runCatchingCache("reading the document cache") { cache.read(document) }
        if (cached != null) return Outcome.Ok(cached.content)

        val downloaded = documentService.downloadDocument(document.downloadUrl)
        if (downloaded is Outcome.Ok) {
            runCatchingCache("caching the document") { cache.write(document, downloaded.value) }
        }
        return downloaded
    }

    override suspend fun purgeExpiredDocuments(): Int =
        runCatchingCache("purging the document cache") { cache.purge() } ?: 0

    private inline fun <T> runCatchingCache(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            Napier.w("Ignoring a cache failure while $what: ${e.message}", e, tag = TAG)
            null
        }
}
