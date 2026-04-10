---
status: investigating
trigger: "Timetable reloads from Dualis when screen wakes (activity recreates)"
created: 2026-04-10T00:00:00Z
updated: 2026-04-10T00:00:00Z
---

## Current Focus

hypothesis: TimetableViewModel is recreated on activity recreation, which calls init { loadLecturesForCurrentWeek() } every time, triggering fetch logic. The underlying issue is that the ViewModel is not preserved across activity recreation because it's created via `remember{}` in a Composable, which doesn't survive activity recreation.

test: Trace the ViewModel lifecycle across screen wake events and confirm whether UI-layer `remember{}` survives activity recreation or if the ViewModel is destroyed and recreated.

expecting: ViewModel is destroyed on activity recreation, causing init {} to fire and loadLecturesForCurrentWeek() to be called again, which may trigger unnecessary API calls even though cache exists.

next_action: Analyze the caching logic in LectureService to determine if cached data is being returned on recreated ViewModel calls, or if data is being fetched fresh.

## Symptoms

expected: After user stays logged in and screen wakes (activity recreates), timetable should display cached data without network request to Dualis

actual: Timetable appears to reload data from Dualis/cache when screen wakes, indicating activity recreation triggers a fresh fetch

errors: No explicit error messages, but network activity observed on screen wake

reproduction: 
1. Load timetable (data fetched from Dualis)
2. Lock screen / screen times out (activity enters onPause/onStop)
3. Unlock screen or screen wakes
4. Activity recreates (onDestroy -> onCreate -> onResume)
5. Timetable appears to refresh

started: Always occurs on activity recreation (screen wake, rotation, etc.)

## Eliminated

(none yet)

## Evidence

- **[1] MainActivity.onCreate() recomposes entire Compose tree** (line 76-159):
  - Calls `setContent { App(...) }` which recomposes the entire UI tree
  - Services are persisted in MainActivity via `mutableStateOf` variables
  - Session persisted via SecureStorage (survives activity recreation) ✓
  - BUT: Each call to setContent() means a fresh Compose tree starts
  - CRITICAL: On activity recreation (screen wake), onCreate() is called again

- **[2] App() composable is recreated with null timetableViewModel** (line 71-81):
  - TimetablePage receives `viewModel = timetableViewModel` parameter (line 260)
  - timetableViewModel is passed from MainActivity (line 75)
  - MainActivity's onCreate() DOES NOT pass timetableViewModel to App() - it's null
  - IMPLICATION: App() always receives timetableViewModel = null

- **[3] TimetablePage instantiation with null ViewModel** (line 78-101 in TimetablePage):
  - When viewModel parameter is null, creates new instance via remember():
    ```kotlin
    val actualViewModel = viewModel ?: remember(database, authenticationService, ...) {
        if (database != null && authenticationService != null && ...) {
            TimetableViewModel(...)
        } else {
            null
        }
    }
    ```
  - Dependencies: `remember(database, authenticationService, sharedHttpClient, sessionManager)`
  - These dependencies are stable (passed from App())
  - `remember{}` will preserve the ViewModel as long as dependencies don't change
  - BUT: If activity recreates and setContent() is called again, Compose tree is torn down

- **[4] TimetableViewModel.init{}** (line 46-48 in TimetableViewModel):
  - Calls `loadLecturesForCurrentWeek()` immediately on instantiation
  - HAPPENS: Every time ViewModel is created
  - IMPLICATION: If activity recreation causes ViewModel creation, init fires

- **[5] Compose Tree Lifecycle vs Activity Lifecycle**:
  - Activity lifecycle: onCreate -> onResume -> onPause -> onStop -> onDestroy
  - On screen wake: onResume is called (activity NOT destroyed if not in low memory condition)
  - But when onDestroy IS called (configuration changes, some screen wakes), new onCreate happens
  - setContent() in onCreate() tears down old Compose tree and creates new one
  - Compose-scoped remember() state is lost when tree is torn down
  - New tree calls remember() again, which creates a NEW ViewModel instance

