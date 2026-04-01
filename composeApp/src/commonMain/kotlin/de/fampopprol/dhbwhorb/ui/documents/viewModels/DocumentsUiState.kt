package de.fampopprol.dhbwhorb.ui.documents.viewModels

import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument

data class DocumentsUiState(
    val documents: List<DualisDocument> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val requiresLogin: Boolean = false,
    val searchQuery: String = "",
    val isDownloading: Map<String, Boolean> = emptyMap(),
)
