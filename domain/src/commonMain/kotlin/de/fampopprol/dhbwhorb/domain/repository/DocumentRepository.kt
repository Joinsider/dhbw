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
     * Fails with [de.fampopprol.dhbwhorb.core.error.AppError.Unsupported] in demo mode, where the
     * listed documents are fixtures with no file behind them.
     */
    suspend fun downloadDocument(document: DualisDocument): Outcome<ByteArray>
}
