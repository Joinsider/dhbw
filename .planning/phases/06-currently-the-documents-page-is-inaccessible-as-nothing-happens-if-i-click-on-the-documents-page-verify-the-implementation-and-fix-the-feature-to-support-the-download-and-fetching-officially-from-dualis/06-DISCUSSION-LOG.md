# Phase 6: Documents Page Fix & Dualis Integration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-04-01
**Phase:** 06-currently-the-documents-page-is-inaccessible-as-nothing-happens-if-i-click-on-the-documents-page-verify-the-implementation-and-fix-the-feature-to-support-the-download-and-fetching-officially-from-dualis
**Areas discussed:** Navigation integration, Dualis document fetching, Download & file handling, Error handling & testing

---

## Navigation Integration

| Option | Description | Selected |
|--------|-------------|----------|
| Add missing callback & verify all pages | Add onNavigateToDocuments to TimetablePage and audit all navigation handlers | ✓ |
| Just fix TimetablePage for now | Add callback to TimetablePage only, leave other verification for later | |

**User's choice:** Add missing callback & verify all pages
**Notes:** TimetablePage is currently missing the `onNavigateToDocuments` callback parameter, which breaks navigation when users click Documents from the Timetable page. Full audit will ensure consistency across TimetablePage, GradesPage, SettingsPage, and DocumentsPage.

---

## Dualis Document Fetching

| Option | Description | Selected |
|--------|-------------|----------|
| Verify it works - test with real Dualis data | Confirm the current parsing logic works correctly | ✓ |
| Known issue - fix the parsing | There's a problem with the current implementation | |

**User's choice:** Verify it works - test with real Dualis data
**Notes:** User noted "Can't be tested. As I don't know the current state" — unable to validate with real Dualis data currently. Phase 6 should verify implementation is correct and document testing procedure for future validation when real data access is available.

---

## Download & File Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Download & open immediately | Keep current behavior | |
| Download & save option | Let users choose to save to files app or open directly | ✓ |

**User's choice:** Download & save option
**Notes:** Enhance the current download functionality to provide users with a choice between saving to their files app or opening directly. This is an improvement over the current "always open immediately" behavior.

---

## Error Handling & Testing

| Option | Description | Selected |
|--------|-------------|----------|
| Enhanced errors + manual testing docs | Add specific error categories and document testing steps | |
| No tests | Keep current error handling as-is | ✓ |

**User's choice:** No tests
**Notes:** Keep the current error handling approach in DocumentsViewModel (error state tracking, loading indicators). No formal unit/integration tests required for this phase.

---

## Claude's Discretion

- Implementation details of the save/open file dialog UI
- Platform-specific file saving mechanics for Android, iOS, Desktop
- Logging verbosity and debugging approach for document fetching issues
