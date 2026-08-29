# Research: Phase 3 - Document Features & UI

## Domain: Document Management & User Interface
**Status:** In Progress
**Confidence:** MEDIUM/HIGH

## Objectives
- Implement a user-friendly "Documents" screen in Compose Multiplatform.
- Enable document searching and filtering.
- Implement document download and platform-native viewing.
- Integrate with existing `DualisDocumentService`.

## Current State Analysis
- **Data Layer:** `DualisDocumentService.kt` is implemented and verified with tests. It can fetch a list of `DualisDocument` objects.
- **UI Architecture:** The app uses a simple `AppScreen` enum for navigation in `App.kt` and a `BottomNavigationBar`.
- **Styling:** Material 3 is used throughout the app with a custom theme.

## Technical Gaps & Strategies

### 1. Navigation Integration
- **Task:** Add `DOCUMENTS` to `AppScreen` and `BottomNavItem`.
- **Task:** Update `App.kt` to handle the new screen and provide the `DocumentsViewModel`.

### 2. UI Implementation
- **Screen:** `DocumentsPage` should feature a `LazyColumn` for document cards.
- **Search:** Use a `DockedSearchBar` or a simple `TextField` for filtering the list.
- **Feedback:** Implement `PullToRefresh` (consistent with `GradesPage`).
- **Cards:** Create a `DocumentCard` component showing title, date, and download button.

### 3. Document Download & Viewing
- **Download:** Use `DualisApiClient` (which uses Ktor) to fetch the file bytes.
- **Storage:** Save to a temporary file.
- **Viewing:** Implement `FileViewer` using `expect`/`actual` pattern.
  - **Android:** Use `FileProvider` and `Intent.ACTION_VIEW`.
  - **iOS:** Use `UIDocumentInteractionController` or `QLPreviewController`.
  - **Desktop:** Use `java.awt.Desktop.getDesktop().open(file)`.

### 4. Dependency Considerations
- **Okio:** Might be useful for multiplatform file I/O.
- **Ktor:** Already present, can be used for downloading bytes.

## Proposed Components
- `DocumentsViewModel`: Manages loading state, document list, and search query.
- `DocumentsPage`: Main UI entry point.
- `DocumentCard`: Item representation.
- `FileViewer` (expect/actual): For opening the downloaded PDF.

## Pitfalls to Watch Out For
- **Session Expiration:** Document downloads might fail if the session expires. `DualisDocumentService` handles this for list fetching, but download logic needs similar care.
- **Permissions (Android):** Managing storage permissions and `FileProvider` configuration.
- **File Names:** Dualis might provide cryptic filenames; we should try to use the document title as the filename.

## Next Steps
1. Create `03-01-PLAN.md` for UI and Navigation foundation.
2. Create `03-02-PLAN.md` for Download and Viewing implementation.
