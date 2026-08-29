# Service Initialization Order (Phase 8)

This document defines the official service initialization order to ensure stability and prevent race conditions or circular dependencies during app startup.

## 1. Synchronous Initialization (Main Thread)
*Execution: `MainActivity.onCreate()` (Immediate)*

| Order | Service | Purpose | Dependencies |
| :--- | :--- | :--- | :--- |
| 1.1 | `enableEdgeToEdge()` | UI setup | None |
| 1.2 | `super.onCreate()` | Activity lifecycle | None |
| 1.3 | `setContent { App() }` | **EARLY UI RENDER** | `isInitialized` flag |
| 1.4 | `NotificationDispatcher.initialize(this)` | System service access | Android `Context` |

---

## 2. Asynchronous Initialization (Background Thread)
*Execution: `lifecycleScope.launch(Dispatchers.IO)` (Non-blocking)*

| Order | Service | Purpose | Dependencies |
| :--- | :--- | :--- | :--- |
| 2.1 | `DatabaseInitializer.initializeDatabaseAsync()` | Database access | Android `Context` (via `getDatabaseBuilder`) |
| 2.2 | `HttpClientInitializer.initializeHttpClientAsync()` | Networking | None (uses `CustomDnsResolver` on Android) |
| 2.3 | `SecureStorage` / `SessionManager` | Persistence | None |
| 2.4 | `AuthenticationService` | Login/Auth | `SessionManager`, `HttpClient` |
| 2.5 | `DualisApiClient` | Network requests | `HttpClient` |
| 2.6 | `DualisLectureService` | Timetable data | `DualisApiClient`, `SessionManager`, `AuthenticationService`, `Database` (DAOs) |
| 2.7 | `LectureService` | Business logic | `Database`, `DualisLectureService` (lazy factory) |
| 2.8 | `TimetableViewModel` | UI State | `LectureService`, `Database` (DAOs) |
| 2.9 | `NotificationPreferences` | Settings | `SecureStorageWrapper` |
| 2.10 | `LectureChangeMonitor` | Background sync | `DualisLectureService`, `Database` (DAOs) |
| 2.11 | `NotificationManager` | Alert dispatch | `LectureChangeMonitor`, `NotificationDispatcher`, `NotificationPreferences` |
| 2.12 | `LectureMonitorScheduler` | Periodic worker | `NotificationManager`, Android `Context` |

---

## 3. Lazy Initialization (First Use Only)
*Execution: Triggered by UI access or first feature demand*

| Service | Initialization Point | Purpose |
| :--- | :--- | :--- |
| `DualisDocumentService` | `App.kt` (when Documents screen active) | Document fetching |
| `DualisGradeService` | `App.kt` (when Grades screen active) | Grade fetching |
| `TimetableParser` | `DualisLectureService` (first `lazy` access) | HTML parsing |
| `GradeParser` | `DualisGradeService` (first `lazy` access) | HTML parsing |
| `DocumentParser` | `DualisDocumentService` (first `lazy` access) | HTML parsing |
| `HtmlParser` | All services (first `lazy` access) | Common HTML checks |

---

## 4. Safety Guardrails

### 500ms Timeout Fallback
If the asynchronous initialization (Step 2) takes longer than 500ms, `MainActivity` forces `isInitialized = true`. 
- **Result:** `App()` renders the main UI screens (Timetable/Login) instead of the initial splash `LoadingIndicator`.
- **Handling:** ViewModels and Pages must handle `null` dependencies (e.g., `viewModel == null` or `database == null`) by showing appropriate skeleton/loading states until services finally arrive.

### Resource Cleanup
- **ViewModels:** Implement `cleanup()` to cancel their `coroutineScope`.
- **Pages:** Call `viewModel.cleanup()` using `DisposableEffect`.
- **MainActivity:** Calls `cleanup()` on all active ViewModels and closes the shared `HttpClient` in `onDestroy()`.
