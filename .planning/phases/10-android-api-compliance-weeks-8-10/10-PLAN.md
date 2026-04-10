---
phase: 10-android-api-compliance-weeks-8-10
plan: 1
type: execute
wave: 1
depends_on: []
files_modified:
  - composeApp/build.gradle.kts
  - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt
  - gradle/libs.versions.toml
autonomous: false
requirements: [ANDROID-01, ANDROID-02, ANDROID-03]
user_setup: []

must_haves:
  truths:
    - "App displays correctly on Android 14, 15, and 16+ without deprecated system bar color calls"
    - "Edge-to-edge rendering enabled with proper inset handling across all device types"
    - "Device detection (phone vs tablet) correctly identifies form factor even on foldables"
    - "Fold state detection works on foldable devices; hinge position available to layouts"
    - "Orientation follows Android version: portrait lock on API <36, free rotation on API 36+"
    - "App works correctly in split-screen and multi-window mode"
    - "No content hidden behind system bars on any device configuration"
  artifacts:
    - path: "gradle/libs.versions.toml"
      provides: "androidx.window 1.5.1+ dependency declaration"
      contains: "androidx-window"
    - path: "composeApp/build.gradle.kts"
      provides: "androidx.window added to dependencies"
      exports: ["implementation(libs.androidx.window)"]
    - path: "composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt"
      provides: "Updated isPhone() with WindowMetrics, fold state listener, conditional orientation"
      min_lines: 350
  key_links:
    - from: "MainActivity.onCreate()"
      to: "WindowInfoTracker.windowLayoutInfo()"
      via: "lifecycleScope.launch with repeatOnLifecycle(STARTED)"
      pattern: "WindowInfoTracker\\.getOrCreate.*\\.windowLayoutInfo"
    - from: "MainActivity.isPhone()"
      to: "Display.getWindowMetrics()"
      via: "if Build.VERSION.SDK_INT >= R"
      pattern: "windowManager\\.currentWindowMetrics"
    - from: "onCreate() orientation lock"
      to: "Build.VERSION.SDK_INT check"
      via: "if (Build.VERSION.SDK_INT < 36)"
      pattern: "Build\\.VERSION\\.SDK_INT.*36"
    - from: "enableEdgeToEdge()"
      to: "Compose Scaffold/TopAppBar/BottomAppBar"
      via: "automatic inset handling"
      pattern: "Scaffold.*contentPadding"

---

<objective>
Update the DHBW Dualis app to be fully compliant with Android 15+ APIs, support large screens and foldables, and remove deprecated system window calls. This phase ensures zero Google Play Console deprecation warnings, enables edge-to-edge rendering with proper inset handling, and adds intelligent device detection and fold state management.

**Purpose:** 
- Pass Google Play Console compliance checks for Android 15+ and Android 16 large-screen requirements
- Support modern device form factors (tablets, foldables, split-screen)
- Maintain backward compatibility with Android 14 while leveraging Android 16 APIs when available

**Output:**
- Updated MainActivity with WindowMetrics-based device detection
- Fold state listener for foldable device support
- Conditional orientation management (API-aware)
- Verified edge-to-edge rendering with proper inset handling
- No deprecated setStatusBarColor/setNavigationBarColor calls
- Comprehensive testing across phone/tablet/foldable/split-screen configurations
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/10-android-api-compliance-weeks-8-10/10-CONTEXT.md
@.planning/phases/10-android-api-compliance-weeks-8-10/research/RESEARCH.md
@composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt
@composeApp/build.gradle.kts
@gradle/libs.versions.toml
@.planning/codebase/ARCHITECTURE.md
</context>

<interfaces>
<!-- Key Android APIs and contracts for Phase 10 implementation -->

From androidx.window (1.5.1):
```kotlin
// Fold state detection
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.FoldingFeature

class WindowInfoTracker {
  fun windowLayoutInfo(activity: Activity): Flow<WindowLayoutInfo>
}

data class WindowLayoutInfo(
  val displayFeatures: List<DisplayFeature>  // FoldingFeature, etc.
)

sealed class DisplayFeature
data class FoldingFeature(
  val bounds: Rect,                          // Hinge position in pixels
  val orientation: Orientation,              // HORIZONTAL or VERTICAL
  val state: FoldState                       // FLAT or HALF_OPENED
) : DisplayFeature()

enum class Orientation { HORIZONTAL, VERTICAL }
enum class FoldState { FLAT, HALF_OPENED }
```

From androidx.activity:
```kotlin
import androidx.activity.enableEdgeToEdge

// Called in MainActivity.onCreate() before setContent()
fun ComponentActivity.enableEdgeToEdge(
  statusBarStyle: SystemBarStyle = SystemBarStyle.auto(...),
  navigationBarStyle: SystemBarStyle = SystemBarStyle.auto(...)
): Unit
```

From android.view:
```kotlin
// WindowMetrics for device detection (API 30+)
fun getWindowMetrics(): WindowMetrics
data class WindowMetrics(
  val bounds: Rect  // Current drawable window area
)

// Fallback: Configuration API (API 14+)
val resources.configuration.screenLayout: Int      // SCREENLAYOUT_SIZE_* constants
val resources.configuration.smallestScreenWidthDp: Int  // In DP
```

