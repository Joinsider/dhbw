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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.fampopprol.dhbwhorb.ui.documents.components.DocumentCard
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsEffect
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsIntent
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsState
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsStore
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavItem
import de.fampopprol.dhbwhorb.ui.navigation.BottomNavigationBar
import de.fampopprol.dhbwhorb.util.isMobilePlatform
import org.koin.compose.koinInject
import de.fampopprol.dhbwhorb.ui.components.DocumentCardSkeleton
import de.fampopprol.dhbwhorb.ui.store.HandleEffects
import de.fampopprol.dhbwhorb.ui.store.collectState
import de.fampopprol.dhbwhorb.util.openFile
import de.fampopprol.dhbwhorb.util.saveFileWithDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DocumentsPage(
    onNavigate: (BottomNavItem) -> Unit = {},
    modifier: Modifier = Modifier,
    store: DocumentsStore = koinInject()
) {

    val uiState by store.collectState()

    // Opening and saving a file are platform calls, so the store asks for them by effect instead
    // of making them itself — which is what keeps :presentation free of platform APIs.
    store.HandleEffects { effect ->
        when (effect) {
            is DocumentsEffect.OpenFile -> openFile(effect.bytes, effect.fileName)
            is DocumentsEffect.SaveFile -> saveFileWithDialog(effect.bytes, effect.fileName)
            is DocumentsEffect.DownloadFailed -> Unit // already reflected in the state
        }
    }

    // The store outlives the composition, so this loads once and costs nothing on a tab switch.
    LaunchedEffect(Unit) { store.dispatch(DocumentsIntent.EnsureLoaded) }

    Scaffold(
        modifier = if (isMobilePlatform()) {
            modifier.statusBarsPadding()
        } else {
            modifier
        },
        bottomBar = {
            BottomNavigationBar(
                currentItem = BottomNavItem.DOCUMENTS,
                onItemSelected = onNavigate
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.requiresLogin) {
                LoginRequiredMessage()
            } else {
                DocumentsContent(uiState = uiState, store = store)
            }
        }
    }
}

@Composable
private fun LoginRequiredMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Please log in to view your documents",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
internal fun DocumentsContent(uiState: DocumentsState, store: DocumentsStore) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        DocumentsSearchField(uiState = uiState, store = store)

        if (uiState.isLoading && uiState.documents.isEmpty()) {
            DocumentsSkeletonList()
        } else {
            DocumentsResults(uiState = uiState, store = store)
        }
    }
}

@Composable
private fun DocumentsSearchField(uiState: DocumentsState, store: DocumentsStore) {
    OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = { store.dispatch(DocumentsIntent.SearchChanged(it)) },
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
                IconButton(onClick = { store.dispatch(DocumentsIntent.SearchChanged("")) }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Search"
                    )
                }
            }
        },
        singleLine = true
    )
}

@Composable
private fun DocumentsSkeletonList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(6) {
            DocumentCardSkeleton()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsResults(uiState: DocumentsState, store: DocumentsStore) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { store.dispatch(DocumentsIntent.Refresh) },
    ) {
        when {
            uiState.documents.isEmpty() && uiState.searchQuery.isNotEmpty() && !uiState.isLoading ->
                EmptyDocumentsMessage("No documents found matching your search.")

            uiState.documents.isEmpty() && !uiState.isLoading ->
                EmptyDocumentsMessage("No documents available.")

            else -> DocumentsList(uiState = uiState, store = store)
        }
    }
}

@Composable
private fun EmptyDocumentsMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text)
    }
}

@Composable
internal fun DocumentsList(uiState: DocumentsState, store: DocumentsStore) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(uiState.documents) { document ->
            DocumentCard(
                document = document,
                onDownloadClick = {
                    store.dispatch(DocumentsIntent.Open(document))
                },
                onSaveToFiles = { doc ->
                    store.dispatch(DocumentsIntent.Save(doc))
                },
                isDownloading = uiState.isDownloading(document)
            )
        }
    }
}

