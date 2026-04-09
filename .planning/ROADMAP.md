# Roadmap: DHBW Dualis KMP v3.0 Stability & Compliance

**Milestone Goal:** Fix critical ANR/freezing issues, ensure Android 15+ API compliance, optimize performance, and improve code quality through proper lifecycle management and resource cleanup.

**Continuation:** This milestone continues from v2.0 Phase 7.1. All phases are numbered starting from Phase 8.

---

## Summary Table

| Phase | Title | Focus Area | Requirements | Est. Effort |
|-------|-------|-----------|--------------|------------|
| 8 | Critical Stability Fixes | Main Thread & Memory | STAB-01, STAB-02 | 3-4 weeks |
| 9 | Performance Optimization | Database & File I/O | STAB-03, STAB-04 | 2-3 weeks |
| 10 | Android API Compliance | System Integration | ANDROID-01, ANDROID-02, ANDROID-03 | 2-3 weeks |
| 11 | Background Services & Resource Mgmt | Workers & Lifecycle | BG-01, BG-02, BG-03 | 2-3 weeks |
| 12 | Code Quality & Cleanup | Architecture | CODE-01, CODE-02, CODE-03, CODE-04, CODE-05 | 3-4 weeks |

---

## Phase 8: Critical Stability Fixes (Weeks 1-4)

**Goal:** Eliminate ANR (Application Not Responding) crashes and memory leaks by moving heavy initialization off the main thread and implementing proper coroutine cleanup in ViewModels.

**Business Impact:** Reduce ANR reports in Google Play Console by 90%+, improve perceived app responsiveness on startup.

### Requirements Addressed
- **STAB-01**: Fix ANR/Freezing in MainActivity Initialization
- **STAB-02**: Fix Memory Leaks in ViewModels

### Success Criteria
1. **Startup Performance:** App responds within 2 seconds on Android 12+ devices (measured by time to first interaction)
2. **ANR Elimination:** Zero ANR reports in next release cycle (vs. current reported ANRs)
3. **Memory Stability:** Memory usage remains stable after 5+ screen transitions without growing leaks (profiler verification)
4. **Splash Screen:** Visual loading indicator shown during initialization completes (<500ms delay perceived)
5. **Test Coverage:** Unit tests verify background initialization and coroutine cleanup in all ViewModels

### Technical Approach
- Move database creation (`createRoomDatabase()`) to `Dispatchers.IO` coroutine
- Move HttpClient initialization to background coroutine
- Defer parser and API client initialization until needed
- Implement `onCleared()` in TimetableViewModel, GradesViewModel, DocumentsViewModel to cancel CoroutineScope
- Replace custom CoroutineScopes with lifecycle-managed `viewModelScope`
- Implement splash screen or loading state during initialization

### Dependencies
- None (critical path - first phase)

### Risks & Mitigations
- **Risk:** Initialization order issues breaking feature discovery
    - **Mitigation:** Document initialization order; use dependency injection for clear ownership
- **Risk:** Race condition between splash screen dismissal and initialization completion
    - **Mitigation:** Use coroutine-based completion signaling (Channel/Flow)

---

## Phase 9: Performance Optimization (Weeks 5-7)

**Goal:** Optimize database queries and eliminate blocking file I/O to improve responsiveness during data loading and document operations.

**Business Impact:** Reduce timetable load time by 90%, eliminate ANR during document operations (>10MB files).

### Requirements Addressed
- **STAB-03**: Optimize Database Queries (N+1 Problem)
- **STAB-04**: Make File I/O Non-Blocking

### Success Criteria
1. **Query Reduction:** Weekly timetable load reduced from 60+ queries to <5 queries (measured by Room query logging)
2. **Load Time:** Timetable week loads in <500ms on mid-range device (Snapdragon 778G or equivalent)
3. **Zero UI Lag:** No detectable jank during timetable rendering (60fps maintained)
4. **File I/O Non-Blocking:** No ANR when opening/saving documents >10MB (5-minute ANR timeout)
5. **Progress Feedback:** Large file operations (>5MB) show smooth progress indication
6. **Streaming:** Large documents streamed instead of fully loaded in memory

