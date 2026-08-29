# Phase 8 Plan Overview

## Quick Start for Developers

This document summarizes the Phase 8 plan structure. **Read FULL plan in `08-PLAN.md`** before starting implementation.

---

## Six Task Groups (24 Atomic Tasks)

### Group 1: MainActivity Initialization Refactoring (4 tasks)
**Goal:** Move heavy work off main thread, render UI immediately  
**Timeline:** Week 1-2

1. **Task 1.1** - Early UI rendering with skeleton states (3-4 days)
2. **Task 1.2** - Async database initialization (2-3 days)
3. **Task 1.3** - Async HttpClient initialization + cleanup (2-3 days)
4. **Task 1.4** - Lazy-load API clients & parsers per feature (3-4 days)

**Key Files:** MainActivity.kt, DatabaseInitializer.kt, HttpClientInitializer.kt

---

### Group 2: ViewModel Lifecycle & Coroutine Management (3 tasks)
**Goal:** Prevent memory leaks by cancelling coroutines on ViewModel disposal  
**Timeline:** Week 2-3

1. **Task 2.1** - Add cleanup() method to all ViewModels (1-2 days)
2. **Task 2.2** - Call cleanup() on composable disposal (2-3 days)
3. **Task 2.3** - Timeout-based fallback for slow initialization (1-2 days)

**Key Files:** TimetableViewModel.kt, GradesViewModel.kt, DocumentsViewModel.kt, TimetablePage.kt, GradesPage.kt, DocumentsPage.kt

---

### Group 3: Service Initialization Ordering & Safety (2 tasks)
**Goal:** Document and enforce safe initialization order  
**Timeline:** Week 2-3

1. **Task 3.1** - Document service initialization order (1 day)
2. **Task 3.2** - Add null-safety checks for late-initialized services (2 days)

**Key Files:** SERVICE_INITIALIZATION_ORDER.md, DualisApiClient.kt, AuthenticationService.kt

---

### Group 4: Memory Leak Prevention & Verification (2 tasks)
**Goal:** Verify coroutine cleanup and memory stability via profiler  
**Timeline:** Week 3

1. **Task 4.1** - Verify no orphaned coroutines (Profiler) (1-2 days)
2. **Task 4.2** - Verify memory stable after 5+ transitions (2-3 days)

**Deliverables:** TEST_RESULTS_PROFILER.md, TEST_RESULTS_MEMORY.md

---

### Group 5: ANR Verification & Startup Performance (2 tasks)
**Goal:** Measure and verify startup performance meets success criteria  
**Timeline:** Week 4

1. **Task 5.1** - Verify ANR elimination via SystemTrace (2-3 days)
2. **Task 5.2** - Document startup performance baseline (1 day)

**Deliverables:** TEST_RESULTS_STARTUP.md

---

### Group 6: Integration Testing & Sign-Off (2 tasks)
**Goal:** Create automated tests and verify all success criteria  
**Timeline:** Week 4

1. **Task 6.1** - Create integration test suite (2-3 days)
2. **Task 6.2** - Phase 8 verification checklist & sign-off (1 day)

**Deliverables:** Unit/integration tests, VERIFICATION_CHECKLIST.md, SIGN_OFF.md

---

## Success Criteria (from ROADMAP)

- [ ] App responds within 2 seconds (T2 measurement)
- [ ] Zero ANR reports in next release cycle
- [ ] Memory stable after 5+ screen transitions (<5% growth)
- [ ] Skeleton UI shown during initialization (<500ms delay)
- [ ] All ViewModels have cleanup() implemented and called

---

## Critical Integration Points

**Most Risky Changes (HIGH impact, need careful review):**
1. MainActivity.onCreate() split (sync vs async) — affects all initialization order
2. Early setContent() call — must not break ViewModel initialization
3. ViewModel cleanup() in DisposableEffect — must fire before GC

**Medium Risk:**
1. Async database init — must handle early ViewModel queries
2. HttpClient closure in onDestroy() — must not cause crashes on rapid app restart
3. Lazy-loading DocumentsViewModel — must handle first-time access smoothly

