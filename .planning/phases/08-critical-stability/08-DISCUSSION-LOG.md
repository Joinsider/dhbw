# Phase 8: Critical Stability Fixes - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.  
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-10  
**Phase:** 08-critical-stability  
**Areas discussed:** Initialization Priority & Database, ViewModel Lifecycle & HttpClient, Loading State & User Feedback, KMP-Specific ViewModel Pattern

---

## Area 1: Database Initialization Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Block UI until ready | Show splash/loading until database is fully initialized (safer, simpler) | |
| Initialize async, show UI immediately | Start database creation in background, render UI with placeholder states (faster perceived startup) | |
| You decide | Claude can choose the right balance based on the code | ✓ |

**User's choice:** You decide (Claude's discretion)  
**Notes:** Claude recommends async initialization with timeout-based fallback to skeleton UI. Balances safety (database ready before queries) with responsiveness.

---

## Area 2: HttpClient Initialization Timing

| Option | Description | Selected |
|--------|-------------|----------|
| Eager (startup) | Create HttpClient in MainActivity.onCreate() on background thread | |
| Lazy (on-demand) | Create only when first API call is needed (faster startup) | |
| You decide | Claude can defer it based on architectural constraints | ✓ |

**User's choice:** You decide (Claude's discretion)  
**Notes:** Claude recommends eager creation on background thread during startup to amortize setup cost and avoid slow first network call.

---

## Area 3: API Clients & Parsers Initialization

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-create on startup | Initialize all on background thread during MainActivity.onCreate() | |
| Lazy per-feature | Create only when the specific feature is first accessed (e.g., TimetableViewModel on tab open) | ✓ |
| You decide | Claude can choose based on complexity | |

**User's choice:** Lazy per-feature  
**Notes:** Reduces MainActivity initialization load; only creates what the user actually needs. DualisApiClient, HtmlParser, TimetableParser created on first use.

---

## Area 4: Visual Loading Feedback During Startup

| Option | Description | Selected |
|--------|-------------|----------|
| Splash screen | Full-screen overlay during startup, dismissed when ready | |
| Skeleton/placeholder states | Show empty screens with loading skeletons (Material Design pattern) | ✓ |
| Indeterminate progress bar | Top progress bar or centered spinner on the actual app UI | |
| No indicator | User just waits silently (not recommended) | |

**User's choice:** Skeleton/placeholder states  
**Notes:** Material Design approach; feels faster to users; allows early interaction with UI structure. Render actual app UI immediately, show loading states per page.

---

## Area 5: ViewModel Coroutine Cleanup (Android vs. KMP)

| Option | Description | Selected |
|--------|-------------|----------|
| Use viewModelScope | Add androidx.lifecycle:lifecycle-runtime-ktx dependency; use standard viewModelScope (recommended for Android) | |
| Manual onCleared() | Override onCleared() and cancel the custom CoroutineScope manually in each ViewModel | |
| DI Framework (Hilt) | Adopt Hilt for lifecycle-aware injection and automatic scope management (bigger change) | |

**User's choice:** "What is the best one for KMP Platform?" (Clarification question asked)  
**Notes:** User recognized that viewModelScope is Android-specific and doesn't work in commonMain where ViewModels are defined. Asked for KMP-aware recommendation.

### Follow-up: KMP-Specific ViewModel Pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Manual CoroutineScope + onCleared() | Keep custom CoroutineScope; each ViewModel implements onCleared() to cancel it (works across platforms) | |
| Move ViewModels to androidMain | Create Android-specific ViewModel subclasses in androidMain that use lifecycle-managed scopes | |
| Use expect/actual pattern | Define expect fun getViewModelScope() in common, actual in androidMain using lifecycle lib | |
| You decide | Claude can evaluate which fits your architecture best | ✓ |

**User's choice:** You decide (Claude's discretion)  
**Notes:** Claude recommends manual CoroutineScope with explicit cleanup() method (KMP-compatible). Maintains shared architecture; simpler than expect/actual for now. Defer lifecycle-aware pattern to Phase 12.

---

## Area 6: HttpClient Resource Cleanup

| Option | Description | Selected |
|--------|-------------|----------|
| Store in MainActivity, close in onDestroy() | Keep a reference and explicitly call httpClient.close() in onDestroy() | ✓ |
| Lazy singleton with cleanup on app exit | Use object pattern; rely on OS cleanup (less safe but simpler) | |
| Per-ViewModel instance (not recommended) | Each ViewModel gets its own HttpClient (wasteful, not viable) | |

**User's choice:** Store in MainActivity, close in onDestroy()  
**Notes:** Prevents "too many open connections" errors on app restart. Clear ownership of lifecycle. Essential for Phase 8 ANR elimination.

---

## Area 7: ViewModel Scope Strategy (Single vs. Isolated)

| Option | Description | Selected |
|--------|-------------|----------|
| Single shared scope per ViewModel | All work in one CoroutineScope(Dispatchers.IO), cancelled in onCleared() | |
| Isolated scopes per operation | Create new scopes for each major task (query, refresh) for better granularity | |
| You decide | Claude can choose based on what minimizes memory leaks | ✓ |

**User's choice:** You decide (Claude's discretion)  
**Notes:** Claude recommends single shared scope per ViewModel. Simpler than per-operation isolation; cancellation is all-or-nothing by design. Effective for leak prevention.

---

## Area 8: Documents Feature – Lazy Loading

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, lazy-load on first access | Initialize DocumentsViewModel only when DocumentsPage is first opened (faster startup) | ✓ |
| No, initialize eagerly at startup | Create DocumentsViewModel during MainActivity.onCreate() with other services | |
| You decide | Claude can assess if lazy-loading creates UI lag | |

**User's choice:** Yes, lazy-load on first access  
**Notes:** ~20% startup time saved for users who don't access documents. Defers DualisDocumentService initialization. Minimal UI lag risk due to skeleton states approach.

---

## Area 9: Navigation Safety – In-Flight Operations

| Option | Description | Selected |
|--------|-------------|----------|
| Cancel in-flight operations | If user navigates before init finishes, cancel the pending coroutines | ✓ |
| Let them complete | Allow background tasks to finish even if user navigates (may leak resources) | |
| You decide | Claude can decide based on impact | |

**User's choice:** Cancel in-flight operations  
**Notes:** Prevents memory leaks from tasks that outlive their UI context. Use proper scope cancellation.

---

## Claude's Discretion Areas

1. **Database initialization strategy (D-08):** Async with skeleton UI + timeout fallback chosen
2. **HttpClient timing (D-09):** Eager initialization on background thread chosen
3. **ViewModel scope isolation (D-07):** Single shared scope per ViewModel chosen
4. **KMP ViewModel lifecycle pattern (D-06):** Custom CoroutineScope with explicit cleanup() chosen

---

## Deferred Ideas

None — all discussion focused on Phase 8 scope. Future optimization opportunities:
- Phase 12 may revisit DI framework adoption (Hilt/Koin) for lifecycle-aware ViewModel management
- Phase 11 may optimize further based on battery profiling results

---

*Phase: 08-critical-stability*  
*Discussion completed: 2026-04-10*
