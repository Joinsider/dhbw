---
phase: 11-background-services-resource-management-weeks-11-13
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt
  - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorScheduler.android.kt
  - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/widget/sync/WidgetSyncWorker.kt
  - composeApp/build.gradle.kts
autonomous: true
requirements:
  - BG-01
  - BG-02
  - BG-03

must_haves:
  truths:
    - "Widget background sync only runs if user has active widgets (battery efficiency)"
    - "LectureMonitorScheduler uses 15-minute interval with WorkManager minimum documented"
    - "NotificationManager and LectureMonitorScheduler skip initialization if notifications disabled"
    - "WidgetSyncWorker can be triggered manually when user manually refreshes timetable"
    - "HttpClient resource cleanup is verified via lifecycle-aware httpClientManager"
    - "App startup is faster for users without notifications or widgets enabled"
    - "Widget sync interval (30 minutes) is configurable via module constant for testing"
  artifacts:
    - path: "composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt"
      provides: "Conditional service initialization with early exit checks"
      min_lines: 350
    - path: "composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorScheduler.android.kt"
      provides: "Clean 15-minute interval constant with WorkManager documentation"
      min_lines: 70
    - path: "composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/widget/sync/WidgetSyncWorker.kt"
      provides: "Smart widget sync with active widget detection and manual triggers"
      min_lines: 150
  key_links:
    - from: "MainActivity.initializeServicesAsync()"
      to: "NotificationManager initialization"
      via: "Early exit check on notificationPreferencesInteractor flags"
      pattern: "if.*notificationsEnabled.*lectureAlertsEnabled"
    - from: "MainActivity.initializeServicesAsync()"
      to: "WidgetSyncWorker.schedulePeriodicSync()"
      via: "Widget existence check before scheduling"
      pattern: "GlanceAppWidgetManager.*getGlanceIds"
    - from: "MainActivity.onDestroy()"
      to: "HttpClientManager lifecycle cleanup"
      via: "httpClientManager observes lifecycle"
      pattern: "lifecycle\\.addObserver\\(httpClientManager\\)"
    - from: "LectureMonitorWorker.doWork()"
      to: "Manual widget refresh trigger"
      via: "Upon successful lecture check, call WidgetSyncWorker.enqueueImmediate()"
      pattern: "WidgetSyncWorker\\.enqueueImmediate"
---

<objective>
**What:** Optimize background service scheduling (WorkManager jobs), eliminate HttpClient resource leaks, and lazy-load heavy features to reduce battery drain and improve startup performance.

**Why:** 
- Users without widgets shouldn't trigger background sync (battery efficiency)
- Hardcoded "5 minutes for testing" must be removed and replaced with proper constants
- NotificationManager initialization should respect user preferences (skip if notifications disabled)
- App startup should be 15-20% faster for users without enabled features
- HttpClient resource leaks must be prevented via explicit cleanup on lifecycle

**Output:** 
- WidgetSyncWorker only schedules if active widgets detected
- LectureMonitorScheduler uses clean 15-min interval with WorkManager minimum documented
- Early-exit guards prevent unnecessary service initialization in MainActivity
- HttpClient cleanup verified through lifecycle observer pattern
- Manual refresh trigger points identified and implemented
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/ROADMAP.md §Phase 11
@.planning/REQUIREMENTS.md §BG-01, BG-02, BG-03
@.planning/phases/11-background-services-resource-management-weeks-11-13/11-CONTEXT.md
@.planning/phases/08-critical-stability/08-CONTEXT.md (Phase 8 completed: database async, HttpClient async, service initialization order)

## Code Locations Referenced

### MainActivity.kt
- Line 73: `httpClientManager` (lifecycle-aware cleanup already in place)
- Lines 100: `lifecycle.addObserver(httpClientManager)` registration
- Lines 165-289: `initializeServicesAsync()` — where all service initialization happens
- Lines 238-249: NotificationManager & LectureMonitorScheduler initialization (needs early exit check)
- Lines 252-254: LectureMonitorScheduler initialization
- Lines 258-282: Preference observer flow for dynamic scheduler control
- Line 287: `WidgetSyncWorker.schedulePeriodicSync()` call (needs early exit check per D-01)

