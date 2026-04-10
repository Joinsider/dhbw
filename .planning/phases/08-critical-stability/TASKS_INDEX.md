# Phase 8 Tasks Index

**Quick task lookup and progress tracking for Phase 8 implementation.**

---

## Task Group 1: MainActivity Initialization Refactoring

### 1.1: Refactor MainActivity.onCreate() for Early UI Rendering
- **Lines in PLAN.md:** 20-54
- **Duration:** 3-4 days
- **Blocks:** 1.2, 1.3, 1.4, 2.3
- **Key Files to Modify:**
  - `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (lines 55-227)
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/pages/TimetablePage.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/pages/GradesPage.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/pages/DocumentsPage.kt`
- **Success Criteria:**
  - `setContent()` called within 50ms of onCreate() start ✓
  - App UI renders with skeleton states in <500ms ✓
  - No crash when pressing back before initialization completes ✓
  - All initialization happens in background ✓

**Status:** [ ] Not Started

---

### 1.2: Extract Database Initialization to Background Coroutine
- **Lines in PLAN.md:** 58-101
- **Duration:** 2-3 days
- **Blocks:** 2.1, 3.1, 3.2
- **Depends On:** 1.1
- **Key Files to Modify:**
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/storage/database/DatabaseInitializer.kt` (NEW)
  - `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt`
  - ViewModels with database access (add null safety)
- **Success Criteria:**
  - Database initialization on Dispatchers.IO ✓
  - No main thread blocking (<100ms impact) ✓
  - App UI renders before database ready ✓
  - First ViewModel query waits for database ✓

**Status:** [ ] Not Started

---

### 1.3: Extract HttpClient Initialization to Background Coroutine
- **Lines in PLAN.md:** 105-160
- **Duration:** 2-3 days
- **Blocks:** 2.1, 3.1, 3.2
- **Depends On:** 1.1
- **Key Files to Modify:**
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/network/HttpClientInitializer.kt` (NEW)
  - `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (add onDestroy cleanup)
  - API clients (add null safety)
- **Success Criteria:**
  - HttpClient initialization on Dispatchers.IO ✓
  - No main thread blocking ✓
  - App interactive before HttpClient ready ✓
  - HttpClient closed in onDestroy(), no connection pool leaks ✓

**Status:** [ ] Not Started

---

### 1.4: Lazy-Load API Clients & Parsers per Feature
- **Lines in PLAN.md:** 164-226
- **Duration:** 3-4 days
- **Blocks:** 2.1, 3.1, 3.2
- **Depends On:** 1.1, 1.2
- **Key Files to Modify:**
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/LectureService.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/DocumentService.kt` (create with lazy init)
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/DualisLectureService.kt`
  - `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (remove eager init)
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/pages/DocumentsPage.kt`
- **Success Criteria:**
  - TimetableViewModel creates DualisLectureService on first lecture load ✓
  - DocumentsViewModel created on first DocumentsPage render ✓
  - DualisDocumentService never created if Documents tab never accessed ✓
  - Startup time reduced 15-20% for Timetable-only users ✓

**Status:** [ ] Not Started

---

## Task Group 2: ViewModel Lifecycle & Coroutine Management

### 2.1: Implement cleanup() Method Pattern in All ViewModels
- **Lines in PLAN.md:** 260-321
- **Duration:** 1-2 days
- **Blocks:** 2.2, 4.1
- **Depends On:** None
- **Key Files to Modify:**
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt`
  - All other ViewModels in `ui/**/viewModels/`
- **Success Criteria:**
  - All ViewModels have cleanup() method ✓
  - Method cancels their coroutineScope ✓
  - No runtime errors when cleanup() called ✓

**Status:** [ ] Not Started

---

### 2.2: Call ViewModel.cleanup() on Composable Disposal
- **Lines in PLAN.md:** 325-376
- **Duration:** 2-3 days
- **Blocks:** 4.1, 4.2
- **Depends On:** 2.1
- **Key Files to Modify:**
  - `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (add cleanup in onDestroy)
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/pages/TimetablePage.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/pages/GradesPage.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/pages/DocumentsPage.kt`
- **Success Criteria:**
  - Profiler shows no "CANCELLED" scopes hanging around ✓
  - No orphaned coroutine warnings in logs ✓
  - Memory stable after 5+ navigation cycles ✓

