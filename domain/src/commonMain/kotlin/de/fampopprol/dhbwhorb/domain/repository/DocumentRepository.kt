/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument

/**
 * The documents Dualis offers for download (certificates, grade reports, payment information).
 */
interface DocumentRepository {

    suspend fun listDocuments(): Outcome<List<DualisDocument>>

    /**
     * Download one document's bytes.
     *
     * In demo mode the bytes are a generated PDF rather than a request to Dualis, so the demo
     * account reaches the viewer and the save dialog like any other.
     */
    suspend fun downloadDocument(document: DualisDocument): Outcome<ByteArray>
}
