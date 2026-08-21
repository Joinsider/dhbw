/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.documents.viewModels

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.usecase.DownloadDocument
import de.fampopprol.dhbwhorb.domain.usecase.ListDocuments
import de.fampopprol.dhbwhorb.util.openFile
import de.fampopprol.dhbwhorb.util.saveFileWithDialog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DocumentsViewModel(
    private val listDocuments: ListDocuments,
    private val downloadDocument: DownloadDocument,
    private val sessionRepository: SessionRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "DocumentsViewModel"
    }

    private val _uiState = MutableStateFlow(DocumentsUiState())

    /**
     * One state object rather than seven flows combined on every emission.
     *
     * The filtered list is derived on write: the search query and the documents are the only
     * inputs, and both change here.
     */
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    /** Unfiltered, so narrowing the search and widening it again needs no reload. */
    private var allDocuments: List<DualisDocument> = emptyList()

    init {
        loadDocuments()
    }

    fun cleanup() {
        Napier.d("Cleaning up DocumentsViewModel", tag = TAG)
        coroutineScope.cancel()
    }

    fun loadDocuments() = fetch(isRefresh = false)

    fun refreshDocuments() = fetch(isRefresh = true)

    private fun fetch(isRefresh: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                error = null,
                requiresLogin = false
            )
        }

        coroutineScope.launch {
            if (!sessionRepository.canAuthenticate()) {
                Napier.d("Documents need a login first", tag = TAG)
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, requiresLogin = true)
                }
                return@launch
            }

            when (val result = listDocuments()) {
                is Outcome.Ok -> {
                    Napier.d("Loaded ${result.value.size} documents", tag = TAG)
                    allDocuments = result.value
                    _uiState.update {
                        it.copy(
                            documents = filtered(result.value, it.searchQuery),
                            isLoading = false,
                            isRefreshing = false,
                            error = null
                        )
                    }
                }
                is Outcome.Err -> {
                    Napier.e("Failed to load documents: ${result.error}", tag = TAG)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = result.error,
                            requiresLogin = result.error is AppError.NoCredentials
                        )
                    }
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(searchQuery = query, documents = filtered(allDocuments, query))
        }
    }

    fun downloadAndOpenDocument(document: DualisDocument) {
        withDocument(document) { bytes -> openFile(bytes, document.title + ".pdf") }
    }

    fun saveDocumentToFiles(document: DualisDocument) {
        withDocument(document) { bytes -> saveFileWithDialog(bytes, document.title + ".pdf") }
    }

    /** Download [document], show it as busy meanwhile, and hand the bytes to [consume]. */
    private fun withDocument(document: DualisDocument, consume: (ByteArray) -> Unit) {
        coroutineScope.launch {
            val key = document.key()
            _uiState.update { it.copy(isDownloading = it.isDownloading + (key to true)) }

            try {
                when (val result = downloadDocument(document)) {
                    is Outcome.Ok -> {
                        Napier.d("Downloaded ${document.title} (${result.value.size} bytes)", tag = TAG)
                        consume(result.value)
                        _uiState.update { it.copy(error = null) }
                    }
                    is Outcome.Err -> {
                        Napier.e("Failed to download ${document.title}: ${result.error}", tag = TAG)
                        _uiState.update { it.copy(error = result.error) }
                    }
                }
            } finally {
                _uiState.update { it.copy(isDownloading = it.isDownloading - key) }
            }
        }
    }

    private fun filtered(documents: List<DualisDocument>, query: String): List<DualisDocument> =
        if (query.isBlank()) documents
        else documents.filter { it.title.contains(query, ignoreCase = true) }

    /** Title alone is not unique — two payment notices can share it — so date and time join it. */
    private fun DualisDocument.key(): String = "$title|$date|$time"
}
