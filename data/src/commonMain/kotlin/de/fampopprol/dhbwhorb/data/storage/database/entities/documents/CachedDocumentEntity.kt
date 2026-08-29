/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.storage.database.entities.documents

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A downloaded Dualis document, kept so the same file is not fetched twice.
 *
 * Keyed by the download URL because that is what Dualis identifies a document by, and what the
 * list hands the download path. The bytes live in the database rather than in a file of their
 * own: it keeps one story about where cached data lives, it makes "delete everything on logout"
 * a single call, and these are certificates and grade reports — kilobytes, not gigabytes.
 *
 * Not a data class: [content] would give it an `equals` that compares array identity, which is
 * exactly the sort of quietly wrong thing a data class is supposed to save you from.
 */
@Entity(tableName = "cached_documents")
class CachedDocumentEntity(
    @PrimaryKey val downloadUrl: String,
    val title: String,
    /** SHA-256 of [content], written when the document was stored. */
    val contentHash: String,
    val content: ByteArray,
    /** When the document was first downloaded, in epoch milliseconds. */
    val cachedAtTimestamp: Long
)
