# Codebase Concerns

**Analysis Date:** 2024-11-20

## Tech Debt

**HTML Scraping with Regex:**
- Issue: The app relies on regular expressions for parsing Dualis HTML instead of a robust DOM-based parser. This is highly fragile and prone to breaking on minor UI changes.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/parser/TimetableParser.kt`, `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/parser/GradeParser.kt`, `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/parser/AuthParser.kt`
- Impact: Frequent maintenance required if Dualis updates their website. Parsing failures are hard to debug and recover from.
- Fix approach: Transition to a KMP-compatible HTML parser (e.g., `kotlinx.serialization` with a custom HTML format or a lightweight DOM library).

**Destructive Room Migrations:**
- Issue: Database configuration uses `fallbackToDestructiveMigration(dropAllTables = true)`.
- Files: `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/data/storage/database/DatabaseFactory.android.kt`, `composeApp/src/desktopMain/kotlin/de/fampopprol/dhbwhorb/storage/database/DatabaseFactory.desktop.kt`
- Impact: Any schema change wipes all user data (grades, timetable). This will lead to poor user experience on updates.
- Fix approach: Implement proper Room migrations for schema changes.

**God Service Pattern:**
- Issue: `DualisLectureService` and `DualisGradeService` handle API orchestration, parsing coordination, and database persistence.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/DualisLectureService.kt`, `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/DualisGradeService.kt`
- Impact: High complexity, difficult to test in isolation, and violates the Single Responsibility Principle.
- Fix approach: Split services into Repository (persistence), API Client (networking), and Use Case (orchestration) layers.

**Manual Dependency Injection in UI:**
- Issue: Services and ViewModels are instantiated manually within the `App` composable.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt`
- Impact: Hard to manage dependencies as the app grows; complicates testing and platform-specific injections.
- Fix approach: Adopt a DI framework like Koin or Hilt (if mobile-only, but Koin is better for KMP).

## Security Considerations

**Plaintext Credential Handling in Memory:**
- Issue: User credentials (username/password) are handled as `String` objects throughout the application flow.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/storage/credentials/CredentialsStorageProvider.kt`, `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/AuthenticationService.kt`
- Risk: Credentials remain in memory longer than necessary and can be captured in heap dumps.
- Recommendations: Use `CharArray` or sensitive data wrappers where possible. Ensure memory is cleared after use.

## Performance Bottlenecks

**Sequential Network Requests (N+1 Problem):**
- Problem: `DualisLectureService` may trigger additional requests to "enrich" lecture data for each event in a week.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/DualisLectureService.kt`
- Cause: The weekly view only provides partial data, necessitating individual requests for full details.
- Improvement path: Implement concurrent fetching or batching if supported by the server; optimize background enrichment logic.

**Inconsistent Cache Policies:**
- Problem: Lectures are cached for 3 days, while grades use a 1-hour window.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/LectureService.kt`, `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/DualisGradeService.kt`
- Impact: Inconsistent user experience regarding data freshness.

## Fragile Areas

**Dualis Session Management:**
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/AuthenticationService.kt`
- Why fragile: Relies on capturing specific redirects and session cookies from a legacy web portal.
- Safe modification: Heavy unit testing with mocked HTML responses.

**HTML Parser Error Resilience:**
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/parser/TimetableParser.kt`
- Why fragile: If a single regex match fails, it often returns `null`, potentially losing an entire lecture or failing the whole parse.
- Test coverage: Need more edge-case HTML samples in tests.

## Missing Critical Features

**Background Synchronization:**
- Problem: The app primarily refreshes data when opened.
- Blocks: Real-time notifications for grade changes or timetable shifts.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureChangeMonitor.kt` (Initial work present but not fully integrated).

**Notification Integration:**
- Problem: Grade change notifications are not yet implemented.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/DualisGradeService.kt` (TODO: Trigger notification).

## Test Coverage Gaps

**Lecture Event Details:**
- What's not tested: Parsing and storage of `fullSubjectName` and detailed lecture info.
- Files: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/storage/database/entities/timetable/LectureEventEntity.kt`
- Priority: Medium

**Authentication Edge Cases:**
- What's not tested: Complex re-authentication flows and session timeout handling during active requests.
- Risk: Users might experience silent failures or repeated login prompts.
- Priority: High

---

*Concerns audit: 2024-11-20*