### Technical Approach
- Implement Room multi-map query with `@Relation` for efficient lecturer joins
- Cache lecturer data locally in ViewModel to avoid repeated queries
- Convert `FileViewer.openFile()` to suspend function using `Dispatchers.IO`
- Convert `FileViewer.saveFileWithDialog()` to suspend function
- Implement streaming for large documents (chunked reading)
- Add progress callbacks for file operations

### Dependencies
- **Blocked By:** Phase 8 (requires stable initialization)

### Risks & Mitigations
- **Risk:** Multi-map queries may have edge cases with Room
    - **Mitigation:** Comprehensive unit tests with sample data; fallback to manual batching if needed
- **Risk:** Streaming implementation complexity
    - **Mitigation:** Start with simple buffering; optimize only if profiler shows bottleneck

---

## Phase 10: Android API Compliance (Weeks 8-10)

**Goal:** Update app to be fully compliant with Android 15+ APIs, support large screens and foldables, and remove deprecated system window calls.

**Business Impact:** Pass Google Play Console compliance checks, expand addressable market to 100% of Android devices.

### Requirements Addressed
- **ANDROID-01**: Fix Edge-to-Edge Display Compatibility (Android 15+)
- **ANDROID-02**: Support Large Screens & Foldables
- **ANDROID-03**: Remove Display Restrictions for Android 16+

### Success Criteria
1. **Zero Deprecation Warnings:** No deprecation warnings in Google Play Console for targetSdkVersion
2. **Android 15+ Rendering:** App displays correctly on Android 15+ without system bar color deprecated calls
3. **Edge-to-Edge:** `enableEdgeToEdge()` properly called; inset handling verified in layouts
4. **Large Screen Support:** App works correctly on tablets, foldables, and split-screen mode
5. **Device Detection:** `isPhone()` using WindowMetrics.bounds correctly identifies device form factor
6. **Foldable Handling:** Hinge position detection works on foldable test devices/emulators
7. **Orientation Flexibility:** Dynamic orientation adjustment based on window size; portrait lock removed for Android 16+
8. **Compliance:** Passes Android 16 large-screen compliance check in multi-window mode

### Technical Approach
- Update androidx-activity to 1.9.0+ (if needed for edge-to-edge support)
- Verify `enableEdgeToEdge()` called in MainActivity before `setContent()`
- Replace deprecated `setStatusBarColor`/`setNavigationBarColor` calls
- Improve `isPhone()` detection using `WindowMetrics.bounds`
- Allow dynamic orientation based on current window size
- Remove hard portrait orientation lock from MainActivity.onCreate()
- Implement hinge position detection for foldables
- Test on Galaxy Z Fold/Z Flip emulator or physical devices

### Dependencies
- **Blocked By:** Phase 8 (requires stable MainActivity initialization)

### Risks & Mitigations
- **Risk:** Edge-to-edge inset handling may cause layout issues on older devices
    - **Mitigation:** Test on Android 14-15 range; use WindowCompat for backward compatibility
- **Risk:** Foldable testing limited without physical devices
    - **Mitigation:** Use emulator hinge detection; document manual testing procedure

---

## Phase 11: Background Services & Resource Management (Weeks 11-13)

**Goal:** Fix background job scheduling, eliminate HTTP client resource leaks, and optimize app startup by lazy-loading heavy features.

**Business Impact:** Reduce battery drain by 20%+, provide user control over background sync, eliminate connection pool exhaustion errors.

### Requirements Addressed
- **BG-01**: Fix WorkManager Background Job Scheduling
- **BG-02**: Fix HttpClient Resource Leaks
- **BG-03**: Lazy-Load DocumentsViewModel

