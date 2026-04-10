# Phase 11: Background Services & Resource Management - Context

**Gathered:** 2026-04-10  
**Status:** Ready for planning

---

## Phase Boundary

Phase 11 fixes background job scheduling, eliminates HttpClient resource leaks (already largely addressed in Phase 8), and optimizes app startup by controlling when heavy services are initialized. Success means:
- Users can control or implicitly benefit from background sync (widget updates happen smartly)
- Background sync interval is configurable for testing and future adjustments
- App startup is faster for users with minimal feature usage (conditional initialization based on preferences)
- No "too many open connections" errors from HttpClient
- WidgetSyncWorker only runs if needed (user has active widgets or manual refresh triggers it)
- Service initialization respects feature preferences and exits early when appropriate

---

## Implementation Decisions

### Background Widget Sync Scheduling

**D-01: Smart Widget Sync (Implicit User Benefit)**
- WidgetSyncWorker only schedules periodic sync if the app detects active widgets via `GlanceAppWidgetManager.getGlanceIds(TimetableGlanceWidget::class.java)`
- Rationale: Battery efficiency; users who don't use widgets never trigger background work. No explicit toggle needed—transparent to user.
- Implementation: In `MainActivity.initializeServicesAsync()`, add pre-check before calling `WidgetSyncWorker.schedulePeriodicSync(context)`. Skip scheduling if no widget instances exist.
- Manual refresh support: When user manually refreshes timetable in-app OR data auto-reloads, immediately call `WidgetSyncWorker.enqueueImmediate(context)` to keep widgets in sync.
- Rationale for manual triggers: Ensures widgets are fresh when app is actively used, even if periodic scheduler hasn't run yet.

### Sync Interval Configuration

**D-02: Configurable Constant for Widget Sync Interval**
- Create a module-level constant in WidgetSyncWorker: `private const val WIDGET_SYNC_INTERVAL_MINUTES = 30L` (or appropriate value)
- NOT user-facing in Settings (keep complexity low for v3.0)
- Purpose: Allow easy adjustment for testing (change one line) and future flexibility without code hunt
- Rationale: WorkManager enforces 15-minute minimum; 30 minutes is reasonable default. Configurable constant lets testers verify behavior at different intervals without rebuilding.
- Document: Add comment explaining WorkManager minimum and rationale for chosen interval

**D-03: LectureMonitorScheduler Interval – Remove "5 Minutes for Testing" Hardcode**
- Remove line 24: `private const val REPEAT_INTERVAL_MINUTES = 15L // Changed to 5 minutes for testing`
- Replace with clear constant: `private const val REPEAT_INTERVAL_MINUTES = 15L`
- Add comment: "WorkManager enforces minimum 15-minute interval for periodic work (Android API constraint)"
- Keep interval locked at 15 minutes (not configurable for users in v3.0)
- Rationale: Lecture checks are less battery-sensitive than widget sync; 15-minute minimum is acceptable. No need for user control.

### Conditional Service Initialization

**D-04: Early Exit Checks in initializeServicesAsync()**
- Before initializing heavy services, check if they're actually needed:
  - **NotificationManager & LectureMonitorScheduler**: Only initialize if `notificationPreferencesInteractor.notificationsEnabled` is true OR `lectureAlertsEnabled` is true
  - **WidgetSyncWorker scheduling**: Only schedule periodic sync if widgets exist (per D-01)
  - **DocumentsViewModel & DualisDocumentService**: Already deferred to first access (Phase 8) — no additional changes needed
- Rationale: App startup ~10-20% faster for users who disable notifications and have no widgets; fewer resources consumed on devices with limited memory
- Implementation: Add guard clauses in `initializeServicesAsync()` before constructing NotificationManager, LectureChangeMonitor, and LectureMonitorScheduler
- Trade-off: If user enables notifications later (in Settings), services initialize on-demand via the preference observer flow (already in place at MainActivity lines 258-282)

### HttpClient Resource Cleanup

**D-05: Reconfirm Phase 8 HttpClient Management – No Changes for Phase 11**
- HttpClient is already stored in `MainActivity.httpClientManager` and cleaned up via lifecycle observer (MainActivity lines 72-100)
- Requirement BG-02 is already satisfied by Phase 8 implementation
- Phase 11 only verifies/documents this; no new code needed
- Rationale: Phase 8 solved the resource leak issue; Phase 11 confirms and may add additional profiling if needed

---

## Claude's Discretion

No areas deferred to Claude. All decisions locked by user input.

---

## Canonical References

Downstream agents MUST read these before planning or implementing:

### Phase Requirements & Goals
- `.planning/ROADMAP.md` §Phase 11 — Phase goal, business impact, success criteria, dependencies
- `.planning/REQUIREMENTS.md` §BG-01, BG-02, BG-03 — Specific requirements and success metrics

### Phase 8 Context (Prerequisite)
- `.planning/phases/08-critical-stability/08-CONTEXT.md` — Foundation decisions on service initialization, ViewModel lifecycle, HttpClient management
  - Critical: D-03, D-04 (HttpClient cleanup and DocumentsViewModel lazy loading are already done)

