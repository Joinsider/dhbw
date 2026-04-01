# Requirements: DHBW Dualis KMP

## 1. Functional Requirements

### 1.1 Secure Login & Connectivity (Critical)
- **SSL Trust:** Fix `CertPathValidatorException` for Android users < 14 and verify trust on older iOS/Desktop versions.
- **Login Persistence:** Maintain session-bound authentication tokens across requests.
- **Error Handling:** Provide clear feedback for login failures (invalid credentials, network issues, trust failures).

### 1.2 Document Management (High)
- **Scraping:** Extract links for documents from `dualis.dhbw.de` (Immatrikulationsbescheinigung, Semester payment notices, etc.).
- **Search:** Allow users to search through the list of available documents.
- **Loading:** Download and potentially preview documents within the app.
- **Offline Support:** (Future) Cache downloaded documents for offline viewing.

### 1.3 Grades & Timetable (Core - Existing)
- **Completeness:** Ensure all grade and timetable data is correctly scraped and displayed.

## 2. Non-Functional Requirements

### 2.1 Security
- **Certificate Pinning/Bundling:** Use the HARICA 2021 Root CA to establish trust for `dualis.dhbw.de`.
- **Credential Safety:** Never log or store credentials insecurely.

### 2.2 Performance
- **Scraping Efficiency:** Minimize unnecessary network requests and ensure parsing is non-blocking.
- **UI Responsiveness:** Maintain a smooth UI during network activity and data processing.

### 2.3 Maintainability
- **Code Consistency:** Use the established Regex-based parsing patterns for any new scrapers.
- **Platform Specifics:** Isolate engine-specific network configurations using KMP patterns (`expect`/`actual`).

## 3. Constraints
- **Unstable API:** Dualis can change its HTML structure at any time, requiring parser updates.
- **No Official API:** All data must be fetched through scraping and session handling.
- **Platform Support:** Must work across Android, iOS, and Desktop (Compose Multiplatform).