From android.os:
```kotlin
object Build.VERSION {
  val SDK_INT: Int
  object VERSION_CODES {
    const val R = 30               // API 30 (Android 11)
    const val TIRAMISU = 33        // API 33 (Android 13)
    // Android 16 = API 36 (added in research notes)
  }
}
```

From androidx.lifecycle:
```kotlin
// Already used in MainActivity
val activity.lifecycleScope: CoroutineScope
fun Lifecycle.repeatOnLifecycle(state: Lifecycle.State): suspend () -> Unit
```
</interfaces>

<tasks>

<task type="auto">
  <name>Task 1: Update Dependencies (androidx.window 1.5.1)</name>
  <files>gradle/libs.versions.toml, composeApp/build.gradle.kts</files>
  <action>
    1. **Add androidx.window to gradle/libs.versions.toml:**
       - In [versions] section, add: `androidx-window = "1.5.1"`
       - In [libraries] section, add: `androidx-window = { module = "androidx.window:window", version.ref = "androidx-window" }`

    2. **Verify androidx.activity version in gradle/libs.versions.toml:**
       - Confirm androidx.activity is >= 1.9.0 (currently 1.13.0 per research, sufficient for enableEdgeToEdge)
       - If < 1.9.0, update to 1.9.0+

    3. **Add androidx.window to composeApp/build.gradle.kts:**
       - In androidMain.dependencies block, add: `implementation(libs.androidx.window)`
       - Comment: "Foldable device support via WindowInfoTracker and FoldingFeature"

    4. **Verify build succeeds:**
       - Run `./gradlew compileDebugKotlin` to verify no import/version conflicts
       - Confirm androidx.window 1.5.1 is in dependency tree

    **Decisions locked (from CONTEXT.md):**
    - Use androidx.window 1.5.1 for FoldingFeature detection (D-03)
    - Rely on existing androidx.activity 1.9.0+ for enableEdgeToEdge() (D-02)
  </action>
  <verify>
    <automated>./gradlew compileDebugKotlin 2>&1 | grep -q "BUILD SUCCESSFUL" && echo "Build succeeded" || echo "Build failed"</automated>
  </verify>
  <done>androidx.window 1.5.1 successfully added to gradle/libs.versions.toml and build.gradle.kts; gradle build succeeds without dependency conflicts; enableEdgeToEdge available from androidx.activity</done>
</task>

<task type="auto">
  <name>Task 2: Refactor isPhone() with WindowMetrics + Configuration Fallback</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
    **Update MainActivity.isPhone() function (lines 251-263) to use WindowMetrics primary approach with Configuration fallback per D-01:**

    1. **Replace existing isPhone() implementation with:**
       ```kotlin
       private fun isPhone(): Boolean {
           return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
               // API 30+: Use WindowMetrics for real-time bounds
               val metrics = windowManager.currentWindowMetrics
               val bounds = metrics.bounds
               val widthDp = bounds.width() / resources.displayMetrics.density
               val heightDp = bounds.height() / resources.displayMetrics.density
               
               // Consider phone if smallest dimension < 600dp
               val smallestDimensionDp = minOf(widthDp, heightDp)
               smallestDimensionDp < 600
           } else {
               // Fallback: Configuration API (API 24-29)
               val configuration = resources.configuration
               val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
               val isTabletByScreenSize = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE
               val isTabletByWidth = configuration.smallestScreenWidthDp >= 600
               !isTabletByScreenSize && !isTabletByWidth
           }
       }
       ```

    2. **Test on different device types:**
       - Phone emulator (Pixel 6 or similar, ~411dp width): should return true
       - Tablet emulator (Pixel Tablet or similar, ~600dp+ width): should return false
       - Foldable emulator unfolded (Galaxy Z Fold 7, ~840dp width): should return false (tablet mode)
       - Foldable emulator folded (Galaxy Z Fold 7, ~370dp width): should return true (phone mode)

    3. **Add Napier logging to document detection result:**
       - After detection, log: `Napier.d("isPhone() detected: <true|false> (WindowMetrics: ${bounds.width()}x${bounds.height()} → ${smallestDimensionDp}dp)", tag = "MainActivity")`
       - This helps debug device detection on various devices in production

    4. **Verify no breaking changes to existing callers:**
       - Lines 92-97 in onCreate() still call isPhone() for orientation lock
       - Search codebase for other isPhone() calls; ensure logic is compatible
       - Expected callers: orientation lock (line 92), potentially layout adaption code
  </action>
  <verify>
    <automated>grep -n "fun isPhone()" /Users/johannes/StudioProjects/dhbw/composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && grep -q "Build.VERSION.SDK_INT >= Build.VERSION_CODES.R" /Users/johannes/StudioProjects/dhbw/composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && echo "isPhone() refactored with WindowMetrics check"</automated>
  </verify>
  <done>isPhone() function updated to use Display.getWindowMetrics() (API 30+) with Configuration fallback; detects form factor correctly on phone/tablet/foldable; Napier logging added for debugging</done>
