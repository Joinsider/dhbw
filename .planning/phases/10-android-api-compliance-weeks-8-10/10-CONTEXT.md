# Phase 10: Android API Compliance (Weeks 8-10) - Context

**Gathered:** 2026-04-10  
**Status:** Ready for planning

---

## Phase Boundary

Phase 10 updates the app to be fully compliant with Android 15+ APIs, support large screens and foldables, and remove deprecated system window calls. Success means:
- Zero deprecation warnings in Google Play Console for targetSdkVersion
- App displays correctly on Android 15+ without deprecated system bar color calls
- Edge-to-edge rendering enabled with proper inset handling
- App works correctly on tablets, foldables, and split-screen mode
- Hinge position detection functional on foldable devices
- Dynamic orientation follows user settings and adapts to window size
- Passes Android 16 large-screen compliance checks

---

## Implementation Decisions

### Device Detection Strategy

**D-01: Hybrid WindowMetrics + Configuration Fallback**
- Primary: Use `Display.getWindowMetrics()` to measure actual window bounds in real-time
- Fallback: Use `Configuration.screenLayout` + `smallestScreenWidthDp` for older Android versions or edge cases where WindowMetrics is unavailable
- Rationale: WindowMetrics provides precise bounds detection for foldables and large screens; Configuration fallback ensures compatibility with older APIs
- Implementation: Update `MainActivity.isPhone()` to try WindowMetrics first, fall back to current Configuration logic if needed
- Updated code location: `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` lines 251-263

### Windows Inset Handling

**D-02: Default enableEdgeToEdge() + Automatic Inset Respect**
- Rely on `enableEdgeToEdge()` call (already in place at MainActivity line 73) combined with Compose's automatic WindowInsets handling
- Do NOT manually apply `WindowInsets.systemBars.asPaddingValues()` unless specific visual requirements demand it
- Rationale: Material Design default is battle-tested; Compose respects insets automatically in most layouts; avoids over-engineering
- Result: System bars draw under content; Compose adjusts content padding automatically
- Validation: Verify no content is hidden behind system bars across phone, tablet, and foldable layouts

### Foldable Support

**D-03: Basic Hinge Detection via FoldingFeature API**
- Implement detection of fold state (folded/unfolded) using `androidx.window.FoldingFeature`
- Detect hinge position and orientation (vertical/horizontal)
- Allow layouts to adapt when fold state changes (e.g., switch between single-column and two-column layouts)
- Do NOT implement full hinge-aware responsive layouts (respecting hinge position in draw bounds) — that's future work
- Rationale: Satisfies compliance requirement without over-engineering; basic detection gives decent UX on actual foldables
- Implementation: Add fold state detection in MainActivity or shared composition state; expose fold state to composables that need it
- Testing: Test on Galaxy Z Fold/Z Flip emulator (available in Android Studio) or physical devices if available

### Orientation Management

**D-04: Sensor-Driven Rotation (User Preference)**
- Remove hard portrait orientation lock from `MainActivity.onCreate()` for Android 16+
- Allow system to manage orientation based on user's rotation preference (Settings > Display > Rotation preference)
- For Android 15 and earlier: Maintain current portrait lock for phones detected as phones; tablets allow all orientations
- For Android 16+: Allow rotation for all devices (no orientation lock)
- Rationale: Respects Android 16+ compliance requirements; follows user's system settings; doesn't override user preferences
- Implementation: Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` (or appropriate version for Android 16) and conditionally set/unset `requestedOrientation`
- Note: Remove `@SuppressLint("SourceLockedOrientationActivity")` annotation once orientation lock is removed

### Status/Navigation Bar Colors

**D-05: Claude's Discretion - No Explicit Color Calls**
- Do NOT call deprecated `setStatusBarColor()` or `setNavigationBarColor()`
- Rely on `enableEdgeToEdge()` and system defaults for bar colors
- If theming is needed in the future: Use Compose's `systemBarsDarkContentLighting` or dynamic color APIs, not direct Window calls
- Rationale: Avoids deprecation; system defaults work well with edge-to-edge; future-proof for Android 17+

---

## Claude's Discretion

The following areas where user deferred to Claude's judgment:

1. **Status/Navigation Bar Colors (D-05):** No explicit color setting; rely on `enableEdgeToEdge()` defaults and system theming

---

## Canonical References

Downstream agents MUST read these before planning or implementing:

### Phase Requirements & Goals
- `.planning/ROADMAP.md` §Phase 10 — Phase goal, business impact, success criteria
- `.planning/REQUIREMENTS.md` §ANDROID-01, ANDROID-02, ANDROID-03 — Specific requirements for edge-to-edge, large screens, and Android 16+ compliance

### Key Code Locations
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` — Main entry point, `enableEdgeToEdge()` call (line 73), orientation logic (lines 92-97), `isPhone()` detection (lines 251-263)

### Android API Documentation (for researcher)
- [Edge-to-Edge displays (Android Developers)](https://developer.android.com/develop/ui/compose/layouts/insets) — Compose WindowInsets and edge-to-edge guidance
- [Large screens support (Android Developers)](https://developer.android.com/develop/ui/compose/layouts/adaptive/window-size-classes) — Window size classes and responsive design
- [Foldables support (Android Developers)](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables) — FoldingFeature API, hinge detection
- [Sensor-driven rotation (Android Developers)](https://developer.android.com/develop/ui/compose/layouts/adaptive/device-orientation) — Handling device orientation and rotation

### Architecture References
- `.planning/phases/08-critical-stability/08-CONTEXT.md` — Phase 8 decisions on lifecycle, async initialization, and orientation handling
- `.planning/codebase/ARCHITECTURE.md` — App architecture and platform-specific patterns

---

## Existing Code Insights

### Reusable Assets
- **`enableEdgeToEdge()` call** (MainActivity line 73): Already in place; no changes needed
- **`isPhone()` detection function** (MainActivity lines 251-263): Refactor to use WindowMetrics primary approach
- **Orientation logic** (MainActivity lines 92-97): Update to respect Android 16+ version checks
- **Lifecycle scope pattern** (MainActivity line 108): Use for fold state and orientation callbacks
- **Napier logging** (throughout): Instrument new device detection and fold state logic

### Established Patterns
- **Configuration-based detection**: Current approach in `isPhone()` — extend with WindowMetrics
- **Async initialization on `lifecycleScope.launch`**: Pattern from Phase 8 — reuse for fold state setup
- **Conditional logic based on `Build.VERSION.SDK_INT`**: Use for Android 16+ orientation changes

### Integration Points
- **MainActivity.onCreate()** (lines 55-87): Primary entry point; update orientation lock removal and add fold state listeners
- **App composable**: May need access to fold state for layout adaptation; use composition locals or state hoisting
- **Layout composables** (TimetablePage, DocumentsPage, GradesPage, etc.): Adapt to respond to fold state if needed

---

## Specific Ideas

None — requirements are clear from ROADMAP.md and REQUIREMENTS.md. Implementation details (API versions, exact WindowMetrics usage, FoldingFeature integration patterns) to be determined during research/planning phase.

---

## Deferred Ideas

None — discussion stayed within Phase 10 scope.

---

*Phase: 10-android-api-compliance-weeks-8-10*  
*Context gathered: 2026-04-10*
