/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.testutil.fakes.FakeDocumentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DocumentUseCasesTest {

    private val document = DualisDocument(
        title = "Bescheinigung",
        date = "01.01.2026",
        time = "12:00",
        downloadUrl = "https://dualis.example/download/1"
    )

    @Test
    fun listDocuments_delegatesToTheRepository() = runTest {
        val repo = FakeDocumentRepository(documents = Outcome.Ok(listOf(document)))

        val result = ListDocuments(repo)()

        assertEquals(listOf(document), assertIs<Outcome.Ok<List<DualisDocument>>>(result).value)
    }

    @Test
    fun purgeExpiredDocuments_returnsHowManyWereDeleted() = runTest {
        val repo = FakeDocumentRepository()
        repo.purged = 4

        val result = PurgeExpiredDocuments(repo)()

        assertEquals(4, result)
        assertEquals(1, repo.purges)
    }

    @Test
    fun downloadDocument_returnsTheBytesAndRecordsTheTitle() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val repo = FakeDocumentRepository(download = Outcome.Ok(bytes))

        val result = DownloadDocument(repo)(document)

        assertEquals(bytes, assertIs<Outcome.Ok<ByteArray>>(result).value)
        assertEquals(listOf("Bescheinigung"), repo.downloaded)
    }
}
