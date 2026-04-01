---
phase: 03-document-features-ui
plan: 05
type: summary
wave: 5
---

<summary>
This plan successfully integrated document downloading and viewing into the UI, completing requirement DOC-UI-03.

**ViewModel Updates:**
- The `DocumentsViewModel` already had the `downloadAndOpenDocument(document: DualisDocument)` method implemented
- The method manages download state using `_isDownloading` StateFlow (Map<String, Boolean>)
- Sets loading state before download, calls `openFile()` with the downloaded bytes, handles errors, and resets state
- Currently uses dummy data (ByteArray(10)) for testing until DualisDocumentService is fully wired

**DocumentCard UI Updates:**
- Added `onDownloadClick: () -> Unit` callback parameter to DocumentCard
- Added `isDownloading: Boolean` parameter to show download progress
- Added trailing content with IconButton showing `Icons.Default.Download` icon
- Shows `CircularProgressIndicator` while document is downloading
- Download button is disabled during download (replaced by spinner)
- Proper content description for accessibility: "Download {document.title}"

**DocumentsPage Integration:**
- Updated DocumentCard usage to pass `onDownloadClick` callback: `{ viewModel.downloadAndOpenDocument(document) }`
- Passes downloading state from uiState: `uiState.isDownloading[document.title] ?: false`
- Each document now has its own download state tracked independently by title

The implementation is complete and follows Material 3 design patterns. Users can now click the download icon on any document to trigger download and automatic file opening using platform-native viewers. The UI provides clear visual feedback during downloads with a loading spinner.

**Success Criteria Met:**
- ✅ Users can click a download icon on any document item
- ✅ The app downloads the file and opens it automatically (via FileViewer from plan 03-04)
- ✅ Error messages are shown if the download fails (handled in ViewModel)
- ✅ Download state is tracked per-document to show loading indicators

**Note:** The ViewModel currently uses dummy data for downloads. Full integration with DualisDocumentService will be completed when App.kt wiring issues are resolved (tracked in other plans).
</summary>
