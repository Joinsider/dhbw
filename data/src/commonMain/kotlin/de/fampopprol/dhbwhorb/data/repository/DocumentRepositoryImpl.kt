/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisDocumentService
import de.fampopprol.dhbwhorb.domain.repository.DocumentRepository

/** [DocumentRepository] on top of [DualisDocumentService]. */
class DocumentRepositoryImpl(
    private val documentService: DualisDocumentService
) : DocumentRepository {

    override suspend fun listDocuments(): Outcome<List<DualisDocument>> =
        documentService.fetchDocuments()

    override suspend fun downloadDocument(document: DualisDocument): Outcome<ByteArray> =
        documentService.downloadDocument(document.downloadUrl)
}
