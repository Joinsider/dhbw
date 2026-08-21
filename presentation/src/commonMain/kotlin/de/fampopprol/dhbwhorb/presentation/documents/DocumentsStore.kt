/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.presentation.documents

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.usecase.DownloadDocument
import de.fampopprol.dhbwhorb.domain.usecase.ListDocuments
import de.fampopprol.dhbwhorb.presentation.store.BaseStore
import de.fampopprol.dhbwhorb.presentation.store.EffectScope
import kotlinx.coroutines.CoroutineScope

class DocumentsStore(
    private val listDocuments: ListDocuments,
    private val downloadDocument: DownloadDocument,
    private val sessionRepository: SessionRepository,
    scope: CoroutineScope
) : BaseStore<DocumentsState, DocumentsIntent, DocumentsMsg, DocumentsEffect>(
    initialState = DocumentsState(),
    scope = scope
) {

    /**
     * One list fetch at a time, and one download per document. Two different documents may
     * download at once; the same one twice may not.
     */
    override fun dedupeKey(intent: DocumentsIntent): Any? = when (intent) {
        DocumentsIntent.Load, DocumentsIntent.EnsureLoaded, DocumentsIntent.Refresh -> "list"
        is DocumentsIntent.Open -> "download-${intent.document.key()}"
        is DocumentsIntent.Save -> "download-${intent.document.key()}"
        is DocumentsIntent.SearchChanged -> null
    }

    override fun reduce(state: DocumentsState, msg: DocumentsMsg): DocumentsState = reduceDocuments(state, msg)

    override suspend fun EffectScope<DocumentsMsg, DocumentsEffect>.handle(
        intent: DocumentsIntent,
        state: DocumentsState
    ) {
        when (intent) {
            DocumentsIntent.Load -> fetch(isRefresh = false)
            DocumentsIntent.Refresh -> fetch(isRefresh = true)

            // Re-entering the screen must not refetch what the store already holds.
            DocumentsIntent.EnsureLoaded ->
                if (!state.hasLoaded && state.error == null) fetch(isRefresh = false)

            is DocumentsIntent.SearchChanged -> emit(DocumentsMsg.SearchChanged(intent.query))

            is DocumentsIntent.Open -> download(intent.document) { name, bytes ->
                DocumentsEffect.OpenFile(name, bytes)
            }

            is DocumentsIntent.Save -> download(intent.document) { name, bytes ->
                DocumentsEffect.SaveFile(name, bytes)
            }
        }
    }

    private suspend fun EffectScope<DocumentsMsg, DocumentsEffect>.fetch(isRefresh: Boolean) {
        emit(DocumentsMsg.LoadStarted(isRefresh))

        if (!sessionRepository.canAuthenticate()) {
            emit(DocumentsMsg.LoginRequired)
            emit(DocumentsMsg.LoadFinished)
            return
        }

        when (val result = listDocuments()) {
            is Outcome.Ok -> emit(DocumentsMsg.Loaded(result.value))
            is Outcome.Err -> emit(DocumentsMsg.Failed(result.error))
        }
        emit(DocumentsMsg.LoadFinished)
    }

    private suspend fun EffectScope<DocumentsMsg, DocumentsEffect>.download(
        document: DualisDocument,
        toEffect: (fileName: String, bytes: ByteArray) -> DocumentsEffect
    ) {
        val key = document.key()
        emit(DocumentsMsg.DownloadStarted(key))
        try {
            when (val result = downloadDocument(document)) {
                is Outcome.Ok -> send(toEffect("${document.title}.pdf", result.value))
                is Outcome.Err -> {
                    emit(DocumentsMsg.DownloadFailed(result.error))
                    send(DocumentsEffect.DownloadFailed(result.error))
                }
            }
        } finally {
            emit(DocumentsMsg.DownloadFinished(key))
        }
    }
}

/**
 * The documents state after [msg].
 *
 * Top-level and therefore unable to reach a store, a repository or a scope: the reducer's purity
 * is structural rather than a promise. Its tests call it directly, with no coroutines involved.
 */
fun reduceDocuments(state: DocumentsState, msg: DocumentsMsg): DocumentsState = when (msg) {
    is DocumentsMsg.LoadStarted -> state.copy(
        isLoading = !msg.isRefresh,
        isRefreshing = msg.isRefresh,
        error = null,
        requiresLogin = false
    )

    is DocumentsMsg.Loaded ->
        state.copy(allDocuments = msg.documents, error = null, hasLoaded = true)

    is DocumentsMsg.Failed -> state.copy(
        error = msg.error,
        requiresLogin = msg.error is AppError.NoCredentials
    )

    DocumentsMsg.LoginRequired -> state.copy(requiresLogin = true, error = null)

    DocumentsMsg.LoadFinished -> state.copy(isLoading = false, isRefreshing = false)

    is DocumentsMsg.SearchChanged -> state.copy(searchQuery = msg.query)

    is DocumentsMsg.DownloadStarted -> state.copy(downloading = state.downloading + msg.key)
    is DocumentsMsg.DownloadFinished -> state.copy(downloading = state.downloading - msg.key)
    is DocumentsMsg.DownloadFailed -> state.copy(error = msg.error)
}