**Low Risk (Mechanical Changes):**
1. Add cleanup() method to ViewModels (pattern reuse)
2. Add null checks to API clients (standard guard clauses)
3. Documentation and profiler testing (no code changes)

---

## Dependency Graph

```
Task 1.1 (Early UI Rendering)
  ├── Task 1.2 (Async DB)
  ├── Task 1.3 (Async HttpClient)
  ├── Task 1.4 (Lazy Loading)
  ├── Task 2.3 (Timeout Fallback)
  ├── Task 3.1 (Document Order)
  └── Task 3.2 (Null Safety)
      
Task 2.1 (Add cleanup())
  └── Task 2.2 (Call cleanup)
      └── Task 4.1 (Verify cleanup)

Task 2.2 (Call cleanup)
  └── Task 4.2 (Memory test)

Task 4.1 + 4.2 (Profiler tests)
  └── Task 5.1 (Startup perf)
      └── Task 5.2 (Baseline docs)

All Tasks
  └── Task 6.1 (Integration tests)
      └── Task 6.2 (Sign-off)
```

**Critical Path (fastest valid sequence):**
1.1 → 1.2 → 1.3 → 1.4 → 2.1 → 2.2 → 2.3 → 3.1 → 3.2 → 4.1 → 4.2 → 5.1 → 5.2 → 6.1 → 6.2

---

## Testing Strategy

**Automated (Runs on CI/CD):**
- Unit tests: cleanup() cancels scope, lazy loading works, timeout fallback renders
- Integration tests: startup <2s, memory stable, no orphaned coroutines

**Manual (Profiler-based):**
- Profiler CoroutineScope: verify "CANCELLED" state after cleanup
- Profiler Memory: verify heap stable after 5+ navigation cycles
- Profiler SystemTrace: verify main thread not blocked >500ms, T2 <2s

**Devices:**
- Baseline: Pixel 6 (2021, modern)
- Compatibility: Galaxy A12 or Pixel 4A (older/slower)
- OS versions: Android 12, 13, 14, 15 (if available)

---

## Reference Map

| What | Where | Why |
|------|-------|-----|
| Full task details | `08-PLAN.md` | Read before starting implementation |
| Architecture decisions | `08-CONTEXT.md` | Understand rationale for each decision |
| Business goals | `../ROADMAP.md` | Phase 8 section shows phase-level goals |
| Current MainActivity | `MainActivity.kt` (lines 55-227) | Know what to refactor |
| ViewModel pattern | `TimetableViewModel.kt` | Model for cleanup() pattern |
| Profiler setup | `TEST_SETUP.md` (to create) | How to run tests |

---

## Weekly Milestones

| Week | Goal | Key Tasks | Deliverable |
|------|------|-----------|-------------|
| 1 | Foundation: early UI + skeleton states | 1.1, 2.1, 3.1 | Early UI rendering works |
| 2 | Async init: database + HttpClient | 1.2, 1.3, 2.3 | Background initialization framework |
| 3 | Cleanup: ViewModels + null safety | 1.4, 2.2, 3.2 | All ViewModels can be disposed safely |
| 4 | Verification: profiler + tests + sign-off | 4.1, 4.2, 5.1, 5.2, 6.1, 6.2 | Phase 8 complete & signed off |

---

## How to Use This Plan

**For Task Assignment:**
1. Read this overview to understand structure
2. Pick a task from Group 1 or 2 (foundational work first)
3. Read full task details in `08-PLAN.md`
4. Follow "Task Steps" section exactly
5. Implement "Files to Modify" listed
6. Verify all "Success Criteria" before moving to dependent task

**For Code Review:**
1. Check that Implementation Details match task specification
2. Verify all Success Criteria pass
3. Cross-reference Architecture Decisions (D-01 through D-09) with implementation
4. Profiler tests run before/after to show improvement

**For Progress Tracking:**
- Maintain checklist in VERIFICATION_CHECKLIST.md
- One checkbox per task, mark complete when success criteria met
- Final sign-off when all 24 tasks complete

---

**Plan Created:** 2026-04-10  
**Phase Duration:** 3-4 weeks (16 business days)  
**Status:** Ready for Execution