**Status:** [ ] Not Started

---

### 2.3: Add Timeout-Based Initialization Fallback
- **Lines in PLAN.md:** 380-423
- **Duration:** 1-2 days
- **Depends On:** 1.1, 1.2, 1.3
- **Key Files to Modify:**
  - `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (add timeout logic)
  - TimetableViewModel, GradesViewModel (add fallback)
- **Success Criteria:**
  - App renders UI within 500ms regardless of service status ✓
  - UI transitions smoothly from skeleton to real data ✓
  - No "stuck on splash" on slow devices ✓

**Status:** [ ] Not Started

---

## Task Group 3: Service Initialization Ordering & Safety

### 3.1: Document Service Initialization Order
- **Lines in PLAN.md:** 466-517
- **Duration:** 1 day
- **Depends On:** 1.1, 1.2, 1.3, 1.4
- **Key Files to Create:**
  - `.planning/phases/08-critical-stability/SERVICE_INITIALIZATION_ORDER.md` (NEW)
- **Key Files to Modify:**
  - `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (add comment link)
  - All service classes (add dependency comments)
- **Success Criteria:**
  - SERVICE_INITIALIZATION_ORDER.md created and complete ✓
  - Each service/ViewModel has dependency comments ✓
  - No circular dependencies ✓

**Status:** [ ] Not Started

---

