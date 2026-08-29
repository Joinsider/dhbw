# Phase 7 Context: Loading Animation & Desktop/Multiplatform Document Saving

## 1. Objective
Enhance the "Documents" page with a clear loading state and implement a platform-native "Save As" functionality across all platforms (prioritizing Desktop verification as per the roadmap).

## 2. Decisions

### D-01: Loading Animation Implementation
- **Component:** Use `androidx.compose.material3.LoadingIndicator` (consistent with `GradesPage` and `TimetablePage`).
- **Placement:** Centered overlay on the `DocumentsPage`. It should be shown when `uiState.isLoading` is true, particularly replacing the empty list or empty-state message.

### D-02: Multiplatform "Save As" approach
- **Functionality:** Introduce a new `expect fun saveFileWithDialog(byteArray: ByteArray, fileName: String)` in `de.fampopprol.dhbwhorb.util.FileViewer.kt`.
- **Implementation Strategy:**
    - **Desktop:** Use `java.awt.FileDialog` (AWT) for a native-looking system dialog on macOS, Windows, and Linux.
    - **Android:** Use `Intent.ACTION_CREATE_DOCUMENT` (with `ActivityResultLauncher` if possible, or via a shared context/callback mechanism).
    - **iOS:** Use `UIDocumentPickerViewController` with `UTType.pdf` or equivalent to allow the user to choose a destination in the Files app.
- **Timing:** Show the dialog **AFTER** the document is successfully downloaded to memory. This matches the current `DocumentsViewModel` pattern and is acceptable for the small PDF files typically retrieved from Dualis.

### D-03: UI Integration
- **Button:** Utilize the existing "Save to Files" option in the `DocumentCard`'s dropdown menu.
- **ViewModel:** Update `DocumentsViewModel.saveDocumentToFiles` to call the new `saveFileWithDialog` instead of the generic `openFile`.

### D-04: Desktop Verification
- **Primary Platform:** macOS (development environment).
- **Secondary Platforms:** Windows and Linux (verifying AWT behavior).

## 3. Implementation Details

### FileViewer Utility Extension
- `expect fun saveFileWithDialog(byteArray: ByteArray, fileName: String)`
- `actual fun saveFileWithDialog(...)` on each platform.

### DocumentsPage Update
```kotlin
if (uiState.isLoading) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}
```

## 4. Risks & Constraints
- **Android Context:** Triggering a "Save As" intent requires an Activity context and potentially a callback. Need to ensure the `FileViewer` utility has access to these or uses a delegated approach.
- **AWT on Desktop:** Ensure `java.awt.FileDialog` doesn't block the Compose UI thread in a way that causes issues (use appropriate dispatchers if needed).
- **iOS File Export:** Requires proper handling of `UIDocumentPickerViewController` and its delegates.