</task>

<task type="auto">
  <name>Task 3: Add Fold State Detection via WindowInfoTracker</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
    **Add fold state listener in MainActivity.onCreate() per D-03 to detect foldable hinge position and state:**

    1. **Add imports at top of MainActivity:**
       ```kotlin
       import androidx.window.layout.WindowInfoTracker
       import androidx.window.layout.FoldingFeature
       import androidx.lifecycle.Lifecycle
       import androidx.lifecycle.repeatOnLifecycle
       ```

    2. **In onCreate() after enableEdgeToEdge() and setContent(), add fold state listener (after line 86, before orientation lock):**
       ```kotlin
       // Add fold state detection for foldable devices
       lifecycleScope.launch {
           lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
               WindowInfoTracker.getOrCreate(this@MainActivity)
                   .windowLayoutInfo(this@MainActivity)
                   .collect { newLayoutInfo ->
                       val foldingFeature = newLayoutInfo.displayFeatures
                           .filterIsInstance<FoldingFeature>()
                           .firstOrNull()
                       
                       if (foldingFeature != null) {
                           Napier.d(
                               "Fold detected: state=${foldingFeature.state}, " +
                               "orientation=${foldingFeature.orientation}, " +
                               "bounds=${foldingFeature.bounds}",
                               tag = "MainActivity"
                           )
                           // TODO: Expose fold state to Compose via CompositionLocal or ViewModel
                           // For now, logging is sufficient for compliance verification
                       } else {
                           Napier.d("No fold detected (phone or non-foldable tablet)", tag = "MainActivity")
                       }
                   }
           }
       }
       ```

    3. **Document placeholder for future fold-aware UI adaptation:**
       - Add comment: "// TODO: Future enhancement — use foldingFeature.bounds to adapt layouts (e.g., two-column on book posture)"
       - Basic detection satisfies D-03; full responsive adaptation deferred to future phase

    4. **Verify no StackOverflowException or ANR:**
       - Confirm lifecycleScope is already available (inherited from ComponentActivity)
       - repeatOnLifecycle(STARTED) auto-cancels when activity pauses → no memory leaks
       - Flow collection stops automatically when activity stops → no dangling listeners

    5. **Test on foldable emulator:**
       - Open foldable emulator (Galaxy Z Fold 7)
       - Check logcat: should see "Fold detected" messages when device folds/unfolds
       - Verify messages appear exactly once per fold state change (no duplicates)
  </action>
  <verify>
    <automated>grep -q "WindowInfoTracker.getOrCreate.*windowLayoutInfo" /Users/johannes/StudioProjects/dhbw/composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && echo "Fold state listener added successfully"</automated>
  </verify>
  <done>Fold state detection listener implemented in MainActivity.onCreate(); detects FoldingFeature with bounds, orientation, and state; lifecycle-aware collection prevents memory leaks; Napier logging captures fold events; TODO placeholder for future layout adaptation</done>
</task>

<task type="auto">
  <name>Task 4: Conditional Orientation Management (Android 16+ API Awareness)</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
    **Replace hard orientation lock with conditional version check (lines 92-97) per D-04 to support Android 16+ compliance:**

    1. **Verify Android 16 API constant:**
       - Android 16 = API 36 (VANILLA_ICE_CREAM in Build.VERSION_CODES)
       - Since API 36 may not be in all SDK versions, define constant locally or use raw value 36

    2. **Replace lines 92-97 with version-aware logic:**
       ```kotlin
       // Conditional orientation lock: portrait on API <36 (Android 15-), free rotation on API 36+ (Android 16+)
       if (Build.VERSION.SDK_INT < 36) {
           // Android 15 and earlier: Lock to portrait for phones only
           if (isPhone()) {
               requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
               Napier.d("Android 15 phone detected - locking to portrait", tag = "MainActivity")
           } else {
               Napier.d("Android 15 tablet detected - allowing all orientations", tag = "MainActivity")
           }
       } else {
           // Android 16+: Allow all orientations (system respects user rotation preference)
           Napier.d("Android 16+ detected - allowing system to manage orientation", tag = "MainActivity")
       }
       ```

    3. **Remove or update @SuppressLint annotation:**
       - Current annotation: `@SuppressLint("SourceLockedOrientationActivity")` on onCreate() (line 71)
       - Since orientation is no longer locked on API 36+, update or remove:
         - Option A: Remove annotation entirely if API 36+ is always free rotation
         - Option B: Change to `@SuppressLint("SourceLockedOrientationActivity", "Condition is always true")` if linting is strict
       - Recommend Option A: Remove @SuppressLint entirely; lint should not complain once lock is conditional

    4. **Verify orientation behavior across Android versions:**
       - On Android 15 emulator with phone form factor: requestedOrientation set to PORTRAIT (logs confirm)
       - On Android 15 emulator with tablet form factor: requestedOrientation NOT set (free rotation)
       - On Android 16 emulator: requestedOrientation NOT set (free rotation, respects system sensor + user preference)
       - Rotation settings respect device.DeviceSpec.defaultOrientation in emulator config

    5. **Test with rotation via adb:**
       - Phone (Android 15): `adb shell settings put system accelerometer_rotation 1` → app stays portrait (override by requestedOrientation)
       - Tablet (Android 15): `adb shell settings put system accelerometer_rotation 1` → app rotates
       - Phone (Android 16): `adb shell settings put system accelerometer_rotation 1` → app rotates (respects user preference)
  </action>
  <verify>
    <automated>grep -q "if (Build.VERSION.SDK_INT < 36)" /Users/johannes/StudioProjects/dhbw/composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && echo "Conditional orientation management implemented"</automated>
  </verify>
  <done>Orientation logic updated with API 36 version check; portrait lock preserved for Android 15 phones, removed for Android 16+ and tablets; @SuppressLint annotation removed or updated; Napier logging documents version-specific behavior; backward compatible with API 24+</done>
