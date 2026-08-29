# Phase 12: Code Quality & Cleanup - Context

**Gathered:** 2026-04-10  
**Status:** Ready for planning

---

## Phase Boundary

Phase 12 improves code maintainability, error resilience, and consistency through theme optimization, database error handling, proper lifecycle management, and race condition elimination. Success means:
- First frame rendered 100ms faster on slow devices via theme caching
- App handles corrupted database gracefully with auto-recovery
- Clear lifecycle ownership across Android/iOS/Desktop platforms
- No UI flicker or race conditions in loading state transitions
- All ViewModels implement deterministic loading patterns

---

## Implementation Decisions

### Theme Initialization & Caching

**D-01: Lazy Theme Loading with CompositionLocal**
- Theme preferences are read in a LaunchedEffect at the top of the App composable (not in Application.onCreate or MainActivity.onCreate)
- Cached in a CompositionLocal (e.g., `LocalThemePrefs`) to avoid prop drilling and repeated storage reads
- Rationale: Simplest pattern; theme is guaranteed available when App composable renders; no need for Application-level setup

**D-02: Batch Theme Changes from Storage Flow**
- When user toggles dark mode in SettingsPage, the toggle writes to storage, which triggers a Flow
- That Flow update causes a LaunchedEffect to re-read from storage and update the CompositionLocal state
- Avoids multiple recompositions per toggle; read happens once per change, result cached for all children
- Rationale: Balances reactivity with performance; matches existing preference observer pattern in MainActivity (Phase 8)

### Database Error Recovery

**D-03: Silent Auto-Recovery with Retry**
- When database initialization fails (throws exception), automatically attempt recovery:
  1. Delete the corrupted database file
  2. Retry initialization immediately
  3. If retry succeeds, proceed normally (no error shown to user)
- Rationale: Most database corruption is transient; auto-recovery catches it without user intervention

**D-04: Blocking Error Screen on Unrecoverable Failure**
- If auto-recovery fails (both deletion and retry fail), show a persistent blocking error screen
- Message: "Database error. Please reinstall the app or contact support."
- User cannot navigate past this screen; forces clean state via reinstall
- Rationale: Unrecoverable corruption is rare; blocking screen ensures data consistency and clear next steps

**D-05: Structured Napier Logging**
- All database initialization failures are logged via Napier at ERROR level
- Include: timestamp, "Database initialization failed", exception message, database file path
- Consistent with existing app logging (Napier used throughout codebase)
- Rationale: Structured logs are easier to parse in Play Console crash reports; matches app conventions

### Lifecycle Management & Service Ownership

**D-06: Maintain Manual DI in v3.0; Evaluate Koin for Future**
- Phase 12 does NOT adopt a DI framework (Hilt or Koin) in v3.0
- Instead, document service ownership and lifecycle clearly in ARCHITECTURE.md
- During Phase 12, research Koin as a potential multiplatform solution
- If Koin is viable, plan adoption as a Phase 13 task (post-v3.0 cleanup phase)
- Rationale: Manual DI is explicit and debuggable; adopting a framework now would risk introducing regressions late in the cycle. Research-then-decide is safer.

**D-07: ARCHITECTURE.md Lifecycle & Ownership Documentation**
- Add a new section to `.planning/codebase/ARCHITECTURE.md`:
  - **Initialization Sequence:** Step-by-step order of service creation (text + code snippets from MainActivity and App.kt)
  - **Service Ownership Matrix:** Table of which service owns which resource (e.g., "HttpClientManager owns httpClient, cleaned up in MainActivity.onDestroy()")
  - **ViewModel Lifecycle Pattern:** Document Phase 8's custom CoroutineScope + cleanup() pattern; explain why it's KMP-compatible
  - **Cleanup Responsibilities:** Which platform owns cleanup (e.g., "Android Activity owns lifecycle observer for HttpClient")
- Rationale: Prevents future confusion and makes lifecycle issues discoverable without needing to read code

### Loading State Race Condition Fix

**D-08: Separate StateFlows for Load State Management**
- Use three separate StateFlows in ViewModels:
  - `isLoading: StateFlow<Boolean>` — true during initial data fetch
  - `data: StateFlow<Data>` — current data (empty initially, skeleton data, then full data)
  - `isRefreshing: StateFlow<Boolean>` — true during pull-to-refresh or background sync
- UI logic composes these: show skeleton if isLoading; show data if available; show refresh spinner if isRefreshing
- Rationale: Flexible and matches existing pattern (mutableStateOf for state); easy to understand and test; no complex sealed classes

**D-09: Mutex Serialization for Load Operations**
- Each ViewModel with data loading (TimetableViewModel, GradesViewModel, DocumentsViewModel) implements a Mutex
- All load operations (initial fetch, refresh, pagination) acquire the Mutex before executing
- If a load is already in progress, subsequent refresh requests wait or are cancelled (depends on UI interaction)
- Prevents concurrent load coroutines from race-condition-ing data updates
- Rationale: Serialization ensures deterministic state transitions; Mutex is cleaner than storing job references

**D-10: Apply Race Condition Fix to All ViewModels**
- TimetableViewModel, GradesViewModel, DocumentsViewModel all get Mutex + StateFlow pattern
- Target implementation scope: ~3 ViewModels modified (not a base class refactor in v3.0)
- Rationale: All three have user-visible loading states; fixing all ensures consistent UX across the app

### Material3 Version Management

**D-11: Defer Material3 Stable Update; Keep Alpha for Expressive Components**
- Do NOT update Material3 to 1.9.0 stable when released
- The app uses Material 3 Expressive, which still has components only available in alpha releases
- Keep current alpha version (1.9.0-alpha04) or update only to newer alphas if needed for bug fixes
- Rationale: Stable release would break Expressive components; functionality > version stability in this case

