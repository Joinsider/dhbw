# Phase 11: Background Services & Resource Management - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents. Decisions are captured in CONTEXT.md.

**Date:** 2026-04-10  
**Phase:** 11-background-services-resource-management-weeks-11-13  
**Areas discussed:** Widget sync control, Sync interval configuration, Service initialization exit checks

---

## Widget Sync Control

| Option | Description | Selected |
|--------|-------------|----------|
| Always scheduled | WidgetSyncWorker runs every 30min regardless. Simplest approach; users always get widget updates. | |
| User toggle in Settings | Add a 'Enable widget sync' toggle in SettingsPage. Only schedule if user opts in. Better battery control. | |
| Implicit (smart) | Only schedule if user has active widgets. Detect via GlanceAppWidgetManager.getGlanceIds(). Complex but battery-friendly. | ✓ |

**User's choice:** Implicit (smart) - only schedule if widgets exist

**Notes:** User also requested manual refresh capability: "Use option 3 but also update widgets if the user manually reloads in the app or an automatic reload happens in the app"

**Implementation impact:** 
- Check `GlanceAppWidgetManager.getGlanceIds()` before scheduling periodic work
- Add `enqueueImmediate()` triggers on manual refresh/auto-reload in TimetablePage
- Battery benefit from avoiding background work for non-widget users
- Transparent to end user (no Settings toggle needed)

---

## Sync Interval Configuration

| Option | Description | Selected |
|--------|-------------|----------|
| Fixed documented constant | Keep hardcoded 15 minutes in code. Document why WorkManager enforces 15min minimum. No user configuration. | |
| Configurable constant | Create BUILD_CONFIG constant (e.g., WIDGET_SYNC_INTERVAL_MINUTES = 15). Easy to change for testing, but not user-facing. | ✓ |
| User-selectable in Settings | Add Settings toggle for 15min/30min/60min intervals. Users can balance battery vs. freshness. More complex. | |

**User's choice:** Configurable constant

**Notes:** Move hardcoded interval to a module-level constant. Support future flexibility without code hunting. Not user-facing in Settings to keep v3.0 scope minimal.

**Implementation impact:**
- WidgetSyncWorker: Extract `WIDGET_SYNC_INTERVAL_MINUTES` as changeable constant
- LectureMonitorScheduler: Remove "5 minutes for testing" comment; document WorkManager 15-minute minimum
- Enables testers to verify behavior at different intervals without rebuilding

---

## Service Initialization Exit Checks

| Option | Description | Selected |
|--------|-------------|----------|
| Skip for v3.0 | Don't add exit checks now. All services initialize unconditionally. Plan full optimization for Phase 12. | |
| Add checks before heavy work | Check preference flags before initializing services. E.g., if both notifications and documents disabled, skip those inits. Faster startup for minimal users. | ✓ |
| Context-dependent | For Phase 11: Only add exit check for WidgetSyncWorker scheduling (skip if no widgets). Defer broader checks to Phase 12. | |

**User's choice:** Add checks before heavy work

**Notes:** Conditional initialization based on user preferences. Early exit if features are disabled.

**Implementation impact:**
- Before NotificationManager init: Check if notifications OR lecture alerts enabled
- Before WidgetSyncWorker scheduling: Check if active widgets exist (per D-01)
- If user enables preferences later, services initialize on-demand via existing observer flows
- Estimated 10-20% startup improvement for minimal-feature users
- Guard clauses in `MainActivity.initializeServicesAsync()`

---

## Claude's Discretion

None — all decisions provided by user.

---

## Deferred Ideas

**1. Widget sync preference toggle (considered, deferred)**
   - Idea: "Enable widget background sync" toggle in SettingsPage
   - Decision: Smart detection (implicit scheduling) provides battery benefit without UI complexity
   - Deferred to: v3.1 or future phase if user requests explicit control

**2. Per-user configurable sync intervals (considered, deferred)**
   - Idea: "Widget refresh frequency" setting in SettingsPage (15min/30min/60min)
   - Decision: 30-minute interval is reasonable default; configurable constant sufficient for v3.0 testing
   - Deferred to: v3.1 if battery feedback indicates need for user control

---

*Discussion concluded 2026-04-10*