### LectureMonitorScheduler.android.kt
- Line 24: `private const val REPEAT_INTERVAL_MINUTES = 15L // Changed to 5 minutes for testing` (MUST be cleaned per D-03)
- Lines 41-47: PeriodicWorkRequestBuilder with interval

### WidgetSyncWorker.kt
- Line 48: `private const val REPEAT_INTERVAL_MINUTES = 30L` (configurable constant per D-02)
- Lines 51-64: `schedulePeriodicSync()` method (add guard check for active widgets per D-01)
- Lines 67-75: `enqueueImmediate()` method (use for manual refresh triggers)
- Line 123: `GlanceAppWidgetManager(context).getGlanceIds(TimetableGlanceWidget::class.java)` available for widget detection

## Implementation Patterns

From Phase 8 (already working):
- HttpClientManager lifecycle observer pattern (MainActivity lines 72-100)
- Preference observer flow combining multiple flags (MainActivity lines 258-282)
- Early exit pattern in conditional service initialization
- GlanceAppWidgetManager widget detection (WidgetSyncWorker line 123)

</context>

<tasks>

<task type="auto">
  <name>Task 1: Remove "5 Minutes for Testing" from LectureMonitorScheduler and Document WorkManager Minimum (D-03)</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorScheduler.android.kt</files>
  <action>
Remove the misleading "Changed to 5 minutes for testing" comment from line 24 and replace with clear, professional constant definition and documentation per D-03.

Current code (line 24):
```kotlin
private const val REPEAT_INTERVAL_MINUTES = 15L // Changed to 5 minutes for testing
```

Replace with:
```kotlin
private const val REPEAT_INTERVAL_MINUTES = 15L
```

Add a documentation comment above the constant explaining the WorkManager minimum and why 15 minutes is appropriate:
```kotlin
/**
 * WorkManager enforces a minimum of 15 minutes for periodic work (Android API constraint).
 * This value is locked at 15 minutes for lecture monitoring. This interval is less
 * battery-sensitive than widget sync and provides reasonable responsiveness for lecture
 * change notifications.
 *
 * Rationale: Lecture checks happen less frequently than widget updates; 15-minute
 * minimum is acceptable for this use case. Not user-configurable in v3.0.
 */
private const val REPEAT_INTERVAL_MINUTES = 15L
```

Also remove the inline comment from line 37 that says "For testing, the 5-minute request will be clamped to 15 minutes" as it's now inaccurate.

Verification: grep confirms the constant is clean (no "5 minutes for testing" mention) and comment explains WorkManager minimum.
  </action>
  <verify>
    <automated>
grep -n "Changed to 5 minutes for testing" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorScheduler.android.kt || echo "✓ Comment removed"
grep -n "WorkManager enforces" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorScheduler.android.kt && echo "✓ Documentation added"
    </automated>
  </verify>
  <done>
LectureMonitorScheduler constant is clean, 15-minute interval is documented, no "5 minutes for testing" comment remains. WorkManager minimum constraint is explicitly documented in code.
  </done>
</task>

<task type="auto">
  <name>Task 2: Configure Widget Sync Interval Constant and Document WorkManager Minimum (D-02)</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/widget/sync/WidgetSyncWorker.kt</files>
  <action>
Ensure the widget sync interval constant in WidgetSyncWorker is properly documented per D-02. The constant already exists at line 48 (`private const val REPEAT_INTERVAL_MINUTES = 30L`), but needs explicit documentation explaining the WorkManager minimum and rationale for the chosen value.

Add a documentation block above the constant (before line 48):
```kotlin
/**
 * WorkManager enforces a minimum of 15 minutes for periodic work (Android API constraint).
 * Widget sync interval set to 30 minutes as a reasonable balance:
 * - Long enough to minimize battery impact
 * - Short enough to keep widgets reasonably fresh
 * - Can be easily adjusted here for testing without code hunt
 *
 * NOT user-facing in Settings (v3.0) — this constant allows testers to verify behavior
 * at different intervals by changing one line and rebuilding.
 *
 * Rationale: Widget updates are less critical than immediate data; 30-minute interval
 * provides good responsiveness without excessive battery drain. Users with no widgets
 * won't trigger this work at all (see smart scheduling in MainActivity.initializeServicesAsync).
 */
private const val REPEAT_INTERVAL_MINUTES = 30L
```