### Success Criteria
1. **User Control:** Users can enable/disable background sync; WidgetSyncWorker only scheduled on explicit opt-in
2. **Battery Impact:** Battery drain reduced by 20%+ (measured by standby test on test device)
3. **Configurable Intervals:** 15-minute sync interval is configurable; reasoning documented
4. **No Connection Pool Leaks:** No "too many open connections" errors; verified in profiler after multiple app restarts
5. **Resource Cleanup:** HttpClient properly closed in MainActivity.onDestroy(); verified in Profiler
6. **Startup Performance:** App startup 15-20% faster for users who never access Documents feature (measured via startup profiler)
7. **Deferred Initialization:** DocumentsViewModel only initialized when DocumentsPage first accessed
8. **Graceful Feature Access:** No UI lag when Documents page is first opened

### Technical Approach
- Remove hardcoded "5 minutes for testing" from LectureMonitorScheduler
- Add configurable constant for 15-minute interval; document WorkManager minimum enforcement
- Only schedule WidgetSyncWorker if explicitly enabled by user
- Add early exit check in `initializeServices()` before heavy initialization
- Store HttpClient reference in MainActivity; call `close()` in `onDestroy()`
- Verify no connection pool leaks via Profiler testing
- Implement conditional DocumentsViewModel initialization using `rememberSaveable`
- Defer `DualisDocumentService` initialization until feature is accessed

### Dependencies
- **Blocked By:** Phase 8 (requires stable service initialization ordering)

### Risks & Mitigations
- **Risk:** WorkManager enforces 15-minute minimum; users may expect faster sync
    - **Mitigation:** Document limitation prominently; offer platform-specific workarounds if feasible
- **Risk:** Lazy-loading Documents may cause initial UI lag on first access
    - **Mitigation:** Pre-initialize on demand with loading state; monitor performance

---

## Phase 12: Code Quality & Cleanup (Weeks 14-17)

**Goal:** Improve code maintainability, error resilience, and consistency through theme optimization, database error handling, proper lifecycle management, dependency management, and race condition elimination.

**Business Impact:** Reduce crash rate from unhandled errors, improve first-frame rendering on slow devices, eliminate UI flicker from race conditions.

### Requirements Addressed
- **CODE-01**: Improve Theme Initialization
- **CODE-02**: Add Error Recovery for Database Initialization
- **CODE-03**: Consistent Lifecycle Management Across Platforms
- **CODE-04**: Update Material3 to Stable Version
- **CODE-05**: Fix Staged Loading Race Condition

### Success Criteria
1. **Theme Performance:** First frame rendered 100ms faster on slow devices (measured on Snapdragon 680 or equivalent)
2. **Theme Caching:** Theme values cached; storage reads moved off composition path to LaunchedEffect
3. **DB Error Handling:** App handles corrupted database gracefully; user sees actionable error screen
4. **DB Recovery:** Database deletion and re-initialization on corruption; users can retry
5. **Lifecycle Documentation:** Initialization order and lifecycle expectations documented
6. **Service Ownership:** No duplicate service initialization; clear ownership of lifecycle responsibilities
7. **DI Framework:** Consider Hilt or Koin for consistent service creation across platforms (decision documented)
8. **Material3 Stable:** Updated to Material3 1.9.0 final release (when available); no pre-release warnings
9. **API Compatibility:** Tested for breaking changes between 1.9.0-alpha04 and stable
10. **UI Consistency:** Material3 rendering consistent across Android, iOS, Desktop
11. **Race Condition Fixed:** Replaced polling-based second fetch with Flow-based reactive loading
12. **Loading States:** Deterministic loading order (Loading → Loaded → Refreshing) implemented
13. **No UI Flicker:** No flicker between skeleton data and full data; race conditions eliminated
14. **Deterministic Updates:** Data updates always in predictable order

### Technical Approach
- Preload theme preferences in Application.onCreate()
- Cache theme values to avoid repeated storage reads
- Move theme reads off composition path into LaunchedEffect
- Wrap `createRoomDatabase()` in try-catch
- Implement fallback strategy: delete corrupted DB, show recovery screen
- Log detailed error information for debugging
- Document initialization order and lifecycle expectations
- Separate platform-specific initialization from common code
- Evaluate DI frameworks (Hilt, Koin) for consistent service creation
- Update Material3 dependency to 1.9.0 stable
- Test for API changes and breaking changes
- Verify UI rendering consistency across platforms
- Replace polling-based second fetch with Flow-based reactive loading
- Implement proper loading states (Loading → Loaded → Refreshing)
- Ensure deterministic data update order

