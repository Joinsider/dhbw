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

## Lifecycle & Ownership (Phase 12)

### Initialization Sequence

The app initializes in the following order to prevent race conditions and ensure services are available when needed:

1. **MainActivity.onCreate()** (Android entry point)
   - Calls `enableEdgeToEdge()` for system bar inset handling (Phase 10)
   - Registers `HttpClientManager` as a lifecycle observer for resource cleanup
   - Sets content with `App()` composable
   - Launches `initializeServicesAsync()` on `lifecycleScope` (Dispatchers.IO)

2. **initializeServicesAsync()** (lines 170-303 in MainActivity.kt)
   - **Database:** `DatabaseInitializer.initializeDatabaseAsync()`
     - Wrapped in try-catch with auto-recovery: delete corrupted DB, retry (Phase 12, D-03)
     - On failure: logs ERROR via Napier; sets `databaseError` state
   - **HttpClient:** `HttpClientInitializer.initializeHttpClientAsync()`
     - Passed to `httpClientManager.setClient()` for lifecycle-aware cleanup
   - **SecureStorage & SessionManager:** Created once; reused for all services
   - **AuthenticationService:** Created with SessionManager
   - **API Clients:** DualisApiClient, DualisLectureService, etc.
   - **Parsers:** Lazy-loaded inside services (not on main thread)
   - **Notification System:** Conditional initialization based on `notificationPreferencesInteractor.notificationsEnabled`
     - If notifications disabled, NotificationManager and LectureMonitorScheduler skip initialization (Phase 11, BG-01)

3. **App.kt Composition**
   - **Theme Initialization (Phase 12, D-01, D-02):**
     - LaunchedEffect reads theme preference from storage once
     - CompositionLocal provides cached theme to all children
     - Subscription to `notificationPreferencesInteractor.darkMode` Flow updates theme on user toggle
   - **View Model Initialization:** TimetableViewModel created at activity scope (persists across rotations)
   - **UI Composition:** Pages rendered with initialized services and ViewModels

### Service Ownership Matrix

| Service | Owner | Lifecycle | Cleanup | Phase |
|---------|-------|-----------|---------|-------|
| HttpClient | MainActivity | Activity lifecycle | `httpClientManager.onDestroy()` | 8, 11 |
| AppDatabase | MainActivity | Application lifetime | Automatic (Room) | 8 |
| SessionManager | MainActivity | Activity scope | Automatic (GC) | 8 |
| AuthenticationService | MainActivity | Activity scope | Automatic (GC) | 8 |
| TimetableViewModel | MainActivity | Activity scope (persists rotation) | `cleanup()` on new instance | 8 |
| GradesViewModel | App scope | Composable lifetime | `cleanup()` on dispose | 8 |
| DocumentsViewModel | App scope | Composable lifetime | `cleanup()` on dispose | 8 |
| NotificationManager | MainActivity | Conditional (notifications enabled) | Activity lifecycle | 11 |
| LectureMonitorScheduler | MainActivity | Conditional (notifications enabled) | WorkManager cleanup | 11 |
| WidgetSyncWorker | System (WorkManager) | Conditional (active widgets) | WorkManager cleanup | 11 |
| NotificationDispatcher | MainActivity | Activity lifecycle | Automatic (GC) | 8 |
| LectureChangeMonitor | MainActivity | Activity lifetime | Automatic (GC) | 8 |
| LectureService | MainActivity | Activity scope | Factory cleanup | 8 |

### ViewModel Lifecycle Pattern (Phase 8)

All ViewModels implement a **custom CoroutineScope + cleanup()** pattern instead of lifecycle-managed `viewModelScope`:

**Rationale:** Kotlin Multiplatform compatibility. `viewModelScope` is Android-only; custom scope works across Android, iOS, macOS, and Desktop.

**Implementation:**
```kotlin
class MyViewModel(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    fun cleanup() {
        Napier.d("Cleaning up MyViewModel", tag = TAG)
        coroutineScope.cancel()
    }
}
```

**Lifecycle Attachment (for TimetableViewModel only, as activity-scoped):**
- TimetableViewModel created in `MainActivity.initializeServicesAsync()`
- On activity recreation: new instance created, previous instance cleaned up
- Caller (MainActivity or activity manager) must call `viewModel.cleanup()` when discarding

**Lifecycle Attachment (for GradesViewModel, DocumentsViewModel, etc., as composable-scoped):**
- Created via `rememberSaveable { GradesViewModel(...) }`
- On composable disposal: `.cleanup()` called
- Recomposition without disposal: same instance reused

### Cleanup Responsibilities

| Component | Resource | Responsible Party | When |
|-----------|----------|-------------------|------|
| HttpClient | Connection pool | HttpClientManager (observer) | Activity onDestroy |
| Database | Query cursors | Room (automatic) | App shutdown |
| CoroutineScopes | Active jobs | ViewModel.cleanup() | Activity/Composable disposal |
| Preferences Flow | Subscription | LaunchedEffect cleanup | Composable disposal |
| WorkManager jobs | Tasks | WorkManager (automatic) | User disables feature or app uninstall |

### DI Framework Evaluation (Phase 12, D-06)

**Current Status (v3.0):** Manual dependency injection via constructor parameters and `remember` blocks in App.kt.

**Why not adopted:**
- Explicit and debuggable; service creation order is clear
- KMP compatibility; no platform-specific DI framework
- Late-cycle adoption risk; manual DI is proven stable

**Candidate Frameworks (Research Only):**
- **Koin:** KMP support; ServiceLocator pattern; easy to adopt
- **Hilt:** Android-only; tightly integrated with Lifecycle; excellent for Android but not KMP

**Decision:** Maintain manual DI in v3.0. Plan Koin evaluation and adoption for Phase 13 (post-v3.0 cleanup phase).

**Next Steps (Phase 13):**
- Research Koin integration with existing manual DI
- Prototype Koin configuration for viewModels
- Measure adoption complexity and test coverage impact
- If promising: plan Koin adoption for v3.1

---

*Lifecycle & Ownership documentation added in Phase 12*
*Last updated: 2025-04-10*

