# Phase 8: Critical Stability Fixes — Planning Complete

**Status:** Ready for Execution (2026-04-10)

---

## Deliverables

This directory contains the complete Phase 8 implementation plan, including:

### Core Planning Documents

1. **[08-PLAN.md](./08-PLAN.md)** ⭐ START HERE
   - 1,007 lines of detailed task specifications
   - 24 atomic, testable implementation tasks
   - Organized in 6 logical task groups
   - Each task includes:
     - Decision references (D-01 through D-09)
     - Implementation steps
     - Files to modify (with absolute paths)
     - Success criteria and acceptance conditions
     - Dependencies and estimated duration

2. **[PLAN_OVERVIEW.md](./PLAN_OVERVIEW.md)** — Executive Summary
   - Quick-start guide for developers
   - 6 task groups at a glance
   - Critical integration points flagged
   - Weekly milestone breakdown
   - Testing strategy overview

3. **[TASKS_INDEX.md](./TASKS_INDEX.md)** — Task Lookup
   - Quick reference for all 24 tasks
   - Jump directly to task details in PLAN.md
   - Progress tracking checklist
   - Dependency graph at a glance

### Context & Reference

4. **[08-CONTEXT.md](./08-CONTEXT.md)** — Architecture Decisions
   - 9 core decisions (D-01 through D-09)
   - Rationale for each decision
   - Known risks and mitigations
   - Deferred ideas for future phases

5. **[08-DISCUSSION-LOG.md](./08-DISCUSSION-LOG.md)** — Discussion Record
   - Phase context gathering session
   - Questions asked and answered
   - Decisions made during planning

---

## Quick Start

### For Developers
1. Read [PLAN_OVERVIEW.md](./PLAN_OVERVIEW.md) (5 minutes)
2. Pick a task from [TASKS_INDEX.md](./TASKS_INDEX.md)
3. Jump to that task in [08-PLAN.md](./08-PLAN.md) (full details)
4. Follow "Task Steps" section
5. Implement files listed under "Files to Modify"
6. Verify all "Success Criteria" ✓

### For Project Managers
1. Review [PLAN_OVERVIEW.md](./PLAN_OVERVIEW.md) for timeline (3-4 weeks)
2. Check [TASKS_INDEX.md](./TASKS_INDEX.md) for progress tracking
3. Refer to "Risk Mitigations" section in [08-PLAN.md](./08-PLAN.md) for blockers
4. Weekly milestone tracking in PLAN_OVERVIEW.md

### For Code Reviewers
1. Cross-reference implementation against Architecture Decisions in [08-CONTEXT.md](./08-CONTEXT.md)
2. Verify all "Success Criteria" met before approving
3. Check profiler test results (will be in TEST_RESULTS_*.md files)
4. Ensure proper cleanup() implementation per D-06 pattern

---

## Phase 8 Success Criteria (ROADMAP.md)

- [ ] **Startup Performance:** App responds within 2 seconds (T2 <2s on Android 12+)
- [ ] **ANR Elimination:** Zero ANR reports in next release cycle
- [ ] **Memory Stability:** Memory usage remains stable after 5+ screen transitions (<5% growth)
- [ ] **Visual Loading:** Skeleton UI shown during initialization (<500ms delay)
- [ ] **Test Coverage:** Unit + integration tests verify all above criteria

---

## Task Groups at a Glance

| Group | Title | Tasks | Duration | Key Outcome |
|-------|-------|-------|----------|------------|
| 1 | MainActivity Refactoring | 1.1-1.4 | 10-14 days | Early UI rendering + async services |
| 2 | ViewModel Lifecycle | 2.1-2.3 | 5-7 days | Proper cleanup + memory leak prevention |
| 3 | Service Ordering | 3.1-3.2 | 3 days | Documented initialization + null safety |
| 4 | Memory Verification | 4.1-4.2 | 3-5 days | Profiler validation of cleanup |
| 5 | Startup Performance | 5.1-5.2 | 3-4 days | ANR elimination + baseline metrics |
| 6 | Testing & Sign-Off | 6.1-6.2 | 3-4 days | Automated tests + verification checklist |

**Total Estimated:** 3-4 weeks

---

## Architecture Decisions (D-01 through D-09)

All 9 architecture decisions from the Phase 8 context gathering are fully mapped to implementation tasks:

- **D-01:** Lazy Per-Feature API Clients → Task 1.4
- **D-02:** Skeleton/Placeholder States → Task 1.1
- **D-03:** HttpClient Cleanup → Task 1.3
- **D-04:** DocumentsViewModel Lazy Load → Task 1.4
- **D-05:** Cancel In-Flight Operations → Task 2.2
- **D-06:** ViewModel CoroutineScope Pattern → Task 2.1
- **D-07:** Scope Isolation (Single Shared) → Task 2.1
- **D-08:** Async Database + Timeout → Task 1.2, 2.3
- **D-09:** Eager HttpClient on Background → Task 1.3

---

## Integration Points (High Risk Changes)

| Change | File | Risk | Task | Mitigation |
|--------|------|------|------|-----------|
| Split onCreate() into sync+async | MainActivity.kt | HIGH | 1.1 | Early setContent, background heavy work |
| Async database initialization | DatabaseInitializer.kt | MEDIUM | 1.2 | Null checks in ViewModels, timeout fallback |
| Async HttpClient initialization | HttpClientInitializer.kt | MEDIUM | 1.3 | Explicit close() in onDestroy |
| Lazy API client loading | LectureService.kt | MEDIUM | 1.4 | Lazy delegates + null checks |
| ViewModel cleanup() calling | TimetablePage, etc. | MEDIUM | 2.2 | DisposableEffect + MainActivity.onDestroy |
| Service initialization ordering | Multiple | MEDIUM | 3.1, 3.2 | Documented dependencies + null guards |