</task>

<task type="auto">
  <name>Task 5: Verify Edge-to-Edge Rendering and Inset Handling</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
    **Verify enableEdgeToEdge() is called and Compose insets are handled correctly per D-02:**

    1. **Confirm enableEdgeToEdge() is in correct location:**
       - Line 73 in MainActivity.onCreate() before super.onCreate()? ✓ Verified in code review
       - Already in place; no changes needed to this call

    2. **Audit key layout composables for inset handling:**
       - Open composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/App.kt (or equivalent root Compose tree)
       - Check that root Scaffold (if used) has:
         - `topBar = { ... }` with Material 3 TopAppBar (auto-applies top system bar insets)
         - `bottomBar = { ... }` with Material 3 BottomAppBar (auto-applies bottom insets)
         - `contentPadding` parameter passed to inner content (LazyColumn, etc.)
       - If Scaffold is not used, check for manual inset handling:
         - Avoid hardcoded padding values (16.dp, 24.dp)
         - Use `Modifier.safeDrawingPadding()` or `Modifier.windowInsetsPadding(WindowInsets.systemBars)` for custom layouts
         - Verify these modifiers are applied to top-level containers

    3. **Visual inspection on different device types:**
       - **Phone (Pixel 6, Android 15):** 
         - App title/content not hidden behind status bar? ✓
         - Bottom navigation not hidden behind navigation bar? ✓
       - **Tablet (Pixel Tablet, Android 14):**
         - Landscape mode: content not hidden behind side navigation bars? ✓
       - **Foldable (Galaxy Z Fold 7 emulator, unfolded):**
         - Both panels properly inset? ✓
         - Hinge area not covered by content? ✓
       - **Split-screen (any device):**
         - App displays correctly in upper/lower split? ✓
         - No content overlap with split divider? ✓

    4. **Verify no deprecated calls:**
       - Search MainActivity for `setStatusBarColor()` → Should NOT exist (using enableEdgeToEdge defaults)
       - Search MainActivity for `setNavigationBarColor()` → Should NOT exist
       - Search for `WindowCompat.setDecorFitsSystemWindows()` → Should NOT exist
       - Confirm all system bar coloring relies on `enableEdgeToEdge()` + system theme defaults

    5. **Check Compose Material 3 versions:**
       - Verify Compose Material 3 >= 1.9.0-alpha04 (per research notes)
       - Material 3 automatically respects insets in standard composables (Scaffold, TopAppBar, LazyColumn)
       - No manual padding typically required for Material 3 layouts

    6. **Napier logging to confirm edge-to-edge active:**
       - Add log in MainActivity.onCreate() after enableEdgeToEdge(): 
         `Napier.d("enableEdgeToEdge() called - system bars will draw under app content", tag = "MainActivity")`
       - Verify in logcat on first app launch
  </action>
  <verify>
    <automated>grep -q "enableEdgeToEdge()" /Users/johannes/StudioProjects/dhbw/composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && echo "enableEdgeToEdge() confirmed present"</automated>
  </verify>
  <done>enableEdgeToEdge() verified in MainActivity.onCreate() before setContent(); Compose inset handling validated across phone/tablet/foldable layouts; no deprecated setStatusBarColor/setNavigationBarColor calls present; Material 3 composables automatically apply insets; visual inspection confirms no content hidden behind system bars</done>
</task>

