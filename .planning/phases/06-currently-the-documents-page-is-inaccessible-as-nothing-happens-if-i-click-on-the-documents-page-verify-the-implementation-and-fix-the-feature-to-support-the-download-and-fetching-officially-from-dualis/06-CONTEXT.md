# Phase 6: Documents Page Fix & Dualis Integration - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix the inaccessible documents page by completing navigation integration, verifying Dualis document fetching works correctly, and enhancing download functionality with save-to-files option. All platforms (Android, iOS, Desktop) must be functional.

</domain>

<decisions>
## Implementation Decisions

### Navigation Integration
- **D-01:** Add `onNavigateToDocuments` callback parameter to TimetablePage (currently missing, breaks navigation from Timetable → Documents)
- **D-02:** Audit all page navigation handlers (TimetablePage, GradesPage, SettingsPage, DocumentsPage) to ensure consistent navigation patterns

### Dualis Document Fetching
- **D-03:** Verify DocumentParser and DualisDocumentService implementation is correct for extracting documents from real Dualis website
- **D-04:** Cannot test with real Dualis data currently; document the manual testing procedure for when real data access is available

### Download & File Handling
- **D-05:** Enhance download functionality to provide users with a choice: save to files app OR open directly (currently only opens immediately)
- **D-06:** Keep current behavior of downloading and opening PDFs; file handling via platform-specific `openFile` utility is acceptable

### Error Handling & Testing
- **D-07:** Keep current error message approach; no formal unit/integration tests required for this phase
- **D-08:** Current error state tracking in DocumentsViewModel (isLoading, isRefreshing, error) provides adequate feedback

### Claude's Discretion
- Specific error message wording and UI presentation for the save/open dialog
- Implementation details of platform-specific file save mechanics
- Logging verbosity for debugging parsing/download issues

</decisions>

<canonical_refs>
## Canonical References

**Key implementation files:**
- `.planning/REQUIREMENTS.md` §1.2 — Document Management requirements (scraping, loading, download)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/pages/DocumentsPage.kt` — Documents UI page
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt` — UI state management
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/DualisDocumentService.kt` — Document fetching service
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/parser/DocumentParser.kt` — HTML parsing logic
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt` — Main app navigation and screen routing

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **DocumentsPage:** Complete UI with search, loading indicators, pull-to-refresh, and document cards
- **DocumentsViewModel:** State management with proper error handling, loading states, and filtering
- **DocumentCard:** Reusable component for displaying individual documents
- **BottomNavigationBar:** Already includes DOCUMENTS as a navigation item (4 items total)

### Established Patterns
- **State Management:** Uses StateFlow and combine for reactive UI updates (consistent with GradesViewModel)
- **Navigation:** Page-to-page callbacks in composable parameters (e.g., `onNavigateToGrades`)
- **Error Handling:** Stores error messages in StateFlow for display in UI
- **Async Operations:** Uses coroutineScope.launch for non-blocking network/file operations

### Integration Points
- App.kt routes DocumentsPage based on AppScreen.DOCUMENTS state
- BottomNavigationBar triggers navigation callbacks from within pages
- DualisDocumentService is injected into DocumentsViewModel for dependency injection
- Platform-specific file opening via `openFile` utility function

</code_context>

<specifics>
## Specific Ideas

- Phase 5 verified parsing logic implementation exists; Phase 6 focuses on making it actually work end-to-end
- Documents feature is critical for feature parity with official Dualis app
- User mentioned documents page is "inaccessible" — primary blocker is navigation, not UI itself

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 06-currently-the-documents-page-is-inaccessible-as-nothing-happens-if-i-click-on-the-documents-page-verify-the-implementation-and-fix-the-feature-to-support-the-download-and-fetching-officially-from-dualis*
*Context gathered: 2026-04-01*