Verification: grep confirms constant is 30L and documentation explains both WorkManager minimum and rationale.
  </action>
  <verify>
    <automated>
grep -A 10 "private const val REPEAT_INTERVAL_MINUTES = 30L" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/widget/sync/WidgetSyncWorker.kt | grep -i "workmanager\|constraint\|minimum\|battery" && echo "✓ Documentation complete"
    </automated>
  </verify>
  <done>
Widget sync interval constant (30L minutes) is documented with WorkManager minimum explanation and rationale. Configurable constant is in place for testing flexibility without code hunting.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add Smart Widget Sync Guard — Only Schedule If Active Widgets Exist (D-01)</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/widget/sync/WidgetSyncWorker.kt</files>
  <action>
Implement the widget existence check in WidgetSyncWorker.schedulePeriodicSync() method (lines 51-64) per D-01. The method should only schedule periodic sync if the app detects active widgets via GlanceAppWidgetManager.

Current code (lines 51-64):
```kotlin
fun schedulePeriodicSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
        REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
    ).setConstraints(constraints).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME_PERIODIC,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
    Napier.d("Periodic widget sync scheduled every $REPEAT_INTERVAL_MINUTES min", tag = TAG)
}
```

Replace with:
```kotlin
fun schedulePeriodicSync(context: Context) {
    // Smart scheduling: only schedule if user has active widgets
    // Rationale: Battery efficiency; users without widgets never trigger background work
    val widgetManager = GlanceAppWidgetManager(context)
    val hasActiveWidgets = try {
        val widgetIds = widgetManager.getGlanceIds(TimetableGlanceWidget::class.java)
        widgetIds.isNotEmpty()
    } catch (e: Exception) {
        Napier.w("Failed to check active widgets: ${e.message}", tag = TAG)
        // On error, assume widgets may exist — err on the side of scheduling
        true
    }
    
    if (!hasActiveWidgets) {
        Napier.d("No active widgets detected — skipping periodic sync scheduling", tag = TAG)
        return
    }
    
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
        REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
    ).setConstraints(constraints).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME_PERIODIC,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
    Napier.d("✓ Periodic widget sync scheduled every $REPEAT_INTERVAL_MINUTES min (active widgets detected)", tag = TAG)
}
```

Verification: grep and code inspection confirm widget existence check is in place before scheduling, logging shows whether scheduling was skipped or executed.
  </action>
  <verify>
    <automated>
grep -A 5 "fun schedulePeriodicSync" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/widget/sync/WidgetSyncWorker.kt | grep -E "getGlanceIds|hasActiveWidgets" && echo "✓ Widget check implemented"
    </automated>
  </verify>
  <done>
WidgetSyncWorker.schedulePeriodicSync() now checks for active widgets before scheduling. Logs clearly indicate whether periodic sync was skipped (no widgets) or scheduled (widgets detected). Battery efficiency improved for users without widgets.
  </done>
</task>

<task type="auto">
  <name>Task 4: Add Early Exit Checks in MainActivity.initializeServicesAsync() for Conditional Initialization (D-04)</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
Implement early exit guards before initializing NotificationManager and LectureMonitorScheduler per D-04. These services should only initialize if notifications are actually enabled.

Find the section where NotificationManager and LectureMonitorScheduler are initialized (currently around lines 238-254). This is within the initializeServicesAsync() method.

Current code structure (lines 238-254):
```kotlin
// Create NotificationManager
val notificationDispatcher = NotificationDispatcher()
val nm = NotificationManager(
    monitor = lectureChangeMonitor,
    dispatcher = notificationDispatcher,
    preferences = prefInteractor
)
notificationManager = nm

// Register NotificationManager in ServiceLocator for Worker access
NotificationServiceLocator.initialize(nm)
Napier.d("NotificationManager initialized and registered", tag = "MainActivity")

// Initialize scheduler
val scheduler = LectureMonitorScheduler(applicationContext)
lectureMonitorScheduler = scheduler
Napier.d("LectureMonitorScheduler initialized", tag = "MainActivity")
```

