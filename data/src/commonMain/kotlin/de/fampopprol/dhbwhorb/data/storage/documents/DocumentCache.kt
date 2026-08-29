/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.documents

import de.fampopprol.dhbwhorb.core.hash.Sha256
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.storage.database.dao.documents.CachedDocumentDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.documents.CachedDocumentEntity
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Downloaded documents, kept for four weeks.
 *
 * A document Dualis has already handed over does not change under its URL, so downloading it a
 * second time costs a request and a wait for bytes that are already on the device. It is kept
 * with the SHA-256 of its content, which is what makes the copy verifiable: bytes that no longer
 * hash to what was written are not served, they are dropped and fetched again.
 *
 * Nothing is kept longer than [MAX_AGE], because a cache without an end is a copy of the
 * student's certificates sitting on the device forever. Expiry is enforced on every read and by
 * [purgeExpired], which the document list calls, so an entry that is never asked for again still
 * goes away.
 */
@OptIn(ExperimentalTime::class)
class DocumentCache(
    private val dao: CachedDocumentDao,
    /** Injectable so the expiry tests do not have to wait four weeks. */
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    companion object {
        private const val TAG = "DocumentCache"

        /** Four weeks, in milliseconds. */
        const val MAX_AGE_MS: Long = 28L * 24 * 60 * 60 * 1000
    }

    /**
     * The cached bytes of [document], or null when there are none to serve.
     *
     * Null covers all three ways that can happen — never downloaded, too old, or no longer
     * matching its hash — because the caller does the same thing in each case: download it.
     */
    suspend fun read(document: DualisDocument): CachedDocument? {
        val entry = dao.get(document.downloadUrl) ?: return null

        val age = now() - entry.cachedAtTimestamp
        if (age >= MAX_AGE_MS) {
            Napier.d("Dropping '${entry.title}' from the cache: ${age / DAY_MS} days old", tag = TAG)
            dao.delete(entry.downloadUrl)
            return null
        }

        val actualHash = Sha256.hex(entry.content)
        if (actualHash != entry.contentHash) {
            // Storage went wrong somewhere. Serving it would hand the viewer a broken PDF and
            // blame Dualis for it.
            Napier.w("Cached '${entry.title}' does not match its hash; discarding it", tag = TAG)
            dao.delete(entry.downloadUrl)
            return null
        }

        Napier.d("Serving '${entry.title}' from the cache (${entry.content.size} bytes)", tag = TAG)
        return CachedDocument(entry.content, entry.contentHash, entry.cachedAtTimestamp)
    }

    /**
     * Stores [content] under [document], hash included.
     *
     * Re-downloading a document the cache already holds keeps the *original* timestamp when the
     * content is unchanged: the four weeks are counted from when the file was first seen, so a
     * repeated download cannot keep a copy alive indefinitely. Content that did change starts its
     * own four weeks, because it is a different file.
     */
    suspend fun write(document: DualisDocument, content: ByteArray): CachedDocument {
        val hash = Sha256.hex(content)
        val previous = dao.get(document.downloadUrl)
        val cachedAt = if (previous?.contentHash == hash) previous.cachedAtTimestamp else now()

        dao.insert(
            CachedDocumentEntity(
                downloadUrl = document.downloadUrl,
                title = document.title,
                contentHash = hash,
                content = content,
                cachedAtTimestamp = cachedAt
            )
        )
        Napier.d("Cached '${document.title}' (${content.size} bytes, sha256 ${hash.take(12)}…)", tag = TAG)
        return CachedDocument(content, hash, cachedAt)
    }

    /** Deletes everything older than [MAX_AGE_MS]. @return how many went. */
    suspend fun purgeExpired(): Int {
        val deleted = dao.deleteCachedAtOrBefore(now() - MAX_AGE_MS)
        if (deleted > 0) Napier.d("Purged $deleted expired document(s)", tag = TAG)
        return deleted
    }
}

/** A document served from the cache, with the hash it was stored under. */
class CachedDocument(
    val content: ByteArray,
    val contentHash: String,
    val cachedAtTimestamp: Long
)

private const val DAY_MS = 24L * 60 * 60 * 1000
