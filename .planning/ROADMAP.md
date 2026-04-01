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
**Plans:** 2 plans
- [ ] 03-01-PLAN.md — Navigation Foundation & UI Components (Wave 1)
- [ ] 03-02-PLAN.md — Documents Page & Integration (Wave 2)

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