Wrap this section with early-exit check:
```kotlin
// Early exit: Skip notification services if notifications disabled
val shouldInitializeNotifications = prefInteractor.notificationsEnabled.value || prefInteractor.lectureAlertsEnabled.value
if (shouldInitializeNotifications) {
    // Create NotificationManager
    val notificationDispatcher = NotificationDispatcher()
    val nm = NotificationManager(
        monitor = lectureChangeMonitor,
        dispatcher = notificationDispatcher,
        preferences = prefInteractor
    )
    notificationManager = nm

    // Register NotificationManager in ServiceLocator for Worker access
    NotificationServiceLocator.initialize(nm)
    Napier.d("✓ NotificationManager initialized and registered", tag = "MainActivity")

    // Initialize scheduler
    val scheduler = LectureMonitorScheduler(applicationContext)
    lectureMonitorScheduler = scheduler
    Napier.d("✓ LectureMonitorScheduler initialized (notifications enabled)", tag = "MainActivity")
} else {
    Napier.d("ℹ️  Notifications disabled — skipping NotificationManager and LectureMonitorScheduler initialization", tag = "MainActivity")
}
```

Note: The preference observer flow (lines 258-282) already handles dynamic start/stop of the scheduler if user enables notifications later, so this early exit is safe.

Verification: grep confirms early exit check is present before NotificationManager creation, logs show conditional initialization path.
  </action>
  <verify>
    <automated>
grep -B 2 "Create NotificationManager" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt | grep -E "shouldInitializeNotifications|Early exit" && echo "✓ Early exit check in place"
    </automated>
  </verify>
  <done>
MainActivity.initializeServicesAsync() now has early-exit guards for NotificationManager and LectureMonitorScheduler. If notifications are disabled, these services are skipped, reducing startup time by ~10-20% for users with notifications off. Preference observer flow still allows dynamic enable/disable.
  </done>
</task>

<task type="auto">
  <name>Task 5: Update WidgetSyncWorker Call in MainActivity to Use Smart Scheduling Guard (D-01 Integration)</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
Update the call to WidgetSyncWorker.schedulePeriodicSync() in MainActivity.initializeServicesAsync() (currently line 287) to work with the new smart scheduling guard. Since the guard is now internal to schedulePeriodicSync(), the call site needs no changes, but should be logged appropriately.

Current code (line 287):
```kotlin
// Schedule periodic widget background sync
WidgetSyncWorker.schedulePeriodicSync(applicationContext)
Napier.d("Widget periodic sync scheduled", tag = "MainActivity")
```

Update to:
```kotlin
// Schedule periodic widget background sync (only if active widgets detected)
Napier.d("Checking for active widgets and scheduling sync...", tag = "MainActivity")
WidgetSyncWorker.schedulePeriodicSync(applicationContext)
// Note: schedulePeriodicSync() internally checks for active widgets and skips if none found
Napier.d("Widget sync setup complete (see worker logs for scheduling result)", tag = "MainActivity")
```

This ensures the MainActivity log flow is clear about the conditional nature of widget scheduling.

Verification: grep confirms the call is in place and logging is clear about conditional scheduling.
  </action>
  <verify>
    <automated>
grep -A 3 "Schedule periodic widget background sync" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt | grep -E "schedulePeriodicSync|Checking for" && echo "✓ Smart scheduling integrated"
    </automated>
  </verify>
  <done>
MainActivity properly calls WidgetSyncWorker.schedulePeriodicSync() which now internally handles smart scheduling. Logging is clear that scheduling is conditional on widget existence.
  </done>
</task>

<task type="auto">
  <name>Task 6: Verify and Document HttpClient Lifecycle Cleanup (D-05 Verification)</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
Verify that HttpClient resource cleanup (D-05) is already in place from Phase 8 and document it clearly. Per D-05, this is a verification task only; no new code needed. HttpClient is stored in MainActivity.httpClientManager and cleaned up via lifecycle observer.

Verification steps:
1. Confirm httpClientManager is initialized at line 73
2. Confirm lifecycle.addObserver(httpClientManager) is called at line 100
3. Confirm HttpClientManager implements DefaultLifecycleObserver with onDestroy() calling httpClient.close()
4. Document this in comments for future maintainers

Add a clear comment block above the httpClientManager initialization (around line 73):
```kotlin
// Lifecycle-aware manager for HttpClient resource cleanup.
// The httpClientManager implements DefaultLifecycleObserver and calls httpClient.close()
// in onDestroy(), preventing "too many open connections" errors on app restart.
// This cleanup is registered with lifecycle.addObserver() at line ~100.
// See HttpClientManager class for implementation details.
// Requirement BG-02: HttpClient Resource Leaks (Phase 8 completion verified)
private val httpClientManager = HttpClientManager()
```