### Dependencies
- **Blocked By:** Phase 8, 9, 10, 11 (requires all other phases' changes to be stable before final cleanup)
- **Optional Coordination:** Can run in parallel with Phase 11 after Phase 8 completes (except Material3 update which should be last)

### Risks & Mitigations
- **Risk:** Material3 1.9.0 final may not be released during Phase 12 timeline
    - **Mitigation:** Plan for alpha/beta usage; defer stable release update if not available
- **Risk:** DI framework adoption late in cycle may cause regressions
    - **Mitigation:** Evaluate framework but defer adoption to v3.1 if risk is too high; document decision
- **Risk:** Flow-based loading refactor is complex; may introduce new race conditions
    - **Mitigation:** Implement with comprehensive unit tests; use cold Flow to prevent multiple emissions

---

## Execution Strategy

### Wave Pattern
- **Wave 1 (Weeks 1-2):** Phase 8 - Critical foundation
- **Wave 2 (Weeks 3-4):** Phase 8 continued + Phase 9 parallel prep
- **Wave 3 (Weeks 5-7):** Phase 9 - Performance optimization
- **Wave 4 (Weeks 8-10):** Phase 10 - Android API compliance
- **Wave 5 (Weeks 11-13):** Phase 11 - Background services
- **Wave 6 (Weeks 14-17):** Phase 12 - Code quality & cleanup

### Key Decision Points
1. **Phase 8 Sign-off:** Verify ANR elimination before proceeding to Phases 9-11
2. **Phase 10 Decision:** Defer foldable support if physical testing unavailable; document in ADR
3. **Phase 11 Decision:** Evaluate DI framework adoption; may defer to v3.1 if complexity too high
4. **Phase 12 Decision:** Defer Material3 stable update if not released by week 14; use alpha/beta if necessary

### Testing & Validation
- **Phase 8:** ANR profiler testing on Android 12+; memory profiler across screen transitions
- **Phase 9:** Query logging on TimetableViewModel; startup profiler on mid-range device
- **Phase 10:** Testing on Android 14, 15, 16 emulators; Galaxy Z Fold/Flip emulator for foldables
- **Phase 11:** Battery profiler for background sync; connection pool profiler for HttpClient
- **Phase 12:** UI frame rate profiling; deterministic testing for loading state race conditions

---

## Risk Register

| Risk | Impact | Likelihood | Mitigation |
|------|--------|-----------|-----------|
| ANR issues persist after Phase 8 | Critical | Low | Use Android Profiler SystemTrace; defer Phase 9+ until root cause found |
| Material3 1.9.0 stable not released | Medium | Medium | Use latest alpha/beta; defer production deployment if breaking changes |
| Foldable testing limited | Low | Medium | Document emulator-only testing; prioritize Galaxy Z Fold emulator |
| DI framework adoption causes regressions | Medium | Low | Evaluate only; defer to v3.1 if too risky |
| Flow-based loading introduces new race conditions | Medium | Low | Comprehensive unit tests; use cold Flow pattern |
| Edge-to-edge layout issues on Android 14 | Medium | Low | Test on Android 14/15 range; use WindowCompat |

---

## Version & Continuation Notes

**Milestone:** v3.0 Stability & Compliance  
**Previous Milestone:** v2.0 (ended at Phase 7.1)  
**Phase Numbering:** Continues from Phase 7.1 (starts at Phase 8)  
**Target Release Date:** 2026-06-30 (16 weeks from 2026-04-09)

This roadmap ensures 100% coverage of all 15 requirements (5 STAB, 3 ANDROID, 3 BG, 5 CODE) across 5 focused phases that are independently testable and deployable.

---

*Created: 2026-04-09*  
*Last Updated: 2026-04-09*