<task type="auto">
  <name>Task 6: Audit and Remove Deprecated System Window Calls</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
    **Search codebase for deprecated API calls and replace with modern edge-to-edge equivalents per D-05:**

    1. **Search in MainActivity.kt for deprecated calls:**
       ```bash
       grep -n "setStatusBarColor\|setNavigationBarColor\|setDecorFitsSystemWindows" \
         /Users/johannes/StudioProjects/dhbw/composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt
       ```
       - Expected result: No matches (already using enableEdgeToEdge)
       - If matches found: Remove immediately and document why they're no longer needed

    2. **Search in entire androidMain directory for deprecated calls:**
       ```bash
       find /Users/johannes/StudioProjects/dhbw/composeApp/src/androidMain -name "*.kt" -type f \
         -exec grep -l "setStatusBarColor\|setNavigationBarColor\|setDecorFitsSystemWindows" {} \;
       ```
       - List any files with matches
       - For each match, determine if it's:
         - A Material Design color setter (replace with enableEdgeToEdge + theme)
         - A deprecated WindowCompat call (remove, rely on enableEdgeToEdge)
         - A comment/documentation (safe to keep)

    3. **If deprecated calls found, replace with modern equivalents:**
       - **Old:** `window.statusBarColor = Color.BLUE`
         **New:** Remove call; let enableEdgeToEdge() + system theme handle colors
       - **Old:** `WindowCompat.setDecorFitsSystemWindows(window, false)`
         **New:** Remove; enableEdgeToEdge() already sets decor fits to false
       - **Old:** Custom inset padding logic
         **New:** Use Material 3 Scaffold + automatic inset handling

    4. **Verify Google Play Console warnings would be eliminated:**
       - No setStatusBarColor() → No deprecation warning
       - No setNavigationBarColor() → No deprecation warning
       - No WindowCompat deprecated methods → No compatibility warnings
       - Build with targetSdkVersion = 35 or higher (assumes current > 33)
       - Confirm via: `grep -i "targetSdk" /Users/johannes/StudioProjects/dhbw/composeApp/build.gradle.kts`

    5. **Document decision in code:**
       - Add comment above enableEdgeToEdge() call in onCreate():
         `// Edge-to-edge rendering: system bars drawn under app content with automatic inset handling.
          // No deprecated setStatusBarColor/setNavigationBarColor calls needed; system theme provides colors.`

    6. **Test on multiple Android versions:**
       - Android 14 (API 34): Verify status bar and navigation bar colors are system defaults (dark/light based on theme)
       - Android 15 (API 35): Same as 14
       - Android 16 (API 36): Same as 14-15
       - If colors don't match app brand requirements, use future Compose APIs (systemBarsDarkContentLighting) rather than deprecated Window calls
  </action>
  <verify>
    <automated>! grep -r "setStatusBarColor\|setNavigationBarColor\|setDecorFitsSystemWindows" /Users/johannes/StudioProjects/dhbw/composeApp/src/androidMain --include="*.kt" && echo "No deprecated system bar calls found"</automated>
  </verify>
  <done>Codebase audited for deprecated setStatusBarColor(), setNavigationBarColor(), and setDecorFitsSystemWindows() calls; no such calls present (already using enableEdgeToEdge); Google Play Console will show zero deprecation warnings for targetSdkVersion; system bar coloring relies entirely on enableEdgeToEdge() and system theme defaults</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <what-built>
    - Dependencies: androidx.window 1.5.1 added to build.gradle.kts
    - Device Detection: isPhone() refactored to use WindowMetrics (API 30+) with Configuration fallback
    - Fold State Detection: WindowInfoTracker listener added to detect foldable hinge and state
    - Orientation Management: Conditional API check (Build.VERSION.SDK_INT < 36) for portrait lock
    - Edge-to-Edge: enableEdgeToEdge() verified at line 73; inset handling working via Material 3
    - Deprecated Calls: Audit confirms no setStatusBarColor/setNavigationBarColor calls present
  </what-built>
  <how-to-verify>
    **Setup:**
    1. Build and install debug APK: `./gradlew installDebugAndroidTest`
    2. Open Android Studio Logcat with filter: "MainActivity"

    **Test 1: Phone Device Detection (Android 15 or earlier)**
    - Open app on Pixel 6 emulator (Android 15)
    - Check logcat for: "Device detected as phone - locking to portrait orientation"
    - Rotate device: app should stay portrait (orientation locked)
    - Expected: Portrait lock active, isPhone() returns true

    **Test 2: Tablet Device Detection**
    - Open app on Pixel Tablet emulator (Android 14)
    - Check logcat for: "Device detected as tablet - allowing all orientations"
    - Rotate device: app should rotate to landscape
    - Expected: No portrait lock, isPhone() returns false

    **Test 3: Fold Detection (Foldable Emulator)**
    - Open app on Galaxy Z Fold 7 emulator (Android 15)
    - Check logcat for: "Fold detected: state=FLAT, orientation=HORIZONTAL"
    - Simulate fold in emulator: Control->Fold/Unfold
    - Check logcat for state change messages
    - Expected: Fold detection logs appear when fold state changes

    **Test 4: Android 16+ Orientation (API 36+)**
    - [If API 36 emulator available] Open app on Android 16 emulator
    - Check logcat for: "Android 16+ detected - allowing system to manage orientation"
    - Rotate device: app should rotate freely (system respects sensor + user setting)
    - Expected: No hard orientation lock, free rotation active

    **Test 5: Edge-to-Edge Visual Check**
    - On phone (Android 15): 
      - App title should NOT be hidden behind status bar
      - Bottom navigation/buttons should NOT be hidden behind navigation bar
    - On tablet (landscape):
      - Content should NOT be hidden behind side system bars
    - On foldable (unfolded):
      - Both panels should display content correctly without overlap

    **Test 6: Split-Screen Mode**
    - Long-press app preview in recent apps
    - Select "Split screen" or use `adb shell am stack move-to-display <activity> <display-id>` for split mode
    - App should display correctly in upper/lower split
    - No content overlap with split divider

    **Acceptance Criteria:**
    - Logcat shows expected device detection messages (phone/tablet/foldable)
    - Orientation lock behavior matches Android version (portrait on 15, free on 16+)
    - Fold detection logs appear on foldable emulators
    - Visual inspection: no content hidden behind system bars
    - Split-screen mode works correctly
    - Build succeeds with no dependency conflicts
  </how-to-verify>
  <resume-signal>Type "approved" to proceed to Task 7 (final testing), or describe any issues found</resume-signal>
