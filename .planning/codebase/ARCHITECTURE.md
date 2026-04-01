# Architecture

**Analysis Date:** 2025-03-11

## Pattern Overview

**Overall:** Layered Architecture with MVVM for the UI layer, implemented using Kotlin Multiplatform (KMP) and Compose Multiplatform.

**Key Characteristics:**
- **Layered Structure:** Separation between UI, Business Logic (Services), and Data (Remote/Storage).
- **MVVM Pattern:** ViewModels handle UI state and logic, while Composables react to state changes.
- **Manual Dependency Injection:** Dependencies are manually instantiated and passed down, primarily in `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt`.
- **Platform-Agnostic Core:** Most business logic and data handling reside in `commonMain`.

## Layers

**UI Layer:**
- Purpose: Handles rendering and user interaction.
- Location: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/`
- Contains: Composables (Pages, Components), ViewModels, Theme definitions.
- Depends on: Services, Data Models.
- Used by: Platform-specific entry points (Android, iOS, Desktop, macOS).

**Service Layer:**
- Purpose: Orchestrates business logic and coordinates data between remote and local sources.
- Location: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/`
- Contains: `DualisGradeService`, `LectureService`, `NotificationManager`.
- Depends on: Data Repositories, API Clients, DAOs.
- Used by: ViewModels.

**Data Layer:**
- Purpose: Handles data retrieval from network and local persistence.
- Location: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/`
- Contains: `DualisApiClient`, `AppDatabase` (Room), `SecureStorage`, `HtmlParser`.
- Depends on: Ktor (Network), Room (Database).
- Used by: Services.

## Data Flow

**Remote Data Fetching (e.g., Grades):**

1. **ViewModel** (e.g., `GradesViewModel`) triggers a load action.
2. **Service** (e.g., `DualisGradeService`) checks if valid data exists in the local **Cache** (`AppDatabase`).
3. If cache is invalid or missing, **Service** calls **Remote Client** (`DualisApiClient`).
4. **Remote Client** performs a network request via Ktor and returns raw HTML.
5. **Parser** (`GradeParser`) extracts structured data from the HTML.
6. **Service** saves the parsed data into **AppDatabase** and returns it to the **ViewModel**.
7. **ViewModel** updates its `uiState` (using Compose `mutableStateOf`).
8. **Composables** (UI) automatically re-render based on the new state.

**State Management:**
- UI state is managed within ViewModels using Compose `mutableStateOf` and `StateFlow` (e.g., in `NotificationPreferencesInteractor`).
- Global application state (like auth status) is managed in `App.kt` and `SessionManager`.

## Key Abstractions

**ViewModel:**
- Purpose: Bridge between UI and Business Logic.
- Examples: `GradesViewModel.kt`, `TimetableViewModel.kt`.
- Pattern: Observable state pattern using Compose `mutableStateOf`.

**Service:**
- Purpose: Domain-specific logic and data orchestration.
- Examples: `DualisGradeService.kt`, `AuthenticationService.kt`.
- Pattern: Service pattern.

**DAO (Data Access Object):**
- Purpose: Abstraction for database operations.
- Examples: `GradeDao.kt`, `LectureEventDao.kt`.
- Pattern: Room DAO pattern.

**Parser:**
- Purpose: Decouples HTML structure from data models (essential for scraping).
- Examples: `HtmlParser.kt`, `GradeParser.kt`, `TimetableParser.kt`.

## Entry Points

**Common Main:**
- Location: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt`
- Triggers: Platform-specific app launches.
- Responsibilities: DI initialization, Root Navigation, Theme management.

**Android:**
- Location: `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt`
- Triggers: Android OS.

**iOS:**
- Location: `iosApp/iosApp/iOSApp.swift` (wraps KMP `ComposeUIViewController`).

## Error Handling

**Strategy:** Functional approach using `Result` types for service calls and UI-level error states.

**Patterns:**
- **Result Wrapper:** Service methods return `Result<T>` to propagate success or failure.
- **UI State Error:** `UiState` classes include an `error: String?` field to display messages to the user.
- **Retry Logic:** Implemented in services (e.g., `fetchGradesWithRetry` in `DualisGradeService.kt`).

## Cross-Cutting Concerns

**Logging:** Uses `Napier` for multiplatform logging (`DebugAntilog` on all platforms).
**Validation:** Basic HTML validation in Parsers (`isValidGradePage`).
**Authentication:** Managed via `SessionManager` and `AuthenticationService`, using `SecureStorage` for credentials.

---

*Architecture analysis: 2025-03-11*
