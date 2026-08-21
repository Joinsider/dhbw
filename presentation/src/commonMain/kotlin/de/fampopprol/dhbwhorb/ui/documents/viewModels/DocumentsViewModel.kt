package de.fampopprol.dhbwhorb.ui.documents.viewModels

import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisDocumentService
import de.fampopprol.dhbwhorb.util.openFile
import de.fampopprol.dhbwhorb.util.saveFileWithDialog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DocumentsViewModel(
    private val dualisDocumentService: DualisDocumentService,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "DocumentsViewModel"
    }

    private val _documents = MutableStateFlow<List<DualisDocument>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _requiresLogin = MutableStateFlow(false)
    private val _isDownloading = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val uiState: StateFlow<DocumentsUiState> = combine(
        _documents,
        _searchQuery,
        _isLoading,
        _isRefreshing,
        _error,
        _requiresLogin,
        _isDownloading
    ) { flows ->
        val documents = flows[0] as List<DualisDocument>
        val searchQuery = flows[1] as String
        val isLoading = flows[2] as Boolean
        val isRefreshing = flows[3] as Boolean
        val error = flows[4] as String?
        val requiresLogin = flows[5] as Boolean
        val isDownloading = flows[6] as Map<String, Boolean>
        
        val filteredDocuments = if (searchQuery.isBlank()) {
            documents
        } else {
            documents.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
        DocumentsUiState(
            documents = filteredDocuments,
            searchQuery = searchQuery,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            error = error,
            requiresLogin = requiresLogin,
            isDownloading = isDownloading
        )
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DocumentsUiState()
    )

    init {
        loadDocuments()
    }


    /**
     * Cleanup resources and cancel coroutine scope.
     */
    fun cleanup() {
        Napier.d("Cleaning up DocumentsViewModel", tag = TAG)
        coroutineScope.cancel()
    }

    fun loadDocuments() {
        _isLoading.value = true
        _error.value = null
        _requiresLogin.value = false

        coroutineScope.launch {
            try {
                val service = dualisDocumentService
                if (!service.hasCredentialsOrSession()) {
                    Napier.d("Skipping loadDocuments: not authenticated and no stored credentials", tag = TAG)
                    _requiresLogin.value = true
                    _isLoading.value = false
                } else {
                    Napier.d("Loading documents from Dualis...", tag = TAG)
                    val result = service.fetchDocuments()

                    result.onSuccess { documents ->
                        Napier.d("Loaded ${documents.size} documents", tag = TAG)
                        _documents.value = documents
                        _isLoading.value = false
                        _error.value = null
                    }.onFailure { e ->
                        Napier.e("Failed to load documents: ${e.message}", e, tag = TAG)
                        _isLoading.value = false
                        _error.value = "Failed to load documents: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error loading documents: ${e.message}", e, tag = TAG)
                _isLoading.value = false
                _error.value = "Error: ${e.message}"
            }
        }
    }

    fun refreshDocuments() {
        _isRefreshing.value = true
        _error.value = null

        coroutineScope.launch {
            try {
                val service = dualisDocumentService
                if (!service.hasCredentialsOrSession()) {
                    Napier.d("Skipping refreshDocuments: login required", tag = TAG)
                    _requiresLogin.value = true
                    _isRefreshing.value = false
                } else {
                    Napier.d("Refreshing documents from Dualis (pull-to-refresh)...", tag = TAG)
                    val result = service.fetchDocuments()

                    result.onSuccess { documents ->
                        Napier.d("Refreshed ${documents.size} documents", tag = TAG)
                        _documents.value = documents
                        _isRefreshing.value = false
                        _error.value = null
                    }.onFailure { e ->
                        Napier.e("Failed to refresh documents: ${e.message}", e, tag = TAG)
                        _isRefreshing.value = false
                        _error.value = "Failed to refresh documents: ${e.message}"
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error refreshing documents: ${e.message}", e, tag = TAG)
                _isRefreshing.value = false
                _error.value = "Error: ${e.message}"
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun downloadAndOpenDocument(document: DualisDocument) {
        coroutineScope.launch {
            val documentKey = getDocumentKey(document)
            _isDownloading.update { it + (documentKey to true) }
            try {
                val service = dualisDocumentService
                Napier.d("Downloading document: ${document.title}", tag = TAG)
                val result = service.downloadDocument(document.downloadUrl)

                result.onSuccess { documentData ->
                    Napier.d("Downloaded document successfully: ${document.title}, size: ${documentData.size} bytes", tag = TAG)
                    openFile(documentData, document.title + ".pdf")
                    _error.value = null
                }.onFailure { e ->
                    Napier.e("Failed to download document: ${e.message}", e, tag = TAG)
                    _error.value = "Failed to download document: ${e.message}"
                }
            } catch (e: Exception) {
                Napier.e("Error downloading document: ${e.message}", e, tag = TAG)
                _error.value = "Error: ${e.message}"
            } finally {
                _isDownloading.update { it - documentKey }
            }
        }
    }

    fun saveDocumentToFiles(document: DualisDocument) {
        coroutineScope.launch {
            val documentKey = getDocumentKey(document)
            _isDownloading.update { it + (documentKey to true) }
            try {
                val service = dualisDocumentService
                Napier.d("Saving document to files: ${document.title}", tag = TAG)
                val result = service.downloadDocument(document.downloadUrl)

                result.onSuccess { documentData ->
                    Napier.d("Downloaded document successfully: ${document.title}, size: ${documentData.size} bytes", tag = TAG)
                    // For save-to-files, we use saveFileWithDialog to prompt for save location
                    saveFileWithDialog(documentData, document.title + ".pdf")
                    _error.value = null
                }.onFailure { e ->
                    Napier.e("Failed to download document for saving: ${e.message}", e, tag = TAG)
                    _error.value = "Failed to save document: ${e.message}"
                }
            } catch (e: Exception) {
                Napier.e("Error saving document: ${e.message}", e, tag = TAG)
                _error.value = "Error: ${e.message}"
            } finally {
                _isDownloading.update { it - documentKey }
            }
        }
    }

    private fun getDocumentKey(document: DualisDocument): String {
        // Create a unique key using title, date, and time to handle multiple documents with same title
        return "${document.title}|${document.date}|${document.time}"
    }
}
