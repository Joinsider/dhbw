# Phase 12: Code Quality & Cleanup - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents. For human reference and compliance reviews only.

**Date:** 2026-04-10  
**Facilitator:** Claude Code (Haiku 4.5)  
**Participants:** User (project lead)

---

## Gray Areas Presented

1. **Theme Initialization Strategy** — Preload timing, caching mechanism, change propagation
2. **Database Error Recovery** — Auto-recovery strategy, fallback behavior, logging approach
3. **DI Framework & Lifecycle Management** — Framework adoption decision, service ownership documentation
4. **Loading State Race Condition Fix** — State pattern choice, race prevention mechanism, implementation scope
5. **Material3 Version Update** — (User clarified: skip stable update; keep alpha for Expressive)

---

## Discussion Transcript

### Area 1: Theme Initialization Strategy

**Q1.1: When should theme preferences preload?**
- **Options presented:**
  - Application.onCreate()
  - MainActivity.onCreate() before setContent
  - First LaunchedEffect in App composable (Recommended)
- **Selected:** First LaunchedEffect in App composable
- **Rationale:** Simplest approach; theme guaranteed available when UI renders; no need for platform-specific setup

**Q1.2: How should cached theme values be stored?**
- **Options presented:**
  - Immutable data class in remember block
  - CompositionLocal with lazy initialization (Recommended)
  - Separate mutableStateOf for each preference
- **Selected:** CompositionLocal with lazy initialization
- **Rationale:** Cleaner API; LocalThemePrefs.current prevents prop drilling; matches Compose patterns

**Q1.3: How should theme changes be applied when user toggles dark mode?**
- **Options presented:**
  - Re-read prefs from storage on every toggle
  - Batch theme changes in next LaunchedEffect cycle (Recommended)
  - Use StateFlow driven by interactor
- **Selected:** Batch theme changes in next LaunchedEffect cycle
- **Rationale:** Avoids multiple recompositions; consistent with existing MainActivity preference observer pattern

---

### Area 2: Database Error Recovery

**Q2.1: When database initialization fails, what should happen?**
- **Options presented:**
  - Auto-delete and retry silently (Recommended)
  - Show error screen first; let user choose
  - One auto-retry, then persistent error
- **Selected:** Auto-delete and retry silently
- **Rationale:** Most DB corruption is transient; silent recovery catches it without user friction

**Q2.2: If auto-recovery fails, what should the app do?**
- **Options presented:**
  - Non-blocking error banner; offline mode
  - Persistent blocking error screen (Recommended)
  - Log and crash with stack trace
- **Selected:** Persistent blocking error screen
- **Rationale:** Unrecoverable corruption is rare; clear error forces reinstall and ensures data consistency

**Q2.3: What details should be logged when database initialization fails?**
- **Options presented:**
  - Full stack trace + exception type + file size
  - Structured Napier logs (Recommended)
  - Silent logging to local crash file
- **Selected:** Structured Napier logs
- **Rationale:** Consistent with existing app logging; easier to parse in Play Console crash reports

---

### Area 3: DI Framework & Lifecycle Management

**Q3.1: Should Phase 12 evaluate or adopt a DI framework (Hilt/Koin)?**
- **Options presented:**
  - Keep manual DI; document ownership
  - Evaluate Koin for Phase 13 (Recommended)
  - Adopt Hilt for Android only
- **Selected:** Evaluate Koin for Phase 13
- **Rationale:** Manual DI is stable; late-cycle framework adoption is risky; research first, plan adoption separately

**Q3.2: What should Phase 12 document to establish clear lifecycle ownership?**
- **Options presented:**
  - Sequence diagram + service cleanup checklist
  - ARCHITECTURE.md additions: Lifecycle & Ownership section (Recommended)
  - ADR: Service Lifecycle & Cleanup Pattern
- **Selected:** ARCHITECTURE.md additions
- **Rationale:** Extends existing codebase documentation; discoverable for future developers; matches workflow conventions

---

### Area 4: Loading State Race Condition Fix

**Q4.1: How should we implement deterministic loading state transitions?**
- **Options presented:**
  - StateFlow with sealed state class
  - Separate StateFlows: isLoading, data, isRefreshing (Recommended)
  - Flow.combine() to merge and filter
- **Selected:** Separate StateFlows
- **Rationale:** Flexible; matches existing mutableStateOf pattern; easier to test and understand

**Q4.2: How should we prevent the race between initial load and refresh?**
- **Options presented:**
  - Cancel previous job before starting new one
  - Mutex serialization lock (Recommended)
  - Cold Flow with replay=1
- **Selected:** Mutex serialization lock
- **Rationale:** Prevents overlapping operations; clear intent; standard Kotlin pattern

**Q4.3: Which ViewModels should get the race-condition fix?**
- **Options presented:**
  - All ViewModels (TimetableViewModel, GradesViewModel, DocumentsViewModel)
  - Critical paths only: TimetableViewModel + GradesViewModel
  - Create base ViewModel class
- **Selected:** All ViewModels
- **Rationale:** Consistent UX across app; all three have user-visible loading states

---

### Area 5: Material3 Version Update

**User Clarification:** "Don't update any material3 version as I am using material 3 expressive for my project which still has many components only in the alpha version"

**Decision Locked:** Defer Material3 stable update indefinitely; keep alpha for Expressive components.

**Rationale:** Stable release would break Expressive; functionality > version stability.

---

## Key Decisions Locked

| Decision | Locked By | Rationale |
|----------|-----------|-----------|
| Theme: LaunchedEffect in App composable | User | Simplest KMP-compatible approach |
| Theme: CompositionLocal caching | User | Prevents prop drilling; cleaner API |
| Theme: Batch changes in LaunchedEffect | User | Avoids multiple recompositions |
| DB: Auto-delete and retry silently | User | Most corruption is transient |
| DB: Blocking error screen on unrecoverable failure | User | Ensures data consistency |
| DB: Napier structured logging | User | Consistent with app conventions |
| DI: Keep manual; evaluate Koin for Phase 13 | User | Late-cycle adoption too risky |
| DI: Document in ARCHITECTURE.md | User | Extends existing docs |
| Loading: Separate StateFlows | User | Flexible; matches existing patterns |
| Loading: Mutex serialization | User | Prevents overlapping operations |
| Loading: Apply to all ViewModels | User | Consistent UX |
| Material3: Keep alpha for Expressive | User | Functional requirement |

---

## Timeline & Notes

**Session Start:** 2026-04-10 ~11:30 UTC  
**Session End:** 2026-04-10 ~12:15 UTC  
**Duration:** ~45 minutes  
**Questions Asked:** 11 main questions + 2 clarifications  
**All Areas Resolved:** Yes, no outstanding ambiguities

---

## Scope Adherence

✓ All discussions stayed within Phase 12 boundary (code quality, not new features)  
✓ No deferred ideas identified (Material3 Expressive clarification was a constraint, not scope creep)  
✓ All decisions align with Phase 8, 10, 11 prior context  
✓ No downstream blockers identified

---

*Discussion facilitated by Claude Code*  
*User confirmed all locked decisions on 2026-04-10*
