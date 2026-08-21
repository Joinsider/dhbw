/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.documents.viewModels

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument

data class DocumentsUiState(
    val documents: List<DualisDocument> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** The classified reason the documents could not be loaded, for the UI to phrase. */
    val error: AppError? = null,
    val requiresLogin: Boolean = false,
    val searchQuery: String = "",
    val isDownloading: Map<String, Boolean> = emptyMap(),
)