---

## Claude's Discretion

The following areas where user deferred to Claude's judgment:

1. **Theme preload location (D-01):** Chose LaunchedEffect over Application.onCreate because it's simpler and KMP-compatible
2. **Theme caching mechanism (D-02):** Chose CompositionLocal over separate state variables for cleaner API

---

## Canonical References

Downstream agents MUST read these before planning or implementing:

### Phase Requirements & Goals
- `.planning/ROADMAP.md` §Phase 12 — Phase goal, business impact, success criteria
- `.planning/REQUIREMENTS.md` §CODE-01, CODE-02, CODE-03, CODE-04, CODE-05 — Specific requirements and success metrics

### Phase Context & Prior Decisions
- `.planning/phases/08-critical-stability/08-CONTEXT.md` — Phase 8 decisions on async initialization, ViewModel lifecycle pattern, custom CoroutineScope with cleanup()
- `.planning/phases/10-android-api-compliance-weeks-8-10/10-CONTEXT.md` — Phase 10 device detection and edge-to-edge decisions
- `.planning/phases/11-background-services-resource-management-weeks-11-13/11-CONTEXT.md` — Phase 11 conditional service initialization patterns

### Architecture & Code References
- `.planning/codebase/ARCHITECTURE.md` — Current architecture (will be extended with Lifecycle & Ownership section in Phase 12)
- `.planning/codebase/CONVENTIONS.md` — Naming patterns, error handling conventions
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt` — Current DI and state management; integration point for theme caching
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` — Entry point; where lifecycle observer pattern is established
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt` — Example ViewModel with existing staged loading pattern
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt` — Similar pattern for GradesViewModel
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt` — Candidate for race condition fix
- `composeApp/src/commonMain/kotlin/.../data/storage/database/` — AppDatabase and createRoomDatabase() function location

### Research References (for Phase Researcher)
- Koin documentation for KMP (research only; not for implementation in v3.0)
- Compose LaunchedEffect best practices for theme initialization
- Kotlin Mutex documentation for serializing coroutine operations
- Room database error recovery patterns

---

## Existing Code Insights

### Reusable Assets
- **lifecycleScope.launch { }** (MainActivity): Already used for preference observation and async initialization; extend for theme loading
- **Custom CoroutineScope pattern** (all ViewModels): Established in Phase 8; extend with Mutex for load serialization
- **StateFlow for preferences** (NotificationPreferencesInteractor): Pattern for theme preferences flow; reuse for theme state
- **Staged loading pattern** (TimetableViewModel.loadLecturesForWeekStaged): Already has skeleton + full data; enhance with proper race condition prevention
- **Napier logging**: Already instrumented throughout; use for database error logging

### Established Patterns
- **Preference-driven initialization** (MainActivity): Combine flows for feature preferences; apply similar pattern to theme
- **Service dependency injection via constructor**: Maintain for clarity; extend documentation for lifecycle ownership
- **Result types for error handling**: Already used in services; apply to database initialization

### Integration Points
- **App composable** (App.kt): Add LaunchedEffect for theme loading; wrap in CompositionLocal
- **MainActivity.onCreate()** (lines 55–87): Update with database error handling try-catch; log failures via Napier
- **ViewModels** (TimetableViewModel, GradesViewModel, DocumentsViewModel): Add Mutex + separate StateFlows for load state
- **ARCHITECTURE.md** (currently at `.planning/codebase/ARCHITECTURE.md`): Extend with Lifecycle & Ownership section

---

## Specific Ideas

### Theme Initialization Spot
When implementing D-01, place the LaunchedEffect early in App composable, before CompositionLocalProvider wraps children. This ensures theme is loaded before any child composable tries to access it via LocalThemePrefs.current.

### Mutex Implementation Pattern
Use `private val loadMutex = Mutex()` in ViewModel; wrap load logic:
```kotlin
private suspend fun loadData() {
  loadMutex.withLock {
    isLoading.value = true
    try {
      // fetch data
    } finally {
      isLoading.value = false
    }
  }
}
```

### Database Recovery Error Message
Craft the error message to be actionable: "Database error. Restart the app to retry. If the problem persists, please reinstall." Gives user clear steps.

---

## Deferred Ideas

### Material3 Expressive Component Upgrades (User Note)
User clarified: Material 3 Expressive is still alpha-heavy and will remain so. Do NOT attempt stable version upgrade in Phase 12 or v3.0. Revisit in v3.1 when Expressive matures.

### DI Framework Adoption
- **Idea:** Adopt Hilt (Android) or Koin (KMP) for lifecycle-aware service creation
- **Decision:** Deferred to Phase 13 after research in Phase 12
- **Reason:** Late-cycle adoption risk; manual DI is stable; research first, then plan adoption separately

### Base ViewModel Refactor
- **Idea:** Create abstract LoadingViewModel base class with Mutex + StateFlow pattern
- **Decision:** Deferred to Phase 13 if time permits
- **Reason:** Refactoring all ViewModels in Phase 12 could introduce regressions; direct implementation in each ViewModel is safer

### Per-User Theme Customization Settings
- **Idea:** Add theme accent color picker, font size adjustment, contrast modes
- **Decision:** Out of scope for v3.0; defer to v3.1
- **Reason:** Phase 12 focuses on CODE-01 (improve initialization), not new features

---

*Phase: 12-code-quality-cleanup-weeks-14-17*  
*Context gathered: 2026-04-10*