</task>

<task type="auto">
  <name>Task 7: Comprehensive Testing & Validation</name>
  <files>composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt</files>
  <action>
    **Execute comprehensive test matrix across Android versions, device types, and configurations to validate Phase 10 compliance:**

    1. **Test Matrix Setup:**
       - Create test plan checklist covering:
         | Android | Device Type | Orientation | Inset Handling | Fold Detection | Notes |
         |---------|-------------|-------------|----------------|---|---|
         | 14 | Phone | Portrait (locked) | ✓ | N/A | Baseline test |
         | 14 | Tablet | Multi (free) | ✓ | N/A | Landscape test |
         | 15 | Phone | Portrait (locked) | ✓ | N/A | isPhone() WindowMetrics |
         | 15 | Tablet | Multi (free) | ✓ | N/A | isPhone() Configuration fallback |
         | 15 | Foldable (folded) | Portrait | ✓ | ✓ | Fold = FLAT, hinge visible in logs |
         | 15 | Foldable (unfolded) | Multi | ✓ | ✓ | Fold detection, two-panel layout possible |
         | 16 | Phone | Multi (free) | ✓ | N/A | Orientation freely respects sensor |
         | 16 | Tablet | Multi (free) | ✓ | N/A | No orientation lock |
         | — | Any | Split-screen | ✓ | Varies | Multi-window compliance |

    2. **Test Device Configurations (via Android Studio Emulator Manager):**
       - **Android 14 Phone:** Pixel 6 (411dp width, portrait)
       - **Android 14 Tablet:** Pixel Tablet (600dp+ width)
       - **Android 15 Phone:** Pixel 6a
       - **Android 15 Tablet:** Pixel Tablet (rotated)
       - **Android 15 Foldable:** Galaxy Z Fold 7 emulator skin
       - **Android 16 Phone:** [If SDK available] Pixel 9 with API 36
       - **Android 16 Tablet:** [If SDK available] Tablet emulator with API 36

    3. **Per-Device Test Procedure:**

       **A. Device Detection Accuracy:**
       - Launch app
       - Check logcat: "isPhone() detected: [true|false]"
       - Verify against expected form factor
       - Measure window bounds: `adb shell wm size` should match reported dimensions

       **B. Orientation Behavior:**
       - Rotate device (via emulator Control menu or physical device)
       - Phone Android 14-15: App should stay portrait (locked)
       - Tablet: App should rotate to landscape
       - Phone Android 16+: App should rotate freely
       - Enable developer option "Rotation" setting, toggle on/off, app should respect system setting

       **C. Fold Detection (Foldables Only):**
       - Check logcat on launch: "Fold detected" or "No fold detected"
       - Simulate fold: emulator Control -> Fold/Unfold
       - Logcat should show new FoldingFeature messages immediately
       - Record fold bounds and orientation (vertical/horizontal)
       - For Z Fold 7: expect horizontal hinge across middle (HORIZONTAL orientation)
       - For Z Flip 7: expect vertical hinge (VERTICAL orientation)

       **D. Edge-to-Edge Visual Check:**
       - App layout should extend under status bar (at top)
       - App layout should extend under navigation bar (at bottom)
       - Text/buttons should NOT be covered by system bars (Material 3 auto-padding handles this)
       - Status bar content (clock, battery) should be readable over app content
       - Test both light and dark themes (if app supports)

       **E. Split-Screen Mode:**
       - For phones: Long-press app in recent, select "Split screen"
       - For tablets: Drag app to left/right edge, select another app for right side
       - App should render correctly at 50% screen height/width
       - No layout overflow or content cutoff
       - Navigation should work in split mode (no ANR)

    4. **Automated Logcat Verification:**
       - Capture logcat during entire test flow
       - Search for:
         - "Device detected as phone" or "Device detected as tablet" → confirms isPhone() called
         - "Fold detected" or "No fold detected" → confirms WindowInfoTracker active
         - "Android 15 phone detected - locking to portrait" or "Android 16+ detected" → confirms version-aware orientation
         - "enableEdgeToEdge() called" → confirms edge-to-edge active
       - Grep count: should have exactly 1 device detection, 1 edge-to-edge, N fold detections (N ≥ 1)

    5. **Performance Validation:**
       - Monitor memory usage via Android Profiler:
         - App startup: < 200MB RSS (baseline)
         - After 5 orientation rotations: no memory growth (< 10MB delta)
         - After 10 fold simulations: no memory growth (< 10MB delta)
       - Monitor CPU:
         - Orientation change: < 500ms re-layout time
         - Fold change: < 300ms re-layout time
         - No ANR timeouts

    6. **Google Play Console Compliance Verification:**
       - Build release APK: `./gradlew bundleRelease`
       - Upload to Play Console internal testing track
       - Check Pre-launch reports (automated testing):
         - Zero deprecation warnings for targetSdkVersion
         - Zero critical/crash errors
       - Review warnings/issues summary:
         - Expected: None related to system bar colors or orientation
       - Manual verification:
         - App works on devices with display cutouts (notched phones)
         - App works on devices with system gesture navigation (API 29+)

    7. **Document Test Results:**
       - Create test summary table with:
         - Device configuration (Android version, form factor, emulator name)
         - Test result (PASS/FAIL) for each of: device detection, orientation, fold detection, inset handling, split-screen
         - Notes (any issues or unexpected behavior)
       - If all tests PASS: Phase 10 Android API Compliance is complete
       - If any test FAIL: Document failure reason, root cause, fix required

    8. **Final Validation Before Phase Completion:**
       - [ ] All test matrix entries marked PASS
       - [ ] Logcat contains expected device detection, orientation, fold messages
       - [ ] No deprecation warnings in Google Play Console preview
       - [ ] Memory usage stable across rotation/fold changes
       - [ ] No ANR timeouts
       - [ ] Split-screen mode works correctly
       - [ ] Edge-to-edge rendering visually correct (no hidden content)
  </action>
  <verify>
    <automated>echo "Manual testing required — see Task 7 how-to-verify section for detailed test matrix and procedures"</automated>
  </verify>
  <done>Comprehensive test matrix executed across Android 14, 15, 16+ emulators with phone/tablet/foldable/split-screen configurations; all tests PASS; device detection accurate; orientation behavior correct per Android version; fold detection working on foldables; edge-to-edge rendering verified with no hidden content; split-screen mode functional; memory stable; zero ANR timeouts; Google Play Console compliance checks passed; test results documented</done>
