# Roadmap: DHBW Dualis KMP

## Phase 1: Secure Connectivity Hotfix (Critical)
Goal: Fix the login issues caused by missing SSL root trust.
**Plans:** 3 plans
- [ ] 01-01-PLAN.md — Resource Foundation & Android Config (Wave 1)
- [ ] 01-02-PLAN.md — Platform-Specific Trust Configuration (expect/actual) (Wave 2)
- [ ] 01-03-PLAN.md — Integration & Verification (Wave 3)

**Requirements:**
- [ ] **SSL-01**: Bundle HARICA TLS RSA Root CA 2021 as a resource.
- [ ] **SSL-02**: Implement `network_security_config.xml` for Android.
- [ ] **SSL-03**: Configure `HttpClient` for Android (OkHttp engine) to trust the bundled CA.
- [ ] **SSL-04**: Configure `HttpClient` for iOS (Darwin engine) to trust the bundled CA.
- [ ] **SSL-05**: Configure `HttpClient` for Desktop (Java/CIO engine) to trust the bundled CA.
- [ ] **SSL-06**: Verify login functionality on Android < 14, iOS, and Desktop.

## Phase 2: Document Scraping Foundation (High)
Goal: Extend the data layer to support document retrieval.
**Plans:** 2 plans
- [x] 02-01-PLAN.md — Document Model & Core Parsing (Wave 1)
- [x] 02-02-PLAN.md — Document Service & Integration (Wave 2)

**Requirements:**
- [x] **DOC-01**: Research and map the HTML structure for "Documents" in Dualis.
- [x] **DOC-02**: Create `DocumentParser.kt` based on the mapping.
- [x] **DOC-03**: Implement `fetchDocuments()` in the shared data layer.
- [x] **DOC-04**: Add unit tests for `DocumentParser.kt` with sample HTML.

## Phase 3: Document Features & UI (High)
Goal: Provide a user-friendly interface for document management.
**Plans:** 5 plans
- [ ] 03-01-PLAN.md — Navigation Foundation & UI Components (Wave 1)
- [ ] 03-02-PLAN.md — Documents Page & Integration (Wave 2)
- [ ] 03-03-PLAN.md — Search Functionality (Wave 3)
- [ ] 03-04-PLAN.md — Data & Platform Layer for Downloads (Wave 4)
- [ ] 03-05-PLAN.md — UI Integration for Downloads (Wave 5)

**Requirements:**
- [ ] **DOC-UI-01**: Create a "Documents" screen using Compose Multiplatform.
- [ ] **DOC-UI-02**: Implement search functionality for the document list.
- [ ] **DOC-UI-03**: Implement document download and platform-native file viewing.
- [ ] **DOC-UI-04**: Add loading indicators and error states for document fetching.

## Phase 4: Final Validation & Refinement (Medium)
Goal: Ensure overall app stability and polish.
- [ ] **VAL-01**: Regression testing for grades and timetable features.
- [ ] **VAL-02**: Optimize scraping logic and network timeouts.
- [ ] **VAL-03**: Clean up technical debt in parsers and services.
- [ ] **VAL-04**: Update documentation and prepare for release.

### Phase 5: Recheck implementation of documents and add actual fetching from dualis. Use the example folder for a tutorial on how to use dualis. If unsure ask me questions

**Goal:** [To be planned]
**Requirements**: TBD
**Depends on:** Phase 4
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 5 to break down)

### Phase 6: Documents Page Fix & Dualis Integration

**Goal:** Fix the inaccessible documents page by completing navigation integration, verifying Dualis document fetching works correctly, and enhancing download functionality with save-to-files option. All platforms (Android, iOS, Desktop) must be functional.

**Requirements:**
- [ ] **DOC-UI-01**: Create a "Documents" screen using Compose Multiplatform (verify implementation complete)
- [ ] **DOC-UI-02**: Implement search functionality for the document list (verify implementation complete)
- [ ] **DOC-UI-03**: Implement document download and platform-native file viewing (enhance with save-to-files option)
- [ ] **DOC-UI-04**: Add loading indicators and error states for document fetching (verify adequate)

**Depends on:** Phase 5

**Plans:** 1 comprehensive plan (4 waves, 13 tasks)
- [x] 06-PLAN.md — Complete phase plan (created 2026-04-01)

**Wave 1:** Fix TimetablePage navigation (blockers)
- [ ] 06-01: Add onNavigateToDocuments callback to TimetablePage and wire in App.kt

**Wave 2:** Audit navigation consistency (parallel)
- [ ] 06-02: Audit all page navigation parameters (TimetablePage, GradesPage, SettingsPage, DocumentsPage)

**Wave 3:** Verify service layer (parallel)
- [ ] 06-03: Code review DocumentParser and DualisDocumentService + create manual testing guide

**Wave 4:** Enhance download UI (sequential)
- [ ] 06-04: Add save-to-files option with new button in DocumentCard and ViewModel function

**Phase Decisions:**
- D-01: Add onNavigateToDocuments callback to TimetablePage
- D-02: Audit all page navigation handlers for consistency
- D-03: Verify DocumentParser and DualisDocumentService implementation
- D-04: Document manual testing procedure for real Dualis data
- D-05: Enhance download to offer save-to-files choice
- D-06: Keep existing openFile behavior for downloads
- D-07: Keep current error message approach (no formal tests)
- D-08: Current error state tracking adequate

**Status:** Ready for execution via `/gsd:execute-phase 06`
