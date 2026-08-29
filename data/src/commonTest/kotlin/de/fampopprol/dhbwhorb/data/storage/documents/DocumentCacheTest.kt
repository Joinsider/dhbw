/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.documents

import de.fampopprol.dhbwhorb.core.hash.Sha256
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.fixtures.DownloadFixtures
import de.fampopprol.dhbwhorb.data.storage.database.entities.documents.CachedDocumentEntity
import de.fampopprol.dhbwhorb.testutil.InMemoryCachedDocumentDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentCacheTest {

    private val document = DualisDocument(
        title = "Notenbescheinigung",
        date = "28.08.2026",
        time = "19:00",
        downloadUrl = "/scripts/filetransfer.exe?doc=1"
    )
    private val content = "%PDF-1.4 a certificate".encodeToByteArray()

    private val dao = InMemoryCachedDocumentDao()
    private var clock = 1_000_000L
    private val cache = DocumentCache(dao) { clock }

    private val fourWeeks = DocumentCache.MAX_AGE_MS

    @Test
    fun anUnknownDocumentIsNotCached() = runTest {
        assertNull(cache.read(document))
    }

    @Test
    fun aWrittenDocumentComesBackWithItsHash() = runTest {
        cache.write(document, content)

        val cached = assertNotNull(cache.read(document))
        assertEquals(content.toList(), cached.content.toList())
        assertEquals(Sha256.hex(content), cached.contentHash)
        assertEquals(clock, cached.cachedAtTimestamp)
    }

    @Test
    fun aDocumentJustUnderFourWeeksOldIsStillServed() = runTest {
        cache.write(document, content)

        clock += fourWeeks - 1

        assertNotNull(cache.read(document))
    }

    @Test
    fun atFourWeeksItIsGone() = runTest {
        cache.write(document, content)

        clock += fourWeeks

        assertNull(cache.read(document), "four weeks is the maximum, not a minimum")
        assertEquals(0, dao.size, "an expired document is deleted, not just ignored")
    }

    @Test
    fun purgingRemovesWhatIsTooOldAndKeepsTheRest() = runTest {
        cache.write(document, content)
        clock += fourWeeks - 1000
        val newer = document.copy(title = "Immatrikulation", downloadUrl = "/scripts/filetransfer.exe?doc=2")
        cache.write(newer, "%PDF-1.4 second".encodeToByteArray())

        // Now the first is over four weeks old and the second is not.
        clock += 1000

        assertEquals(1, cache.purge())
        assertEquals(1, dao.size)
        assertNotNull(cache.read(newer))
    }

    @Test
    fun purgingAnEmptyCacheRemovesNothing() = runTest {
        assertEquals(0, cache.purge())
    }

    @Test
    fun purgingAlsoRemovesPagesThatAreNotDocuments() = runTest {
        // Written by a version that did not yet recognise Dualis' timeout page. Reading it would
        // drop it, but only if that document is ever opened again.
        dao.insert(
            CachedDocumentEntity(
                downloadUrl = document.downloadUrl,
                title = document.title,
                contentHash = Sha256.hex(DownloadFixtures.SESSION_TIMEOUT_PAGE),
                content = DownloadFixtures.SESSION_TIMEOUT_PAGE,
                cachedAtTimestamp = clock
            )
        )
        cache.write(
            document.copy(downloadUrl = "/scripts/filetransfer.exe?doc=9"),
            "%PDF-1.4 a real one".encodeToByteArray()
        )

        assertEquals(1, cache.purge())
        assertEquals(1, dao.size, "the real document stays")
    }

    @Test
    fun contentThatNoLongerMatchesItsHashIsNotServed() = runTest {
        // What a truncated write or a corrupted row would look like.
        dao.insert(
            CachedDocumentEntity(
                downloadUrl = document.downloadUrl,
                title = document.title,
                contentHash = Sha256.hex(content),
                content = "%PDF-1.4 something else".encodeToByteArray(),
                cachedAtTimestamp = clock
            )
        )

        assertNull(cache.read(document))
        assertEquals(0, dao.size, "a copy that cannot be trusted is dropped, not kept")
    }

    @Test
    fun rewritingTheSameContentDoesNotExtendTheFourWeeks() = runTest {
        // Otherwise re-downloading a document every week would keep it forever, and "maximum four
        // weeks" would only hold for documents nobody touches.
        cache.write(document, content)
        val firstSeen = clock

        clock += fourWeeks / 2
        cache.write(document, content)

        assertEquals(firstSeen, assertNotNull(cache.read(document)).cachedAtTimestamp)

        clock += fourWeeks / 2
        assertNull(cache.read(document), "four weeks after the first download, not the last")
    }

    @Test
    fun changedContentStartsItsOwnFourWeeks() = runTest {
        cache.write(document, content)
        clock += fourWeeks / 2

        val updated = "%PDF-1.4 a corrected certificate".encodeToByteArray()
        cache.write(document, updated)

        val cached = assertNotNull(cache.read(document))
        assertEquals(clock, cached.cachedAtTimestamp)
        assertEquals(Sha256.hex(updated), cached.contentHash)
        assertTrue(cached.contentHash != Sha256.hex(content))
    }

    @Test
    fun aDualisPageIsNotCached() = runTest {
        // The download endpoint answers an expired session with its timeout page and HTTP 200.
        // Caching that would serve Dualis' "please log in again" notice as the document for the
        // next four weeks.
        assertNull(cache.write(document, DownloadFixtures.SESSION_TIMEOUT_PAGE))

        assertEquals(0, dao.size)
    }

    @Test
    fun aPageCachedByAnOlderVersionIsDroppedOnRead() = runTest {
        val page = DownloadFixtures.SESSION_TIMEOUT_PAGE
        dao.insert(
            CachedDocumentEntity(
                downloadUrl = document.downloadUrl,
                title = document.title,
                contentHash = Sha256.hex(page),
                content = page,
                cachedAtTimestamp = clock
            )
        )

        assertNull(cache.read(document), "its hash is intact, but it is not a document")
        assertEquals(0, dao.size)
    }
}