### 3.2: Add Null Safety Checks for Late-Initialized Services
- **Lines in PLAN.md:** 521-579
- **Duration:** 2 days
- **Depends On:** 3.1
- **Key Files to Modify:**
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/DualisApiClient.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/AuthenticationService.kt`
  - `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/LectureService.kt`
  - All ViewModels accessing database
- **Success Criteria:**
  - No null reference exceptions on early access ✓
  - Graceful error messages in UI ✓
  - Logs show which service wasn't ready ✓

**Status:** [ ] Not Started

---

## Task Group 4: Memory Leak Prevention & Verification

### 4.1: Verify No Orphaned Coroutines (Profiler Testing)
- **Lines in PLAN.md:** 622-657
- **Duration:** 1-2 days (profiler work)
- **Depends On:** 2.1, 2.2
- **Test Report:** `.planning/phases/08-critical-stability/TEST_RESULTS_PROFILER.md`
- **Success Criteria:**
  - All ViewModels show "CANCELLED" state after DisposableEffect cleanup ✓
  - No "RUNNING" coroutines after back navigation ✓
  - 0 orphaned scopes after app exit ✓

**Status:** [ ] Not Started

---

### 4.2: Verify Memory Stability (Memory Profiler)
- **Lines in PLAN.md:** 661-714
- **Duration:** 2-3 days (profiler work)
- **Depends On:** 1.1-1.4, 2.1-2.2
- **Test Report:** `.planning/phases/08-critical-stability/TEST_RESULTS_MEMORY.md`
- **Success Criteria:**
  - Memory <5% growth after 5+ navigation cycles ✓
  - No instance count growth for ViewModels/Services ✓
  - Heap profiler shows garbage collection working ✓

**Status:** [ ] Not Started

---

## Task Group 5: ANR Verification & Startup Performance

### 5.1: Verify ANR Elimination via SystemTrace Profiler
- **Lines in PLAN.md:** 757-829
- **Duration:** 2-3 days (multiple test runs)
- **Depends On:** 1.1-1.4
- **Test Report:** `.planning/phases/08-critical-stability/TEST_RESULTS_STARTUP.md`
- **Measurement Points:**
  - T1 (first paint): <500ms ✓
  - T2 (first interaction): <2 seconds ✓
  - T3 (full data): <3 seconds ✓
  - No main thread blocks >500ms ✓
- **Test Devices:**
  - Pixel 6 (baseline, modern)
  - Galaxy A12 or Pixel 4A (older/slower)

**Status:** [ ] Not Started

---

### 5.2: Document Startup Performance Baseline
- **Lines in PLAN.md:** 833-854
- **Duration:** 1 day
- **Depends On:** 1.4, 5.1
- **Deliverable:** Comparison of eager vs. lazy initialization
- **Success Criteria:**
  - Baseline metrics recorded ✓
  - Lazy loading shows measurable improvement ✓
  - Report shows device + OS version ✓

**Status:** [ ] Not Started

---

## Task Group 6: Integration Testing & Sign-Off

### 6.1: Create Phase 8 Integration Test Suite
- **Lines in PLAN.md:** 898-973
- **Duration:** 2-3 days
- **Depends On:** 1.1-1.4, 2.1-2.2
- **Test Files to Create:**
  - `composeApp/src/commonTest/kotlin/.../ViewModelCleanupTest.kt`
  - `composeApp/src/commonTest/kotlin/.../LazyLoadingTest.kt`
  - `composeApp/src/androidTest/kotlin/.../StartupPerformanceTest.kt`
  - `.planning/phases/08-critical-stability/TEST_SETUP.md`
- **Test Coverage:**
  - Unit: cleanup() cancels scope ✓
  - Unit: lazy-loading initializes on-demand ✓
  - Unit: timeout fallback renders UI ✓
  - Integration: startup <2s ✓
  - Integration: memory stable ✓

**Status:** [ ] Not Started

---

### 6.2: Phase 8 Verification Checklist & Sign-Off
- **Lines in PLAN.md:** 977-1007
- **Duration:** 1 day
- **Depends On:** All previous tasks
- **Deliverables:**
  - `.planning/phases/08-critical-stability/VERIFICATION_CHECKLIST.md` (NEW)
  - `.planning/phases/08-critical-stability/SIGN_OFF.md` (NEW)
- **Final Checklist:**
  - Startup Performance (Task 5.1) ✓
  - ANR Elimination (Task 5.1) ✓
  - Memory Stability (Task 4.2) ✓
  - ViewModel Cleanup (Task 2.1-2.2) ✓
  - Service Initialization (Task 1.1-1.4, 3.1) ✓
  - Integration Tests Pass (Task 6.1) ✓

**Status:** [ ] Not Started

---

## Progress Tracking

| Group | Task | Status | Days Remaining |
|-------|------|--------|-----------------|
| 1 | 1.1 - Early UI | [ ] | 3-4 |
| 1 | 1.2 - Async DB | [ ] | 2-3 |
| 1 | 1.3 - Async HttpClient | [ ] | 2-3 |
| 1 | 1.4 - Lazy Load APIs | [ ] | 3-4 |
| 2 | 2.1 - cleanup() methods | [ ] | 1-2 |
| 2 | 2.2 - Call cleanup | [ ] | 2-3 |
| 2 | 2.3 - Timeout fallback | [ ] | 1-2 |
| 3 | 3.1 - Document order | [ ] | 1 |
| 3 | 3.2 - Null safety | [ ] | 2 |
| 4 | 4.1 - Coroutine verification | [ ] | 1-2 |
| 4 | 4.2 - Memory verification | [ ] | 2-3 |
| 5 | 5.1 - Startup verification | [ ] | 2-3 |
| 5 | 5.2 - Performance baseline | [ ] | 1 |
| 6 | 6.1 - Integration tests | [ ] | 2-3 |
| 6 | 6.2 - Sign-off | [ ] | 1 |

**Total Estimated:** 3-4 weeks (16-17 business days)

---

## How to Use This Index

1. **For Task Assignment:** Find task in this index, copy line reference, then read full details in `08-PLAN.md`
2. **For Progress Tracking:** Mark [ ] with [X] when starting, update "Status" column above
3. **For Dependency Understanding:** Check "Depends On" column before selecting next task
4. **For Success Verification:** Find task, scroll to "Success Criteria", verify all checkmarks before moving on

---

**Index Created:** 2026-04-10  
**Plan Size:** 1,007 lines  
**Total Tasks:** 24 (organized in 6 groups)

