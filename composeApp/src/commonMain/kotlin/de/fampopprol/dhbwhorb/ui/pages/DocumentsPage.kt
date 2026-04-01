package de.fampopprol.dhbwhorb.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.ui.documents.components.DocumentCard
import de.fampopprol.dhbwhorb.ui.documents.viewModels.DocumentsViewModel
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavigationBar
import de.fampopprol.dhbwhorb.util.isMobilePlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsPage(
    viewModel: DocumentsViewModel,
    onNavigateToTimetable: () -> Unit,
    onNavigateToGrades: () -> Unit,
    onNavigateToSettings: () -> Unit,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // If we were previously blocked due to missing login and the app is now logged in, try again once
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && uiState.requiresLogin) {
            viewModel.loadDocuments()
        }
    }

    Scaffold(
        modifier = if (isMobilePlatform()) {
            modifier.statusBarsPadding()
        } else {
            modifier
        },
        bottomBar = {
            if (isLoggedIn) {
                BottomNavigationBar(
                    currentItem = BottomNavItem.DOCUMENTS,
                    onItemSelected = { item ->
                        when (item) {
                            BottomNavItem.TIMETABLE -> onNavigateToTimetable()
                            BottomNavItem.GRADES -> onNavigateToGrades()
                            BottomNavItem.DOCUMENTS -> { /* Already here */
                            }

                            BottomNavItem.SETTINGS -> onNavigateToSettings()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.requiresLogin && !isLoggedIn) {
                // Show login required message
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Please log in to view your documents",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        label = { Text("Search Documents") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Search"
                                    )
                                }
                            }
                        },
                        singleLine = true
                    )

                    if (uiState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refreshDocuments() },
                    ) {
                        if (uiState.documents.isEmpty() && uiState.searchQuery.isNotEmpty() && !uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No documents found matching your search.")
                            }
                        } else if (uiState.documents.isEmpty() && !uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No documents available.")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.documents) { document ->
                                    val documentKey = "${document.title}|${document.date}|${document.time}"
                                    DocumentCard(
                                        document = document,
                                        onDownloadClick = { viewModel.downloadAndOpenDocument(document) },
                                        onSaveToFiles = { doc -> viewModel.saveDocumentToFiles(doc) },
                                        isDownloading = uiState.isDownloading[documentKey] ?: false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

