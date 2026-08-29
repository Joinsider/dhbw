/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.documents

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument

data class DocumentsState(
    /** Everything Dualis listed, unfiltered, so narrowing the search needs no reload. */
    val allDocuments: List<DualisDocument> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Keys of the documents currently downloading. */
    val downloading: Set<String> = emptySet(),
    val error: AppError? = null,
    val requiresLogin: Boolean = false,
    /**
     * Whether a load has ever come back.
     *
     * The screen re-enters the composition on every tab switch, so it asks for a load only when
     * one is needed. Without this the store survives the switch but the page refetches anyway.
     */
    val hasLoaded: Boolean = false
) {
    /** What the list shows. Derived, so it can never drift from the query. */
    val documents: List<DualisDocument>
        get() = if (searchQuery.isBlank()) {
            allDocuments
        } else {
            allDocuments.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

    fun isDownloading(document: DualisDocument): Boolean = document.key() in downloading
}

/** Title alone is not unique — two payment notices share it — so date and time join it. */
fun DualisDocument.key(): String = "$title|$date|$time"

sealed interface DocumentsIntent {
    data object Load : DocumentsIntent

    /** Load only if nothing has been loaded yet. Dispatched when the screen appears. */
    data object EnsureLoaded : DocumentsIntent
    data object Refresh : DocumentsIntent
    data class SearchChanged(val query: String) : DocumentsIntent

    /** Download and hand to the system viewer. */
    data class Open(val document: DualisDocument) : DocumentsIntent

    /** Download and ask the user where to keep it. */
    data class Save(val document: DualisDocument) : DocumentsIntent
}

sealed interface DocumentsMsg {
    data class LoadStarted(val isRefresh: Boolean) : DocumentsMsg
    data class Loaded(val documents: List<DualisDocument>) : DocumentsMsg
    data class Failed(val error: AppError) : DocumentsMsg
    data object LoginRequired : DocumentsMsg
    data object LoadFinished : DocumentsMsg

    data class SearchChanged(val query: String) : DocumentsMsg
    data class DownloadStarted(val key: String) : DocumentsMsg
    data class DownloadFinished(val key: String) : DocumentsMsg
    data class DownloadFailed(val error: AppError) : DocumentsMsg
}

sealed interface DocumentsEffect {
    /**
     * The bytes are ready.
     *
     * Opening a file and showing a save dialog are platform calls, so they leave the store as
     * effects instead of being made from inside it — which is what lets `:presentation` stay free
     * of platform APIs and reachable from Swift.
     */
    data class OpenFile(val fileName: String, val bytes: ByteArray) : DocumentsEffect {
        override fun equals(other: Any?): Boolean =
            this === other || (other is OpenFile && fileName == other.fileName && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
    }

    data class SaveFile(val fileName: String, val bytes: ByteArray) : DocumentsEffect {
        override fun equals(other: Any?): Boolean =
            this === other || (other is SaveFile && fileName == other.fileName && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
    }

    data class DownloadFailed(val error: AppError) : DocumentsEffect
}
