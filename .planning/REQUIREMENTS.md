# Requirements: v3.0 Stability & Compliance

## Milestone Goals
Fix critical ANR/freezing issues, ensure Android 15+ API compliance, optimize performance, and improve code quality through proper lifecycle management and resource cleanup.

**Phase Mapping:** See `.planning/ROADMAP.md` for detailed phase breakdown and execution strategy.

---

## 1. Stability & Performance (CRITICAL)

### STAB-01: Fix ANR/Freezing in MainActivity Initialization
**Phase: 8** (Critical Stability Fixes)
- [ ] Move database creation to background coroutine (Dispatchers.IO)
- [ ] Move HttpClient initialization to background coroutine
- [ ] Defer heavy service initialization (parsers, API clients) to background
- [ ] Only initialize essential core services on main thread
- [ ] Implement splash screen or loading state while initialization completes
- **Success Criteria:** App responds within 2 seconds on Android 12+ devices, no ANR reports in next release

### STAB-02: Fix Memory Leaks in ViewModels
**Phase: 8** (Critical Stability Fixes)
- [ ] Implement `onCleared()` in TimetableViewModel to cancel CoroutineScope
- [ ] Implement `onCleared()` in GradesViewModel to cancel CoroutineScope
- [ ] Implement `onCleared()` in DocumentsViewModel to cancel pending downloads
- [ ] Replace custom CoroutineScope with lifecycle-managed viewModelScope
- **Success Criteria:** Memory usage stable over 5+ screen transitions, no growing leaks in profiler

### STAB-03: Optimize Database Queries (N+1 Problem)
**Phase: 9** (Performance Optimization)
- [ ] Batch-load lecturer data in TimetableViewModel instead of per-lecture queries
- [ ] Implement Room multi-map query with `@Relation` for efficient joins
- [ ] Cache lecturer data locally in ViewModel
- [ ] Reduce weekly timetable load from 60+ queries to <5 queries
- **Success Criteria:** Timetable week loads in <500ms on mid-range device, zero UI lag

### STAB-04: Make File I/O Non-Blocking
**Phase: 9** (Performance Optimization)
- [ ] Convert FileViewer.openFile() to suspend function with Dispatchers.IO
- [ ] Convert FileViewer.saveFileWithDialog() to suspend with Dispatchers.IO
- [ ] Implement streaming for large documents instead of full array load
- [ ] Add progress callbacks for large file operations (>5MB)
- **Success Criteria:** No ANR when opening/saving documents >10MB, smooth progress indication

---

## 2. Android API Compliance (HIGH)

### ANDROID-01: Fix Edge-to-Edge Display Compatibility (Android 15+)
**Phase: 10** (Android API Compliance)
- [ ] Verify enableEdgeToEdge() is called correctly in MainActivity before setContent()
- [ ] Update androidx-activity to 1.9.0+ if not already
- [ ] Test WindowCompat.setDecorFitsSystemWindows() on Android 14/15/16
- [ ] Remove deprecated setStatusBarColor/setNavigationBarColor calls
- [ ] Verify inset handling in UI layouts for system bars
- **Success Criteria:** No deprecation warnings in Google Play Console, app displays correctly on Android 15+

### ANDROID-02: Support Large Screens & Foldables
**Phase: 10** (Android API Compliance)
- [ ] Improve isPhone() device detection using WindowMetrics.bounds
- [ ] Allow dynamic orientation adjustment based on current window size
- [ ] Test on Galaxy Z Fold/Z Flip physical devices or emulator
- [ ] Remove hard orientation lock for tablets in landscape/split-screen
- [ ] Handle hinge position detection for foldable devices
- **Success Criteria:** App works correctly on tablets, foldables, and split-screen mode; no UI corruption

### ANDROID-03: Remove Display Restrictions for Android 16+
**Phase: 10** (Android API Compliance)
- [ ] Remove orientation lock from MainActivity.onCreate() that restricts to portrait only
- [ ] Allow system to manage orientation based on user settings
- [ ] Test on Android 16 emulator with multi-window mode
- **Success Criteria:** App passes Android 16 large-screen compliance check, works in all window modes

---