</task>

</tasks>

<verification>
**Pre-Phase Completion Checklist:**

1. **Task 1 (Dependencies):**
   - [ ] androidx.window 1.5.1 in gradle/libs.versions.toml
   - [ ] androidx.window in composeApp/build.gradle.kts
   - [ ] ./gradlew compileDebugKotlin succeeds
   - [ ] No dependency conflicts with androidx.activity, androidx.lifecycle

2. **Task 2 (Device Detection):**
   - [ ] isPhone() uses WindowMetrics for API 30+ (Build.VERSION.SDK_INT >= R)
   - [ ] isPhone() falls back to Configuration for API 24-29
   - [ ] Phone emulator: isPhone() returns true
   - [ ] Tablet emulator: isPhone() returns false
   - [ ] Foldable folded: isPhone() returns true
   - [ ] Foldable unfolded: isPhone() returns false
   - [ ] Napier logging documents bounds calculation

3. **Task 3 (Fold Detection):**
   - [ ] WindowInfoTracker.windowLayoutInfo() flow added in onCreate()
   - [ ] FoldingFeature detection with bounds, orientation, state
   - [ ] lifecycleScope.repeatOnLifecycle(STARTED) prevents memory leaks
   - [ ] Logcat shows "Fold detected" or "No fold detected" on launch
   - [ ] Fold state changes trigger new collection events
   - [ ] TODO placeholder for future fold-aware layout adaptation

4. **Task 4 (Orientation Management):**
   - [ ] Build.VERSION.SDK_INT < 36 check for portrait lock
   - [ ] Android 15 phones: requestedOrientation = PORTRAIT
   - [ ] Android 15 tablets: no orientation lock
   - [ ] Android 16+: no orientation lock (free rotation)
   - [ ] @SuppressLint annotation removed or updated
   - [ ] Napier logging confirms version-specific behavior

5. **Task 5 (Edge-to-Edge Verification):**
   - [ ] enableEdgeToEdge() called before setContent() (line 73)
   - [ ] Material 3 Scaffold present with proper TopAppBar/BottomAppBar
   - [ ] No content hidden behind status/navigation bars
   - [ ] LazyColumn uses contentPadding for Scaffold padding
   - [ ] Visual inspection on phone/tablet/foldable
   - [ ] Compose Material 3 >= 1.9.0-alpha04

6. **Task 6 (Deprecated Calls Removal):**
   - [ ] No setStatusBarColor() calls in codebase
   - [ ] No setNavigationBarColor() calls in codebase
   - [ ] No setDecorFitsSystemWindows() calls in codebase
   - [ ] All system bar colors rely on enableEdgeToEdge() + system theme
   - [ ] Google Play Console will show zero system bar deprecation warnings

