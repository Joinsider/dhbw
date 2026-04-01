package de.fampopprol.dhbwhorb.ui.documents.viewModels

import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.util.openFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DocumentsViewModel(
    private val coroutineScope: CoroutineScope,
    // private val dualisDocumentService: DualisDocumentService // Assuming this will be injected
) {

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

    fun loadDocuments() {
        coroutineScope.launch {
            _isLoading.value = true
            // Dummy data for now
            _documents.value = listOf(
                DualisDocument("Exam Results", "2023-01-15", "10:00", "https://example.com/exam_results.pdf"),
                DualisDocument("Course Registration", "2023-02-01", "11:00", "https://example.com/course_registration.pdf"),
                DualisDocument("Library Fines", "2023-03-10", "12:00", "https://example.com/library_fines.pdf"),
                DualisDocument("Timetable", "2023-03-15", "13:00", "https://example.com/timetable.pdf")
            )
            _isLoading.value = false
        }
    }

    fun refreshDocuments() {
        coroutineScope.launch {
            _isRefreshing.value = true
            // TODO: Implement actual refresh logic
            _isRefreshing.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun downloadAndOpenDocument(document: DualisDocument) {
        coroutineScope.launch {
            _isDownloading.update { it + (document.title to true) }
            try {
                // val documentData = dualisDocumentService.downloadDocument(document.downloadUrl)
                // Dummy data
                val documentData = ByteArray(10) { it.toByte() } // Simulate some data
                openFile(documentData, document.title + ".pdf")
            } catch (e: Exception) {
                _error.value = "Failed to download document: ${e.message}"
            } finally {
                _isDownloading.update { it - document.title }
            }
        }
    }
}