## 3. Background Services & Lifecycle (MEDIUM)

### BG-01: Fix WorkManager Background Job Scheduling
**Phase: 11** (Background Services & Resource Management)
- [ ] Remove "5 minutes for testing" hardcoded value from LectureMonitorScheduler
- [ ] Only schedule WidgetSyncWorker if user explicitly enables it
- [ ] Replace hardcoded 15-minute interval with configurable constant
- [ ] Add early exit check before heavy initialization in initializeServices()
- [ ] Document why 15-minute minimum is enforced by WorkManager
- **Success Criteria:** Users can control background sync, battery drain reduced by 20%+

### BG-02: Fix HttpClient Resource Leaks
**Phase: 11** (Background Services & Resource Management)
- [ ] Store HttpClient reference in MainActivity
- [ ] Call sharedHttpClient.close() in onDestroy()
- [ ] Verify no connection pool leaks on app restart
- **Success Criteria:** No "too many open connections" errors, resource cleanup verified in Profiler

### BG-03: Lazy-Load DocumentsViewModel
**Phase: 11** (Background Services & Resource Management)
- [ ] Initialize DocumentsViewModel only when DocumentsPage is first accessed
- [ ] Use rememberSaveable with conditional initialization
- [ ] Defer DualisDocumentService until feature is accessed
- **Success Criteria:** App startup 15-20% faster for users who don't use documents

---

## 4. Code Quality & Consistency (MEDIUM)

### CODE-01: Improve Theme Initialization
**Phase: 12** (Code Quality & Cleanup)
- [ ] Preload theme preferences in Application.onCreate()
- [ ] Cache theme values to avoid repeated storage reads
- [ ] Move theme reads off composition path into LaunchedEffect
- **Success Criteria:** First frame rendered 100ms faster on slow devices

### CODE-02: Add Error Recovery for Database Initialization
**Phase: 12** (Code Quality & Cleanup)
- [ ] Wrap createRoomDatabase() in try-catch
- [ ] Implement fallback strategy (delete corrupted DB, show error screen)
- [ ] Log detailed error information for debugging
- **Success Criteria:** App handles DB corruption gracefully, user sees actionable error

### CODE-03: Consistent Lifecycle Management Across Platforms
**Phase: 12** (Code Quality & Cleanup)
- [ ] Document initialization order and lifecycle expectations
- [ ] Separate platform-specific initialization from common code
- [ ] Consider DI framework (Hilt, Koin) for consistent service creation
- **Success Criteria:** No duplicate service initialization, clear ownership of lifecycle

### CODE-04: Update Material3 to Stable Version
**Phase: 12** (Code Quality & Cleanup)
- [ ] Update Material3 from 1.9.0-alpha04 to 1.9.0 stable (when released)
- [ ] Test for API changes and breaking changes
- [ ] Verify UI rendering consistency
- **Success Criteria:** No pre-release warnings, code compatible with final Material3 release

### CODE-05: Fix Staged Loading Race Condition
**Phase: 12** (Code Quality & Cleanup)
- [ ] Replace polling-based second fetch with Flow-based reactive loading
- [ ] Implement proper loading states (Loading → Loaded → Refreshing)
- [ ] Eliminate race conditions between skeleton and full data
- **Success Criteria:** No UI flicker, deterministic data update order, no race conditions

---

## Requirements Coverage Summary

**Total Requirements:** 15

| Category | Count | Phases |
|----------|-------|--------|
| Stability & Performance | 4 | 8, 9 |
| Android API Compliance | 3 | 10 |
| Background Services & Lifecycle | 3 | 11 |
| Code Quality & Consistency | 5 | 12 |

**Coverage:** 100% (15/15 requirements mapped to phases)

---

## Future Requirements (Deferred)

- Performance profiling and optimization beyond identified issues
- Full accessibility audit (WCAG compliance)
- Comprehensive test coverage for new code
- Migration to official Dualis API (if ever released)

---

## Out of Scope

- **New Features:** v3.0 focuses solely on fixes and stability
- **Major Architecture Refactor:** Only targeted refactoring for identified issues
- **Platform-Specific Features:** Keeping existing platform parity