---

## Files to Create (Total: 7 new files)

```
Phase 8 Directory (.planning/phases/08-critical-stability/):
├── 08-PLAN.md ⭐ (1,007 lines - main deliverable)
├── PLAN_OVERVIEW.md (executive summary)
├── TASKS_INDEX.md (task lookup)
├── SERVICE_INITIALIZATION_ORDER.md (to create during Task 3.1)
├── TEST_RESULTS_PROFILER.md (to create during Task 4.1)
├── TEST_RESULTS_MEMORY.md (to create during Task 4.2)
└── TEST_RESULTS_STARTUP.md (to create during Task 5.1)

Code Directory (composeApp/src/):
├── commonMain/.../data/storage/database/DatabaseInitializer.kt (NEW)
├── commonMain/.../data/network/HttpClientInitializer.kt (NEW)
├── commonTest/.../ViewModelCleanupTest.kt (NEW)
├── commonTest/.../LazyLoadingTest.kt (NEW)
└── androidTest/.../StartupPerformanceTest.kt (NEW)
```

---

## Files to Modify (Total: ~20 files)

**Android Layer:**
- MainActivity.kt (major refactor - lines 55-227)

**Common Layer (ViewModels):**
- TimetableViewModel.kt (add cleanup)
- GradesViewModel.kt (add cleanup)
- DocumentsViewModel.kt (add cleanup + lazy load)

**Common Layer (Services):**
- LectureService.kt (lazy getter)
- DocumentService.kt (create + lazy init)
- DualisLectureService.kt (lazy parsers)
- DualisApiClient.kt (null checks)
- AuthenticationService.kt (null checks)

**Common Layer (UI):**
- TimetablePage.kt (add DisposableEffect)
- GradesPage.kt (add DisposableEffect)
- DocumentsPage.kt (add DisposableEffect + rememberSaveable)

**See [08-PLAN.md](./08-PLAN.md) for complete list per task.**

---

## Testing Strategy

### Automated (CI/CD)
- Unit tests: cleanup(), lazy-loading, timeout fallback
- Integration tests: startup performance, memory stability

### Manual (Profiler)
- **Profiler CoroutineScope:** Verify "CANCELLED" after cleanup
- **Memory Profiler:** Verify heap stable after 5+ navigation cycles
- **SystemTrace:** Verify main thread <500ms blocks, T2 <2s

### Test Devices
- Baseline: Pixel 6 (modern)
- Compatibility: Galaxy A12 or Pixel 4A (older/slower)

**See [08-PLAN.md](./08-PLAN.md) Task Group 5-6 for full testing procedures.**

---

## Phase Dependencies

- **Blocked By:** None (Phase 8 is critical path)
- **Blocks:** Phase 9 (Performance Optimization), Phase 10+ depend on stable initialization

---

## How This Plan Maps to ROADMAP.md

From `ROADMAP.md` Phase 8 section:
```
Requirement STAB-01: Fix ANR/Freezing in MainActivity Initialization
  ↓ Addressed by Tasks 1.1-1.4, 5.1

Requirement STAB-02: Fix Memory Leaks in ViewModels
  ↓ Addressed by Tasks 2.1-2.2, 4.2

Success Criteria: Startup <2s, ANR elimination, Memory stable, Skeleton UI, Tests pass
  ↓ Verified by Tasks 5.1, 5.2, 4.2, 1.1, 6.1-6.2
```

---

## Decision Records (ADR Format)

This plan serves as a detailed implementation ADR for Phase 8. Key decisions captured:

- **ADR-Phase8-01:** Split MainActivity.onCreate() into sync (UI) + async (heavy work)
- **ADR-Phase8-02:** Render skeleton UI before database/HttpClient ready
- **ADR-Phase8-03:** Lazy-load API clients per feature (D-01, D-04)
- **ADR-Phase8-04:** Custom CoroutineScope with explicit cleanup() for KMP ViewModels (D-06)
- **ADR-Phase8-05:** Timeout-based fallback for slow initialization (D-08)

See [08-CONTEXT.md](./08-CONTEXT.md) for full decision rationale.

---

## Verification & Sign-Off

- **Pre-Implementation:** [ ] All stakeholders reviewed PLAN.md and PLAN_OVERVIEW.md
- **Per-Task:** [ ] Developer marks success criteria ✓ before moving to dependent task
- **Phase Completion:** [ ] All 24 tasks complete + all test results in (Task 6.2)
- **Sign-Off:** [ ] Tech lead reviews SIGN_OFF.md and approves Phase 8 closure

---

## Contact & Questions

For questions about this plan:
1. Check [08-CONTEXT.md](./08-CONTEXT.md) for decision rationale
2. Review [08-DISCUSSION-LOG.md](./08-DISCUSSION-LOG.md) for discussion context
3. Jump to specific task in [08-PLAN.md](./08-PLAN.md) for implementation details

---

**Plan Status:** ✅ Ready for Execution  
**Created:** 2026-04-10  
**Version:** 1.0  
**Prepared by:** Software Architect (GSD Planner)

---

*Next Step: Assign Task 1.1 to first developer. Estimated start-to-completion: 3-4 weeks.*
