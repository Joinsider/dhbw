# Phase 8: Critical Stability Fixes - Context

**Gathered:** 2026-04-10  
**Status:** Ready for planning

---

## Phase Boundary

Phase 8 eliminates ANR (Application Not Responding) crashes and memory leaks by moving heavy initialization off the main thread and implementing proper coroutine cleanup in ViewModels. Success means:
- App responds within 2 seconds on Android 12+ devices (time to first interaction)
- Zero ANR reports in the next release cycle
- Memory usage remains stable over 5+ screen transitions
- All critical services initialized by time user can interact with the app

---

## Implementation Decisions

### Initialization Strategy

**D-01: API Clients & Parsers – Lazy Per-Feature**
- DualisApiClient, HtmlParser, TimetableParser are initialized only when the specific feature is first accessed
- TimetableViewModel initializes DualisLectureService on first use
- DocumentsViewModel (and thus DualisDocumentService) deferred until Documents tab is opened
- Rationale: Reduces MainActivity initialization load; only creates what the user actually needs

**D-02: Visual Loading Feedback – Skeleton/Placeholder States**
- App renders the actual UI structure (empty screens) immediately, not a splash screen overlay
- Each page shows loading skeleton states while data loads
- Material Design pattern: content placeholders, not a blocking full-screen splash
- Rationale: Feels faster to users; allows early interaction with UI structure

**D-03: HttpClient Resource Cleanup – Store in MainActivity.onDestroy()**
- Store a reference to the shared HttpClient in MainActivity
- Call `httpClient.close()` explicitly in `onDestroy()` to prevent connection pool leaks
- Rationale: Prevents "too many open connections" errors on app restart; clear ownership of lifecycle

**D-04: Documents Feature – Lazy Load on Tab Access**
- DocumentsViewModel is NOT initialized in MainActivity.onCreate()
- Initialize only when DocumentsPage composable is first rendered
- Use rememberSaveable to preserve ViewModel across recompositions
- Rationale: ~20% startup time saved for users who never access documents; defers DualisDocumentService creation

**D-05: Navigation Safety – Cancel In-Flight Operations**
- If user navigates away before background initialization completes, cancel the pending coroutines
- Use proper scope cancellation to prevent resource leaks from orphaned tasks
- Rationale: Prevents memory leaks from tasks that outlive their UI context

### ViewModel Lifecycle & Coroutine Management

**D-06: ViewModel CoroutineScope Pattern – Claude's Discretion (KMP-Aware)**
- **Constraint:** ViewModels are in `commonMain` (shared code), so `androidx.lifecycle.viewModelScope` (Android-only) is not available
- **Pattern:** Each ViewModel maintains its own `CoroutineScope(Dispatchers.IO)` as a property
- **Cleanup:** Each ViewModel must implement a `cleanup()` or `onCleared()` method that calls `coroutineScope.cancel()`
- **Responsibility:** Android-side composables or activities must call ViewModel.cleanup() when the ViewModel is no longer needed
- **Alternative considered but deferred:** Moving ViewModels to androidMain with expect/actual pattern for lifecycle-awareness (deferred to Phase 12 Code Quality)
- Rationale: Maintains KMP architecture; simpler than expect/actual for now; explicit cleanup is safe if enforced

**D-07: Scope Isolation Strategy – Claude's Discretion**
- Use a single shared `CoroutineScope` per ViewModel (not isolated per-operation)
- All coroutines launched via `coroutineScope.launch { ... }`
- When cancelled in cleanup(), all pending operations are terminated together
- Rationale: Simpler than per-operation isolation; cancellation is all-or-nothing by design

### Database Initialization

**D-08: Database Blocking Strategy – Claude's Discretion (Async Recommended)**
- Move `createRoomDatabase()` to `Dispatchers.IO` (background coroutine) in MainActivity
- Do NOT block the main thread waiting for database creation
- Options for handling UI:
  - **Option A:** Show skeleton UI while database initializes (D-02 chosen approach)
  - **Option B:** Delay setContent() until database is ready (simpler but slower)
  - **Option C:** Initialize database, but allow UI to render with null placeholders, retry on first data access
- **Recommendation:** Option A + early timeout check
  - If database is ready within ~500ms, proceed normally
  - If not ready, render skeleton UI anyway after timeout
  - Prevents "stuck on splash" experience on very slow devices
- Rationale: Balances safety (database ready before data queries) with responsiveness (UI visible quickly)

### HttpClient Initialization

**D-09: HttpClient Timing – Claude's Discretion (Eager Recommended)**
- Create HttpClient in MainActivity on `Dispatchers.IO` (background thread) during onCreate()
- Do NOT lazily create on first request (connection setup overhead)
- Rationale: Eager creation amortizes setup cost over app lifetime; first network call won't be unexpectedly slow

---

## Claude's Discretion

The following areas where user deferred to Claude's judgment:

1. **Database blocking vs. async (D-08):** Recommending async initialization with skeleton UI + timeout fallback
2. **HttpClient eager vs. lazy (D-09):** Recommending eager initialization on background thread during startup
3. **ViewModel scope isolation (D-07):** Single shared scope per ViewModel (simpler, safe with proper cleanup)
4. **ViewModel lifecycle pattern for KMP (D-06):** Custom CoroutineScope with explicit cleanup() method (works across platforms, avoids expect/actual)

---

## Specific Implementation Details

### Main Thread Blocking Elimination

**Current state (problematic):**
```
onCreate() {
  initializeServices()  // BLOCKS MAIN THREAD:
    ├─ createRoomDatabase()        // Heavy I/O
    ├─ HttpClient initialization   // Network setup
    ├─ All API clients + parsers   // Object creation
    ├─ All ViewModels              // ViewModel init + data loading
    └─ LectureMonitorScheduler     // Background job setup
  setContent()  // Only rendered AFTER all above finishes
}
```

**Target state (Phase 8):**
```
onCreate() {
  setContent()  // Render immediately with skeleton UI
  
  lifecycleScope.launch {
    // Background: move heavy work here
    initializeServicesAsync() {
      ├─ createRoomDatabase() on Dispatchers.IO      // Deferred
      ├─ HttpClient on Dispatchers.IO                // Deferred
      ├─ Parsers created on-demand (lazy)
      └─ LectureMonitorScheduler setup
    }
  }
}
```

### ViewModel Cleanup Pattern (KMP)

Each ViewModel will follow this pattern:

```kotlin
class TimetableViewModel(
  lectureService: LectureService,
  // ... other dependencies
  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
  // ... normal ViewModel code ...
  
  fun cleanup() {
    coroutineScope.cancel()
  }
}
```

Caller (in Android UI layer) must invoke:
```kotlin
DisposableEffect(timetableViewModel) {
  onDispose {
    timetableViewModel.cleanup()
  }
}
```

---

## Canonical References

Downstream agents MUST read these before planning or implementing:

### Phase-Specific Requirements
- `.planning/REQUIREMENTS.md` (STAB-01, STAB-02 sections) — Success criteria and specific tasks
- `.planning/ROADMAP.md` §Phase 8 — Phase goal, dependencies, risks

### Code Context & Integration Points
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` — Current initialization in lines 89–226 (initializeServices method)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt` — Example ViewModel pattern (custom CoroutineScope at line 33)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt` — Similar pattern for GradesViewModel
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt` — Candidate for lazy loading

### Database & Architecture
- `composeApp/src/commonMain/kotlin/.../data/storage/database/` — AppDatabase and createRoomDatabase() function location
- `composeApp/src/androidMain/kotlin/.../data/network/` — Custom DNS resolver and HttpClient setup

### Existing Patterns to Preserve
- `DualisLectureService`, `DualisGradeService`, `DualisDocumentService` — API service pattern (move to lazy initialization)
- `NotificationDispatcher`, `LectureMonitorScheduler` — Background task patterns (essential, initialize early)

---

## Existing Code Insights

### Reusable Assets
- **lifecycleScope.launch { }** (MainActivity line 195): Already used for preference observation; pattern applies to async initialization
- **Custom CoroutineScope pattern** (ViewModels): Established in TimetableViewModel, GradesViewModel, DocumentsViewModel; extend with cleanup()
- **Napier logging**: Already instrumented throughout; use for init phase logging

### Established Patterns
- **Service dependency injection via constructor**: All services pass dependencies (good; maintain for clarity)
- **Staged loading** (TimetableViewModel.loadLecturesForWeekStaged): Already implements skeleton + full data pattern; extend to initialization
- **Preference-driven initialization** (MainActivity line 195): Combine flows for scheduler control; apply same pattern to feature flags

### Integration Points
- **MainActivity.onCreate()** (line 55–87): Entry point for refactoring; split into sync (UI) + async (heavy work)
- **setContent()** (line 78): Will move earlier; remove dependency on service initialization
- **ViewModels passed to App()** (line 80–85): Database and preferences already passed; extend pattern to lazy-loaded features

---

## Risk Mitigations

### Risk: Initialization Order Issues Breaking Features
- **Mitigation:** Document initialization order explicitly; use dependency injection for clear ownership
- **Testing:** Unit tests verify all services initialize without exceptions; integration tests check feature access at each init stage

### Risk: Race Condition Between Splash Dismissal and Initialization
- **Mitigation:** Use coroutine completion signaling (Channel/Flow from D-02 skeleton states)
- **No blocking splash screen:** Use timeout-based fallback to avoid "stuck on splash" on slow devices

### Risk: Database Not Ready When First ViewModel Queries It
- **Mitigation:** Wrap initial queries in try-catch; defer ViewModel data loading until database ready
- **Alternative:** ViewModels check database availability before querying; show loading state if not ready yet

---

## Deferred Ideas

None — discussion stayed within Phase 8 scope. Phase 12 (Code Quality & Cleanup) will revisit DI framework adoption for lifecycle-aware ViewModel management across all platforms.

---

*Phase: 08-critical-stability*  
*Context gathered: 2026-04-10*
