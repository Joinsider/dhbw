/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.domain.repository.DocumentRepository

/** The documents Dualis currently offers. */
class ListDocuments(private val repository: DocumentRepository) {
    suspend operator fun invoke(): Outcome<List<DualisDocument>> = repository.listDocuments()
}

/**
 * Deletes cached documents once they are too old to keep.
 *
 * Dispatched at app start rather than only when the documents screen is opened: the age limit is
 * a deletion deadline, and a student who never opens that tab again must not keep a four-week-old
 * copy of their certificates on the device because of it.
 */
class PurgeExpiredDocuments(private val repository: DocumentRepository) {
    suspend operator fun invoke(): Int = repository.purgeExpiredDocuments()
}

/** Fetch one document's bytes, for opening or for saving. */
class DownloadDocument(private val repository: DocumentRepository) {
    suspend operator fun invoke(document: DualisDocument): Outcome<ByteArray> =
        repository.downloadDocument(document)
}