### Key Code Locations
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt`:
  - Line 165-289: `initializeServicesAsync()` method where all service initialization happens
  - Line 72-100: `HttpClientManager` lifecycle observer for cleanup
  - Line 252-254: `LectureMonitorScheduler` initialization
  - Line 287: `WidgetSyncWorker.schedulePeriodicSync()` call (needs early exit check)
  - Line 258-282: Preference observer flow for dynamic start/stop of scheduler

- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/services/notifications/LectureMonitorScheduler.android.kt`:
  - Line 24: Hardcoded `REPEAT_INTERVAL_MINUTES` (remove "5 minutes for testing" comment)
  - Line 41-47: PeriodicWorkRequestBuilder with interval

- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/widget/sync/WidgetSyncWorker.kt`:
  - Line 48: `REPEAT_INTERVAL_MINUTES = 30L` constant (make it easy to adjust)
  - Line 51-64: `schedulePeriodicSync()` method (add guard check for active widgets)
  - Line 67-75: `enqueueImmediate()` method (use for manual refresh triggers)

- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt`:
  - Line 310-330: DocumentsPage (already lazily initializes DocumentsViewModel per Phase 8)
  - Verify: All preference interactors passed to SettingsPage for user control

### Android API Documentation (for researcher)
- [WorkManager minimum interval constraints](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest) — WorkManager enforces 15-minute minimum for periodic work

### Related Patterns in Codebase
- Preference observer pattern (MainActivity lines 258-282): Model for conditional scheduler control
- GlanceAppWidgetManager usage (WidgetSyncWorker lines 123-124): Pattern for detecting active widgets

---

## Existing Code Insights

### Reusable Assets
- **Preference observer flow** (MainActivity lines 258-282): Already combines notification flags and triggers scheduler start/stop; extend pattern for WidgetSyncWorker
- **GlanceAppWidgetManager** (WidgetSyncWorker line 123): Already used in `pushStateToWidgets()`; can reuse for widget existence check
- **WidgetSyncWorker.schedulePeriodicSync()** (line 51-64): Already has constraint builder; just add pre-check before calling
- **NotificationPreferencesInteractor** (App.kt, MainActivity): Already passes preference states; reuse in conditional checks

### Established Patterns
- **Lifecycle-based cleanup** (HttpClientManager): Pattern to follow for any new resource-heavy services
- **Conditional initialization via LaunchedEffect/lifecycleScope.launch**: Pattern in MainActivity for async work based on lifecycle state
- **Early service return if not initialized** (WidgetSyncWorker lines 82-94): Bootstrap pattern if cold-started; reuse pattern for conditional init checks

### Integration Points
- **MainActivity.initializeServicesAsync()** (line 165): Central coordination point for all service startup; add guard clauses here
- **NotificationPreferencesInteractor**: Already wired to SettingsPage; flows already exposed for preference observation
- **WidgetSyncWorker.schedulePeriodicSync()**: Called from MainActivity line 287; add pre-check here for widget existence

---

## Specific Ideas

### Widget Existence Check Implementation
When checking for active widgets before scheduling, consider:
- Call `GlanceAppWidgetManager(context).getGlanceIds(TimetableGlanceWidget::class.java)` — returns empty list if no widgets
- Only schedule if list is non-empty
- Consider also re-scheduling if user adds a widget while app is running (may be out of scope for v3.0, but note for future)

### Manual Refresh Trigger Points
Identify places where timetable data is manually refreshed and trigger `WidgetSyncWorker.enqueueImmediate()`:
- User pulls-to-refresh on TimetablePage
- User navigates between weeks/days and data auto-reloads
- Background notification check completes successfully (in NotificationManager.checkAndNotify())

### Notification Preference Check
Before creating NotificationManager in `initializeServicesAsync()`:
```
if (notificationPreferencesInteractor.notificationsEnabled.value || notificationPreferencesInteractor.lectureAlertsEnabled.value) {
  // Create NotificationManager, LectureChangeMonitor, LectureMonitorScheduler
} else {
  Napier.d("Notifications disabled - skipping NotificationManager initialization", tag = "MainActivity")
}
```

---

## Deferred Ideas

**Widget preference toggle considered and deferred:**
- Idea: "Enable widget background sync" toggle in SettingsPage (user control)
- Decision: Not needed for v3.0. Smart detection (only schedule if widgets exist) provides battery benefit without UI complexity
- Deferred to: v3.1 or Phase 12 if user requests explicit control

**Per-user configurable sync intervals considered and deferred:**
- Idea: "Widget refresh frequency" setting in SettingsPage (15min/30min/60min)
- Decision: Not needed for v3.0. 30-minute interval is reasonable; configurable constant is sufficient for testing
- Deferred to: v3.1 if battery feedback indicates need for user control

---

*Phase: 11-background-services-resource-management-weeks-11-13*  
*Context gathered: 2026-04-10*