Then verify the HttpClientManager class exists and has proper cleanup:
```bash
find . -name "HttpClientManager.kt" -o -name "HttpClientManager.android.kt" | xargs grep -l "onDestroy\|close()"
```

Verification: Code inspection and grep confirm cleanup is in place, documented, and lifecycle-registered.
  </action>
  <verify>
    <automated>
grep -n "httpClientManager = HttpClientManager()" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && \
grep -n "lifecycle.addObserver(httpClientManager)" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && \
find composeApp -name "*HttpClientManager*" -type f | xargs grep -l "onDestroy\|close()" && echo "✓ HttpClient cleanup verified"
    </automated>
  </verify>
  <done>
HttpClient resource cleanup is verified as complete from Phase 8. The httpClientManager lifecycle observer pattern is documented and in place. No new code needed. BG-02 requirement satisfied.
  </done>
</task>

<task type="auto">
  <name>Task 7: Identify and Document Manual Refresh Trigger Points for Immediate Widget Sync (D-01 Enhancement)</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorWorker.kt</files>
  <action>
Identify trigger points where timetable data is manually refreshed and should trigger WidgetSyncWorker.enqueueImmediate() per D-01. This ensures widgets stay fresh when the app is actively used.

Create a documentation file (or add comments to code) identifying the three main refresh trigger points:

1. **User pulls-to-refresh on TimetablePage**
   - File: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/TimetablePage.kt` (or similar)
   - Pattern: Look for `PullRefreshIndicator` or `RefreshIcon` onClick handler
   - Action: After successful refresh, call `WidgetSyncWorker.enqueueImmediate(context)`

2. **User navigates between weeks/days and data auto-reloads**
   - File: `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt`
   - Pattern: Look for navigation state change listeners that trigger data reload
   - Action: After successful reload, trigger immediate widget sync

3. **Background notification check completes successfully**
   - File: `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorWorker.kt` (already exists)
   - Pattern: In LectureMonitorWorker.doWork(), after successful notificationManager.checkAndNotify()
   - Action: Add `WidgetSyncWorker.enqueueImmediate(context)` call

For LectureMonitorWorker (item 3 above), update the doWork() method (around line 104-107) to trigger immediate widget sync on success:

```kotlin
// Perform the monitoring check
Napier.d("🚀 Calling notificationManager.checkAndNotify()...", tag = TAG)
val success = notificationManager.checkAndNotify()

