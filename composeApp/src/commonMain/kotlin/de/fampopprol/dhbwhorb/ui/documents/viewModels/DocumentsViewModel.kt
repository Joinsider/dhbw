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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DocumentsViewModel(
    private val dualisDocumentService: DualisDocumentService?,
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
     * Retries a database or network operation for up to 5 seconds.
     * This ensures that if services are still initializing in the background,
     * the ViewModel will eventually get the data once they are ready.
     * 
     * Retry Strategy:
     * - Max attempts: 5
     * - Delay between attempts: 1 second
     * - Total duration: ~5 seconds
     */
    private suspend fun <T> getDataWithRetry(
        actionName: String,
        block: suspend () -> T?
    ): T? {
        val maxAttempts = 5
        val delayMillis = 1000L
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                // Check if services are ready (not null)
                // This is specifically for Task 1.2 and 2.3 requirements
                val result = block()
                if (result != null) return result
                
                Napier.d("Attempt $attempt for $actionName returned null (service might not be ready), retrying...", tag = TAG)
            } catch (e: Exception) {
                lastException = e
                Napier.w("Attempt $attempt for $actionName failed: ${e.message}", tag = TAG)
            }

            if (attempt < maxAttempts) {
                delay(delayMillis)
            }
        }

        Napier.e("All $maxAttempts attempts failed for $actionName. Last error: ${lastException?.message}", tag = TAG)
        return null
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
                // Use retry logic to wait for dualisDocumentService
                val service = getDataWithRetry("Documents Service Availability") {
                    dualisDocumentService
                }

                if (service == null) {
                    _isLoading.value = false
                    _error.value = "Documents service not available. Please try again later."
                    return@launch
                }

                // Check if we can attempt loading: authenticated, demo mode, or credentials available for re-auth
                if (!service.hasCredentialsOrSession()) {
                    Napier.d("Skipping loadDocuments: not authenticated and no stored credentials", tag = TAG)
                    _requiresLogin.value = true
                    _isLoading.value = false
                    return@launch
                }

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
                // Use retry logic to wait for dualisDocumentService
                val service = getDataWithRetry("Documents Service Availability (Refresh)") {
                    dualisDocumentService
                }

                if (service == null) {
                    _isRefreshing.value = false
                    _error.value = "Service not ready"
                    return@launch
                }

                if (!service.hasCredentialsOrSession()) {
                    Napier.d("Skipping refreshDocuments: login required", tag = TAG)
                    _requiresLogin.value = true
                    _isRefreshing.value = false
                    return@launch
                }

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
                // Use retry logic to wait for dualisDocumentService
                val service = getDataWithRetry("Documents Service Availability (Download)") {
                    dualisDocumentService
                }

                if (service == null) {
                    _error.value = "Service not ready"
                    return@launch
                }

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
                // Use retry logic to wait for dualisDocumentService
                val service = getDataWithRetry("Documents Service Availability (Save)") {
                    dualisDocumentService
                }

                if (service == null) {
                    _error.value = "Service not ready"
                    return@launch
                }

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