- **[6] TimetableViewModel.init{}** triggers fetch chain:
  - `init { loadLecturesForCurrentWeek() }` → `loadLecturesForWeek(0)` 
  - Sets `uiState.isLoading = true`
  - Calls `lectureService.getLecturesForWeekStaged(weekOffset)`

- **[7] LectureService.getLecturesForWeekStaged()** (line 77-97 in LectureService):
  - Checks database cache first: `val cached = getLecturesForWeekFromDatabase(start, end)`
  - If cached, returns immediately with `Pair(cached, false)` - NO RELOAD
  - Database queries return cached lectures from before activity destruction
  - Sets `isReloading = false` (no background refresh needed if cache fresh)
  - IMPLICATION: Cache IS being used, but ViewModel shows loading state momentarily

- **[8] Visual Effect on Screen Wake**:
  - Activity destroys (activity recreates due to orientation, locale, etc.)
  - Compose tree torn down → all remember() state lost
  - setContent() called again → new Compose tree built
  - TimetablePage's remember() creates NEW ViewModel
  - TimetableViewModel.init {} calls loadLecturesForCurrentWeek()
  - UI shows loading spinner while service.getLecturesForWeekStaged() runs
  - Service returns cached data from database within ~1 second
  - UI updates with lectures (which are identical to before)
  - Net effect: "timetable reloaded" (momentary spinner, same data appears)

- **[9] The Real Problem**: 
  - TimetableViewModel is Composable-scoped, not Activity-scoped
  - Activity recreation = Compose tree recreation = ViewModel recreation
  - ViewModel creation triggers init { fetch } even if data is cached
  - User sees loading spinner on every activity recreation (screen wake)
  - DATA is correct (from cache) but EXPERIENCE is "reload"

## Resolution

root_cause: **ViewModel Scope Mismatch - Compose-Scoped Instead of Activity-Scoped**

The TimetableViewModel is created in TimetablePage using Compose's `remember{}` function (line 78-101 in TimetablePage.kt). This ties the ViewModel lifecycle to the Compose tree, NOT the Activity lifecycle.

**The Problem Chain:**
1. Activity.onCreate() calls `setContent { App(...) }` every time the activity is created/recreated
2. This tears down the old Compose tree and creates a new one
3. When the new Compose tree builds, TimetablePage's `remember{}` block runs
4. Since `remember{}` state is lost when the tree is torn down, a NEW TimetableViewModel is instantiated
5. TimetableViewModel.init { loadLecturesForCurrentWeek() } fires automatically
6. UI shows loading state while service layer checks database for cached data
7. Database returns cached lectures within ~1 second
8. User sees momentary loading spinner
9. UI updates with identical cached data
10. User perceives "timetable reloaded"

**Why the data is actually cached:**
- LectureService.getLecturesForWeekStaged() correctly queries the database first
- Database persists across activity recreation (Room database file survives)
- Cached lectures are found and returned with `isReloading = false`
- No network call happens (data is current)
- The "reload" is visual only (loading spinner), not a network reload

**The Real Issue:** Activity recreation triggers ViewModel recreation, causing UI to show loading state even though data is cached. User experiences this as "timetable reloading" rather than "activity recreating."

fix: **Option A (Recommended): Move ViewModel to Activity Scope**
- Keep ViewModel instantiation in MainActivity, not TimetablePage
- Pass ViewModel instance as a stable parameter to TimetablePage
- ViewModel survives activity recreation, init{} only fires once
- No loading spinner on activity recreation

**Option B: Skip Init Load if Cache Exists**
- Detect if cached data exists before calling loadLecturesForCurrentWeek()
- Only trigger loading UI if cache is empty
- Requires quick DB query in ViewModel.init

**Option C: Lazy ViewModel + Preserve State**
- Create a scoped state holder in MainActivity or NavHost
- Preserve ViewModel instance separate from Compose tree lifecycle
- Requires architectural changes to Compose navigation

verification: (pending implementation)

files_changed: []