if (!success) {
    // ... existing retry logic
} else {
    // ENHANCEMENT per D-01: Trigger immediate widget refresh on successful check
    Napier.d("✓ Check succeeded — triggering immediate widget sync to keep widgets fresh", tag = TAG)
    WidgetSyncWorker.enqueueImmediate(context)
    
    Napier.d("╔════════════════════════════════════════════════════════════════════╗", tag = TAG)
    Napier.d("║  ✅ Background Worker: Completed successfully                      ║", tag = TAG)
    Napier.d("╚════════════════════════════════════════════════════════════════════╝", tag = TAG)
    return Result.success()
}
```

Verification: grep confirms WidgetSyncWorker.enqueueImmediate() calls are in place at identified trigger points.
  </action>
  <verify>
    <automated>
grep -r "enqueueImmediate" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorWorker.kt && echo "✓ Manual trigger added to LectureMonitorWorker"
    </automated>
  </verify>
  <done>
Manual refresh trigger points identified and documented. LectureMonitorWorker.doWork() now calls WidgetSyncWorker.enqueueImmediate() on successful checks. This ensures widgets stay fresh when app is actively used, even if periodic scheduler hasn't run yet. Enhancement per D-01 complete.
  </done>
</task>

<task type="auto">
  <name>Task 8: Add Integration Tests for Smart Widget Scheduling and Notification Conditional Init (Verification)</name>
  <files>composeApp/src/androidTest/kotlin/de/fampopprol/dhbwhorb/integration/BackgroundServicesIntegrationTest.kt</files>
  <action>
Create integration tests to verify the smart widget scheduling and notification conditional initialization work correctly. These tests ensure the new logic doesn't break in future refactors.

Create a new test file: `composeApp/src/androidTest/kotlin/de/fampopprol/dhbwhorb/integration/BackgroundServicesIntegrationTest.kt`

Test scenarios:

1. **Test: Widget sync not scheduled when no widgets exist**
   - Setup: Mock GlanceAppWidgetManager to return empty widget list
   - Action: Call WidgetSyncWorker.schedulePeriodicSync(context)
   - Assertion: Verify WorkManager did NOT enqueue periodic work
   - Logging: Check logs contain "No active widgets detected"

2. **Test: Widget sync scheduled when widgets exist**
   - Setup: Mock GlanceAppWidgetManager to return non-empty widget list
   - Action: Call WidgetSyncWorker.schedulePeriodicSync(context)
   - Assertion: Verify WorkManager DID enqueue periodic work
   - Logging: Check logs contain "active widgets detected"

3. **Test: Notification services skipped when notifications disabled**
   - Setup: Mock notificationPreferencesInteractor to return notificationsEnabled=false, lectureAlertsEnabled=false
   - Action: Call MainActivity.initializeServicesAsync()
   - Assertion: Verify notificationManager and lectureMonitorScheduler are null (not initialized)
   - Logging: Check logs contain "Notifications disabled — skipping"

4. **Test: Notification services initialized when notifications enabled**
   - Setup: Mock notificationPreferencesInteractor to return notificationsEnabled=true
   - Action: Call MainActivity.initializeServicesAsync()
   - Assertion: Verify notificationManager and lectureMonitorScheduler are non-null
   - Logging: Check logs contain "NotificationManager initialized"

5. **Test: HttpClient cleanup on activity destroy**
   - Setup: Create MainActivity, observe lifecycle
   - Action: Trigger onDestroy()
   - Assertion: Verify httpClientManager.onDestroy() was called and HttpClient.close() executed
   - Logging: Check Profiler shows no connection pool leaks

Implementation approach:
- Use Robolectric for Android framework mocking
- Use mockito or similar for dependency injection test doubles
- Focus on the guard logic, not the full service initialization (which is Phase 8 responsibility)
- Tests should be < 2 seconds each for quick iteration

Verification: Test file exists, all 5 test cases pass, coverage > 80% for new code paths.
  </action>
  <verify>
    <automated>
test -f "composeApp/src/androidTest/kotlin/de/fampopprol/dhbwhorb/integration/BackgroundServicesIntegrationTest.kt" && \
grep -c "fun test" composeApp/src/androidTest/kotlin/de/fampopprol/dhbwhorb/integration/BackgroundServicesIntegrationTest.kt && \
echo "✓ Integration tests created"
    </automated>
  </verify>
  <done>
Integration tests verify smart widget scheduling and conditional notification initialization. All 5 test scenarios pass. Test coverage ensures future refactors won't break the new logic. Tests run in < 2 seconds total.
  </done>
</task>

</tasks>

<verification>
**Phase 11 Completion Verification Checklist:**

1. **D-01: Smart Widget Sync (Implicit User Benefit)** ✓
   - [ ] WidgetSyncWorker.schedulePeriodicSync() checks for active widgets via GlanceAppWidgetManager.getGlanceIds()
   - [ ] Early exit if no widgets detected
   - [ ] Logs clearly indicate whether scheduling was skipped or executed
   - [ ] Manual refresh trigger in LectureMonitorWorker.doWork() calls WidgetSyncWorker.enqueueImmediate()

2. **D-02: Configurable Constant for Widget Sync Interval** ✓
   - [ ] WIDGET_SYNC_INTERVAL_MINUTES = 30L exists at WidgetSyncWorker line ~48
   - [ ] Documentation explains WorkManager 15-minute minimum
   - [ ] Comment explains rationale for 30-minute choice
   - [ ] Constant is NOT in user-facing Settings (kept complexity low)

3. **D-03: LectureMonitorScheduler Interval – Remove "5 Minutes for Testing"** ✓
   - [ ] Line 24 in LectureMonitorScheduler.android.kt has clean REPEAT_INTERVAL_MINUTES = 15L
   - [ ] "Changed to 5 minutes for testing" comment is removed
   - [ ] New documentation block explains WorkManager 15-minute minimum
   - [ ] Comment explains why 15 minutes is locked (not configurable for users)

4. **D-04: Early Exit Checks in initializeServicesAsync()** ✓
   - [ ] Guard clause before NotificationManager creation checks notificationPreferencesInteractor flags
   - [ ] Guard clause before LectureMonitorScheduler creation uses same flags
   - [ ] Logs indicate "Notifications disabled — skipping" path when appropriate
   - [ ] Preference observer flow (lines 258-282) still handles dynamic enable/disable

5. **D-05: HttpClient Resource Cleanup Verification** ✓
   - [ ] httpClientManager lifecycle observer is registered (line 100)
   - [ ] HttpClientManager.onDestroy() calls httpClient.close()
   - [ ] Code is documented in comments for future maintainers
   - [ ] Phase 8 completion verified; no new code changes needed

**Requirement Coverage:**

- **BG-01: Fix WorkManager Background Job Scheduling** ✓
  - [ ] Remove hardcoded "5 minutes for testing" (D-03)
  - [ ] Only schedule WidgetSyncWorker if active widgets detected (D-01)
  - [ ] Configurable constant for intervals (D-02)
  - [ ] Early exit before heavy initialization (D-04)
  - [ ] Document WorkManager 15-minute minimum (D-03)

- **BG-02: Fix HttpClient Resource Leaks** ✓
  - [ ] Store HttpClient reference in MainActivity (Phase 8 complete)
  - [ ] Call close() in onDestroy() via httpClientManager (Phase 8 complete)
  - [ ] Verified in Profiler; no connection pool leaks
  - [ ] D-05: Verification complete, no new changes needed

- **BG-03: Lazy-Load DocumentsViewModel** ✓
  - [ ] Already completed in Phase 8 (D-04 of Phase 8 CONTEXT)
  - [ ] DocumentsViewModel initialized only on DocumentsPage access
  - [ ] Uses rememberSaveable with conditional initialization
  - [ ] No new changes needed for Phase 11; verify in existing code

**Integration Tests:**
- [ ] BackgroundServicesIntegrationTest.kt created with 5 test scenarios
- [ ] All tests pass
- [ ] Coverage > 80% for new code paths
- [ ] Tests run in < 2 seconds total

**Manual Testing:**
- [ ] Build app, monitor logs during startup
- [ ] Verify "No active widgets detected" log if no widgets installed
- [ ] Add widget, rebuild, verify "active widgets detected" log
- [ ] Toggle notifications off in Settings, restart app, verify "Notifications disabled — skipping" log
- [ ] Monitor Android Profiler for HttpClient resource cleanup on app destroy
- [ ] Verify battery usage with and without notifications/widgets enabled
- [ ] Confirm startup time is 15-20% faster for users with notifications and widgets disabled

</verification>

<success_criteria>
Phase 11 is complete when:

1. **Battery Efficiency**: Widget sync only scheduled if user has active widgets (zero unnecessary background work)

2. **Readable Code**: LectureMonitorScheduler has clean 15-minute constant with WorkManager minimum documented

3. **Fast Startup**: App initializes NotificationManager and LectureMonitorScheduler only if notifications enabled, saving ~10-20% startup time for users with notifications off

4. **Resource Cleanup**: HttpClient resource cleanup verified via lifecycle-aware httpClientManager; no "too many open connections" errors on app restart

5. **Manual Refresh**: When user manually refreshes timetable or background check succeeds, widgets sync immediately via WidgetSyncWorker.enqueueImmediate()

6. **Configurable Testing**: Widget sync interval (30 minutes) easily adjustable via module constant without code hunting

7. **Testable**: Integration tests verify smart scheduling, conditional init, and resource cleanup; coverage > 80%

8. **Backward Compatible**: All Phase 8 initialization order decisions still respected; preference observer flow still handles dynamic enable/disable of scheduler

9. **Documented**: All decisions (D-01 through D-05) are operationalized in code with clear comments and logging

10. **Requirement Compliance**: All BG-01, BG-02, BG-03 requirements fully addressed and verified

</success_criteria>

<output>
After completion, create `.planning/phases/11-background-services-resource-management-weeks-11-13/11-01-SUMMARY.md` documenting:
- Files modified with line numbers
- Git commit hash
- Test results (integration test names and pass/fail)
- Battery profiling results (if available)
- Startup time improvement measured
- Logging verification (screenshot of logs showing smart scheduling, conditional init, cleanup)
</output>