7. **Task 7 (Comprehensive Testing):**
   - [ ] Test matrix completed for Android 14, 15, 16+
   - [ ] All device types tested: phone, tablet, foldable, split-screen
   - [ ] Device detection accuracy verified (logcat)
   - [ ] Orientation behavior correct per Android version
   - [ ] Fold detection working on foldables
   - [ ] Edge-to-edge rendering visually correct
   - [ ] Split-screen mode functional
   - [ ] Memory stable (< 10MB growth across rotations)
   - [ ] No ANR timeouts
   - [ ] Test results documented with PASS/FAIL for each entry

8. **Checkpoint (Human Verification):**
   - [ ] All manual tests from Task 6 checkpoint passed
   - [ ] Logcat contains expected messages
   - [ ] Visual inspection confirms requirements met

**Phase Completion Gates:**
- All 7 tasks DONE
- All checklist items marked completed
- Test matrix 100% PASS
- Zero deprecation warnings in Google Play Console
- Approval from human tester (checkpoint Task 6)
</verification>

<success_criteria>
**Phase 10 Complete When:**

1. **Zero Deprecation Warnings** (ANDROID-01)
   - Google Play Console shows no warnings for setStatusBarColor/setNavigationBarColor
   - No warnings for targetSdkVersion on Android 15+
   - Build log shows zero deprecation errors

2. **Android 15+ Rendering** (ANDROID-01)
   - Edge-to-edge enabled with enableEdgeToEdge()
   - App displays correctly on Android 14, 15 without deprecated system bar calls
   - System bars draw under content; insets handled automatically

3. **Device Detection** (ANDROID-02)
   - isPhone() correctly identifies phone (< 600dp smallest dimension) vs tablet
   - WindowMetrics used on API 30+; Configuration fallback on API 24-29
   - Tested on phone, tablet, foldable form factors
   - Foldable detection: folded → isPhone()=true, unfolded → isPhone()=false

4. **Large Screen Support** (ANDROID-02)
   - App works on tablets in portrait and landscape
   - App works on foldables in both folded and unfolded states
   - Split-screen mode functional (no crashes, responsive UI)
   - No content hidden on any configuration

5. **Foldable Handling** (ANDROID-02)
   - Fold state detection via WindowInfoTracker
   - FoldingFeature.bounds logged (hinge position)
   - FoldingFeature.orientation identified (VERTICAL/HORIZONTAL)
   - FoldingFeature.state captured (FLAT/HALF_OPENED)
   - Test on Galaxy Z Fold 7 emulator, Z Flip 7 emulator, or physical devices if available

6. **Orientation Flexibility** (ANDROID-03)
   - Android 15 phones: portrait lock active (requestedOrientation = PORTRAIT)
   - Android 15 tablets: free rotation (no orientation lock)
   - Android 16+ (API 36+): free rotation for all devices (respects system sensor + user setting)
   - Version check uses Build.VERSION.SDK_INT < 36

7. **Android 16 Compliance** (ANDROID-03)
   - App passes Play Console large-screen compliance check in multi-window mode
   - No setRequestedOrientation() enforced on tablets/foldables
   - Respects Android 16 design policy (orientation freedom on large screens)

**Metrics:**
- Memory: < 10MB growth after 5+ screen transitions
- Startup: < 3 seconds from app launch to interactive UI
- Orientation rotation: < 500ms re-layout time
- Fold state change: < 300ms re-layout time
- ANR: Zero ANR timeouts across all test configurations
- Test coverage: 100% of test matrix entries marked PASS
</success_criteria>

<output>
After Phase 10 completion, create `.planning/phases/10-android-api-compliance-weeks-8-10/10-PLAN-SUMMARY.md` with:

1. **Executive Summary** (3-5 sentences)
   - What was accomplished (edge-to-edge, device detection, fold support, Android 16 compliance)
   - Artifacts created (updated MainActivity, gradle configs)
   - Compliance status (zero deprecation warnings, Android 15+ supported, foldables supported)

2. **Task Completion Matrix**
   - All 7 tasks with status (DONE)
   - Key deliverables per task
   - No blockers or deferred work

3. **Test Results Summary**
   - Test matrix: all entries PASS
   - Device configurations tested
   - Performance metrics (memory, CPU, startup)
   - Google Play Console verification passed

4. **Files Modified**
   - composeApp/build.gradle.kts (androidx.window added)
   - gradle/libs.versions.toml (androidx-window = "1.5.1")
   - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt (isPhone refactor, fold detection, orientation management)

5. **Key Code Changes**
   - WindowMetrics-based device detection (API 30+ with Configuration fallback)
   - WindowInfoTracker fold state listener
   - Conditional orientation lock (API 36 version check)
   - Verified edge-to-edge rendering + inset handling

6. **Requirements Fulfilled**
   - ANDROID-01: Fix Edge-to-Edge Display Compatibility (Android 15+) — COMPLETE
   - ANDROID-02: Support Large Screens & Foldables — COMPLETE
   - ANDROID-03: Remove Display Restrictions for Android 16+ — COMPLETE

7. **Next Phase Gate**
   - All tasks DONE, all tests PASS
   - Ready to proceed to Phase 11: Background Services & Resource Management
</output>

