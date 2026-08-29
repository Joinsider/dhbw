# Phase 10: Android API Compliance - Research

**Researched:** 2026-04-10  
**Domain:** Android API modernization, edge-to-edge rendering, large-screen support, foldable device handling  
**Confidence:** HIGH (official docs + verified implementation patterns)

---

## Summary

Phase 10 modernizes the app's Android integration to meet Android 15+ API requirements and support modern device form factors (tablets, foldables, split-screen). The phase has four core technical components:

1. **Edge-to-Edge Rendering** — `enableEdgeToEdge()` already enabled; requires validation that Compose respects WindowInsets automatically (no manual padding typically needed)
2. **Device Detection** — Upgrade `isPhone()` from Configuration-only to use `Display.getWindowMetrics()` primary, Configuration fallback for compatibility
3. **Foldable Support** — Implement basic fold state detection via `androidx.window.FoldingFeature` with `WindowInfoTracker` flow
4. **Orientation Flexibility** — Remove hard portrait lock for Android 16+ (API 36+) devices while preserving it for Android 15 and earlier on phones

All decisions from CONTEXT.md are locked and verified through this research. Implementation details and code patterns are documented below.

**Primary recommendation:** Implement in this order: (1) verify edge-to-edge inset handling is sufficient without manual padding, (2) upgrade `isPhone()` with WindowMetrics, (3) add fold state listener in MainActivity, (4) conditionally remove orientation lock based on API level.

---

## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01: Hybrid WindowMetrics + Configuration Fallback**
- Primary: Use `Display.getWindowMetrics()` to measure actual window bounds in real-time
- Fallback: Use `Configuration.screenLayout` + `smallestScreenWidthDp` for older Android versions or edge cases where WindowMetrics is unavailable
- Rationale: WindowMetrics provides precise bounds detection for foldables and large screens; Configuration fallback ensures compatibility with older APIs
- Implementation: Update `MainActivity.isPhone()` to try WindowMetrics first, fall back to current Configuration logic if needed
- Code location: `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` lines 251-263

**D-02: Default enableEdgeToEdge() + Automatic Inset Respect**
- Rely on `enableEdgeToEdge()` call (already in place at MainActivity line 73) combined with Compose's automatic WindowInsets handling
- Do NOT manually apply `WindowInsets.systemBars.asPaddingValues()` unless specific visual requirements demand it
- Rationale: Material Design default is battle-tested; Compose respects insets automatically in most layouts; avoids over-engineering
- Result: System bars draw under content; Compose adjusts content padding automatically
- Validation: Verify no content is hidden behind system bars across phone, tablet, and foldable layouts

**D-03: Basic Hinge Detection via FoldingFeature API**
- Implement detection of fold state (folded/unfolded) using `androidx.window.FoldingFeature`
- Detect hinge position and orientation (vertical/horizontal)
- Allow layouts to adapt when fold state changes (e.g., switch between single-column and two-column layouts)
- Do NOT implement full hinge-aware responsive layouts (respecting hinge position in draw bounds) — that's future work
- Rationale: Satisfies compliance requirement without over-engineering; basic detection gives decent UX on actual foldables
- Implementation: Add fold state detection in MainActivity or shared composition state; expose fold state to composables that need it
- Testing: Test on Galaxy Z Fold/Z Flip emulator (available in Android Studio) or physical devices if available

**D-04: Sensor-Driven Rotation (User Preference)**
- Remove hard portrait orientation lock from `MainActivity.onCreate()` for Android 16+
- Allow system to manage orientation based on user's rotation preference (Settings > Display > Rotation preference)
- For Android 15 and earlier: Maintain current portrait lock for phones detected as phones; tablets allow all orientations
- For Android 16+: Allow rotation for all devices (no orientation lock)
- Rationale: Respects Android 16+ compliance requirements; follows user's system settings; doesn't override user preferences
- Implementation: Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` (or appropriate version for Android 16) and conditionally set/unset `requestedOrientation`
- Note: Remove `@SuppressLint("SourceLockedOrientationActivity")` annotation once orientation lock is removed

### Claude's Discretion

**D-05: No Explicit Color Calls for Status/Navigation Bars**
- Do NOT call deprecated `setStatusBarColor()` or `setNavigationBarColor()`
- Rely on `enableEdgeToEdge()` and system defaults for bar colors
- If theming is needed in the future: Use Compose's `systemBarsDarkContentLighting` or dynamic color APIs, not direct Window calls
- Rationale: Avoids deprecation; system defaults work well with edge-to-edge; future-proof for Android 17+

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ANDROID-01 | Fix Edge-to-Edge Display Compatibility (Android 15+) | Material 3 Scaffold and Compose WindowInsets automatically handle insets when `enableEdgeToEdge()` is called; no additional manual padding typically required for standard layouts |
| ANDROID-02 | Support Large Screens & Foldables | `Display.getWindowMetrics()` provides runtime window bounds; `FoldingFeature` API detects fold state, orientation, and hinge position; tested via emulator skins and multi-window mode |
| ANDROID-03 | Remove Display Restrictions for Android 16+ | Android 16 (API 36) ignores `setRequestedOrientation()` for large screens; conditional removal based on `Build.VERSION.SDK_INT >= 36` allows targeted compliance |

---

## Standard Stack

### Core Dependencies

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.activity | 1.13.0 (current in project) | Contains `enableEdgeToEdge()` API | Official Material 3 edge-to-edge support; standard in all modern Android projects |
| androidx.window | Not yet in project | FoldingFeature, WindowInfoTracker, WindowLayoutInfo | Standard Jetpack library for foldable device support; officially recommended by Google |
| androidx.lifecycle | 2.10.0 (current) | Lifecycle-aware coroutines via `lifecycleScope` | Battle-tested pattern for managing async tasks tied to Activity/Fragment lifecycle |
| Compose Material 3 | 1.9.0-alpha04 (current, staged for stable) | Scaffold, WindowInsets handling | Material 3 automatically respects insets; Compose Scaffold is standard for adaptive layouts |

### Installation

Add to `gradle/libs.versions.toml`:

```toml
[versions]
androidx-window = "1.5.1"  # Latest stable; supports Android API 14+

[libraries]
androidx-window = { module = "androidx.window:window", version.ref = "androidx-window" }
androidx-window-java = { module = "androidx.window:window-java", version.ref = "androidx-window" }  # Optional: Java API adapters
```

Add to `composeApp/build.gradle.kts` in `androidMain.dependencies`:

```kotlin
androidMain.dependencies {
    implementation(libs.androidx.window)
    // If using Java callback adapters instead of Kotlin Flows:
    // implementation(libs.androidx.window.java)
}
```

**Version verification (as of 2026-04-10):**
- androidx.activity: 1.13.0 is current in project; 1.9.0+ confirmed available and compatible
- androidx.window: 1.5.1 is current stable; provides FoldingFeature, WindowInfoTracker, full foldable support
- androidx.lifecycle: 2.10.0 is current in project; compatible with window manager integration

### Why Not Alternatives

- **Manual inset handling vs. Compose automatic:** Compose and Material 3 handle insets automatically when using standard composables (Scaffold, TopAppBar, BottomAppBar, LazyColumn with padding modifiers). Manual padding is only needed for custom layouts or non-standard positioning.
- **Direct Display API vs. Jetpack Window library:** While `Display.getWindowMetrics()` is available in API 30+, the Jetpack WindowManager library provides compatibility back to API 14 and is the official recommendation for foldable support.
- **setRequestedOrientation vs. sensor-based system control:** On Android 16+, `setRequestedOrientation()` is ignored for large screens anyway; relying on system sensor input respects user preferences and aligns with platform design.

---

## Architecture Patterns

### Recommended Integration Points

| Component | Integration | Pattern |
|-----------|-----------|---------|
| **MainActivity.isPhone()** | Device detection | Check WindowMetrics.bounds first (API 30+), fall back to Configuration |
| **MainActivity.onCreate()** | Orientation lock | Conditional based on `Build.VERSION.SDK_INT` and device form factor |
| **MainActivity.lifecycleScope** | Fold state listener | Launch collection of `WindowInfoTracker.windowLayoutInfo()` Flow |
| **Compose layout composables** | Fold-aware UI | Observe fold state from composition local or state hoisting |
| **Scaffold / TopAppBar / BottomAppBar** | Inset handling | Use Material 3 defaults; avoid manual WindowInsets unless custom positioning required |

### Pattern 1: Hybrid Device Detection with WindowMetrics

**What:** Query actual window bounds at runtime using Display.getWindowMetrics() (API 30+) with Configuration API fallback for older devices.

**When to use:** Determining whether device is phone or tablet, especially important for foldables where physical screen size doesn't indicate form factor.

**Example:**

```kotlin
// Source: Android Developers - WindowMetrics reference
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
        // Fallback: Configuration API (all API levels)
        val configuration = resources.configuration
        val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val isTabletByScreenSize = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE
        val isTabletByWidth = configuration.smallestScreenWidthDp >= 600
        !isTabletByScreenSize && !isTabletByWidth
    }
}
```

**Why this pattern:**
- WindowMetrics reflects actual drawable window area (accounts for status bar, navigation bar, fold position)
- Configuration API is reliable fallback for devices older than API 30
- Handles foldables correctly: when Z Fold is unfolded as tablet, WindowMetrics sees full width; when folded, sees narrower width

### Pattern 2: Fold State Detection with WindowInfoTracker (Kotlin Flows)

**What:** Monitor foldable hinge position and fold state using Jetpack WindowManager with lifecycle-aware coroutines.

**When to use:** Adapting UI layout when device folds/unfolds (e.g., switching single-column to two-column on book posture).

**Example:**

```kotlin
// Source: Android Developers - Make Your App Fold Aware guide
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // ... existing setup ...

    // Add fold state listener (in lifecycleScope for lifecycle awareness)
    lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            WindowInfoTracker.getOrCreate(this@MainActivity)
                .windowLayoutInfo(this@MainActivity)
                .collect { newLayoutInfo ->
                    val foldingFeature = newLayoutInfo.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .firstOrNull()
                    
                    // Update app state based on fold feature
                    if (foldingFeature != null) {
                        Napier.d(
                            "Fold detected: state=${foldingFeature.state}, " +
                            "orientation=${foldingFeature.orientation}, " +
                            "bounds=${foldingFeature.bounds}",
                            tag = "MainActivity"
                        )
                        // Expose to Compose via CompositionLocal or state
                    }
                }
        }
    }
}
```

**Why this pattern:**
- `repeatOnLifecycle(STARTED)` automatically stops collection when Activity is paused, preventing memory leaks
- Flow-based approach is reactive and testable
- `displayFeatures` list contains all device features (not just folds); easily extended for future hardware
- Hinge position available via `FoldingFeature.bounds` for pixel-perfect layout adaptation

### Pattern 3: Conditional Orientation Lock Removal (Android 16+)

**What:** Remove hard portrait orientation lock for Android 16+ devices while preserving it for phones on Android 15 and earlier.

**When to use:** Complying with Android 16 large-screen policy that ignores orientation locks on tablets and foldables.

**Example:**

```kotlin
// Source: Android 16 Orientation Changes blog post + Jetpack Compose lifecycle patterns
@SuppressLint("SourceLockedOrientationActivity")  // Remove after implementing conditional lock
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // ... existing setup ...

    // Conditionally lock orientation based on API level and device form factor
    if (Build.VERSION.SDK_INT < 36) { // API 36 = Android 16
        // Android 15 and earlier: Lock to portrait for phones only
        if (isPhone()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Napier.d("Android 15 phone detected - locking to portrait", tag = "MainActivity")
        } else {
            Napier.d("Android 15 tablet detected - allowing all orientations", tag = "MainActivity")
        }
    } else {
        // Android 16+: Allow all orientations (system respects user preference)
        Napier.d("Android 16+ detected - allowing system to manage orientation", tag = "MainActivity")
    }
}
```

**Why this pattern:**
- Android 16 (API 36+) ignores `requestedOrientation` for large screens anyway; better to respect that explicitly
- Maintains backward compatibility: phones on Android 15 still locked to portrait (expected behavior)
- Clean separation of version-specific logic using `Build.VERSION.SDK_INT`
- Napier logging documents why each decision was made (valuable for debugging device-specific issues)

### Pattern 4: EdgeToEdge + Compose WindowInsets (Default Behavior)

**What:** Enable edge-to-edge rendering with automatic Compose inset handling; avoid manual padding unless custom positioning required.

**When to use:** Standard app layouts where system bars should be drawn under app content with automatic padding via Compose.

**Example:**

```kotlin
// Source: Android Developers - Material 3 Insets guide
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()  // Already called in project at line 73
    super.onCreate(savedInstanceState)

    setContent {
        App()  // Compose content
    }
}

// In App composable or shared container:
@Composable
fun App() {
    Scaffold(
        topBar = { 
            // Material 3 TopAppBar automatically applies top system bar insets
            TopAppBar(title = { Text("Title") })
        },
        bottomBar = {
            // Material 3 BottomAppBar automatically applies bottom system bar insets
            BottomAppBar { /* content */ }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .consumeWindowInsets(innerPadding)  // Consume Scaffold padding
                .fillMaxSize(),
            contentPadding = innerPadding  // Apply as content padding
        ) {
            // Content automatically padded away from system bars
            items(100) { Text("Item $it") }
        }
    }
}

// If custom inset handling needed:
@Composable
fun CustomLayout() {
    Box(
        modifier = Modifier.safeDrawingPadding()  // Utility: applies safe drawing insets as padding
    ) {
        Text("Content safe from system UI")
    }
}
```

**Why this pattern:**
- Material 3 Scaffold and composables handle insets automatically; no manual work in 99% of cases
- `consumeWindowInsets()` prevents double-padding when both Scaffold and LazyColumn apply insets
- `safeDrawingPadding()` is a utility that automatically adjusts for status bars, navigation bars, notches, cutouts
- Compose insets are animated automatically (including IME animations backported to API 21)

### Anti-Patterns to Avoid

- **Hardcoded padding values:** Never use fixed padding like `.padding(16.dp)` for all content; system bars size changes with device configuration
- **Ignoring insets on custom composables:** If building custom layout with manual measurement, must apply WindowInsets.systemBars or safeDrawing padding
- **Calling setStatusBarColor/setNavigationBarColor:** Deprecated in API 35+; let enableEdgeToEdge() and system theming handle colors
- **Using Display.getMetrics() instead of getWindowMetrics():** getMetrics() only reflects physical display, not drawable window area (ignores status bar, keyboard, fold position)
- **Not testing split-screen mode:** App may layout correctly in fullscreen but break when resized to half-screen; critical validation step for tablet/large-screen support

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| **Display metrics/window bounds** | Custom view measurement code | `Display.getWindowMetrics()` (API 30+) or `Configuration.smallestScreenWidthDp` | WindowMetrics reflects real drawable area accounting for system UI; Configuration requires parsing; both have edge cases (keyboard, fold position, split-screen) that custom code will miss |
| **Inset handling in Compose** | Manual padding calculation based on system bar heights | Material 3 Scaffold + Compose `windowInsetsPadding()`, `safeDrawingPadding()` modifiers | Compose automatically animates insets with IME, handles configuration changes, and Scaffold multiplexes insets for all child composables; manual padding breaks on configuration change or custom IME |
| **Foldable device detection** | Regular expression on build.MODEL or screen dimension heuristics | `androidx.window.FoldingFeature` + `WindowInfoTracker` | FoldingFeature provides authoritative hinge position, fold state, and orientation; device name parsing is fragile and misses future foldable designs; WindowInfoTracker is reactive and testable |
| **Orientation management** | Custom orientation preference storage + manual Activity rotation handling | `Build.VERSION.SDK_INT` checks + `requestedOrientation` | Android 16 respects system user preference anyway; custom code adds complexity and can conflict with system policy; version check is single source of truth |

**Key insight:** WindowMetrics, Compose insets, and FoldingFeature are all relatively new (API 30+, Compose 1.1+, Window 1.0+) but are now the *only* sanctioned approaches by Google. Hand-rolling measurement, inset calculation, or fold detection either duplicates functionality or misses edge cases (especially split-screen, fold position, dynamic reconfiguration).

---

## Runtime State Inventory

**Note:** This phase is API-level modernization with no data model changes, no stored state refactors, no service rebranding. Runtime state inventory is not applicable.

The following categories are explicitly verified as out-of-scope:

- **Stored data:** No changes to database schema, user ID storage, or data keys
- **Live service config:** No changes to Dualis API endpoints, authentication strings, or backend configuration
- **OS-registered state:** No Task Scheduler tasks, pm2 processes, systemd units, or launchd plists created/modified
- **Secrets/env vars:** No environment variable names changed or new secrets introduced
- **Build artifacts:** No new compiled binaries or installed packages that would require cleanup

---

## Common Pitfalls

### Pitfall 1: Assuming enableEdgeToEdge() Handles Everything

**What goes wrong:** Developer calls `enableEdgeToEdge()` and assumes content automatically pads away from system bars, then ships app with content hidden behind status/navigation bars on Android 15+.

**Why it happens:** `enableEdgeToEdge()` makes the system bars *translucent and drawable under*, but does NOT automatically apply padding. Compose does respect insets *when you use Material 3 composables*, but custom layouts don't automatically get padding.

**How to avoid:**
1. Verify layout uses Material 3 `Scaffold`, `TopAppBar`, `BottomAppBar` (which auto-apply insets)
2. For custom layouts, explicitly apply `Modifier.safeDrawingPadding()` or `Modifier.windowInsetsPadding(WindowInsets.systemBars)`
3. Test on Android 15 emulator with status/nav bars visible; content must not be hidden

**Warning signs:**
- Text or buttons cut off at top/bottom of screen on Android 15+ devices
- Content layout shifts when keyboard appears (indicates insets not being consumed)
- UI looks correct on emulator but broken on physical device with gesture navigation

### Pitfall 2: WindowMetrics Bounds Misinterpreted as Screen Size

**What goes wrong:** Developer calls `Display.getWindowMetrics().bounds` and assumes it returns physical screen dimensions, then uses fixed aspect ratio assumptions for device detection.

**Why it happens:** `getWindowMetrics()` returns the *drawable window area* (what your app can actually use), which excludes status bar, navigation bar, and on foldables, the fold position. If device is in multi-window/split-screen mode, bounds reflect only the app's allocated area, not the full screen.

**How to avoid:**
1. Use `getWindowMetrics().bounds.width()` and `.height()` only for runtime layout adaptation, not for device classification
2. For device type classification, compare smallest dimension against 600dp threshold (standard tablet breakpoint)
3. Test on Galaxy Z Fold with device both folded and unfolded; bounds must change
4. Test in split-screen mode; bounds must reflect app window, not full screen

**Warning signs:**
- Device detected as tablet when physically in phone mode (folded Z Fold)
- App layout unchanged when entering split-screen despite bounds shrinking
- Hard-coded aspect ratios or fixed pixel assumptions in device detection logic

### Pitfall 3: Forgetting Fold State Can Change at Runtime

**What goes wrong:** Developer checks fold state once in `onCreate()`, caches it, then layout breaks when user folds/unfolds device.

**Why it happens:** `WindowLayoutInfo.displayFeatures` is a Flow that emits updates whenever fold state changes. If you collect it once and don't re-observe, you miss updates. Also easy to collect without lifecycle awareness and leak the coroutine.

**How to avoid:**
1. Always collect `WindowInfoTracker.windowLayoutInfo()` in a `repeatOnLifecycle` block tied to Activity lifecycle
2. Don't cache fold state as a field; keep it in a mutable composition state or Flow that composables can observe
3. Test by dragging hinge slider in emulator while app is open; layout must adapt in real-time

**Warning signs:**
- Layout doesn't update when folding/unfolding device
- Memory leak detectable in profiler after app pause/resume cycles
- Coroutine warnings in logcat about uncancelled collection

### Pitfall 4: Hard Orientation Lock on Android 16+ Large Screens

**What goes wrong:** App hard-locks to portrait via `requestedOrientation = SCREEN_ORIENTATION_PORTRAIT` on all Android versions. App fails Google Play Console checks on Android 16 because policy ignores the lock and app is flagged as non-compliant with large-screen adaptive behavior.

**Why it happens:** Before Android 16, apps could force orientation. Android 16 policy (enforced for apps targeting API 36+) ignores orientation locks on large screens (≥ 600dp) to prevent poor UX. Developers who don't version-check their orientation code don't realize the lock is being ignored and don't adapt.

**How to avoid:**
1. Add version check: `if (Build.VERSION.SDK_INT < 36)` before setting `requestedOrientation`
2. For Android 16+, only apply orientation lock if device is a small phone (≤ 600dp width)
3. Test on Android 16 emulator in tablet mode; orientation lock should NOT apply
4. Remove `@SuppressLint("SourceLockedOrientationActivity")` once version check is in place

**Warning signs:**
- Google Play Console flags app as non-compliant with large-screen support on Android 16+
- User can't rotate app on large-screen device even when system rotation setting is enabled
- Lint warning about orientation lock remains in code

### Pitfall 5: Configuration Changes Breaking Fold State

**What goes wrong:** App collects fold state flow but doesn't handle configuration changes (orientation, window size). Collect lambda captures old reference to Activity context or state, and when Activity is recreated during orientation change, fold state updates go to old Activity instance.

**Why it happens:** Configuration changes (orientation, window size, locale) trigger Activity recreation by default. If fold state collector doesn't use `repeatOnLifecycle`, it's tied to a specific Activity instance and breaks on recreation.

**How to avoid:**
1. Always use `repeatOnLifecycle(Lifecycle.State.STARTED)` when collecting from `WindowInfoTracker`
2. Don't capture Activity or ViewModel references in the collect lambda; use `this@MainActivity` for Activity methods
3. Expose fold state to Compose via CompositionLocal or ViewModel Flow, not via Activity field
4. Test by rotating device while app has fold state listener active; layout must adapt without crashing

**Warning signs:**
- App crashes when rotating device after fold state listener is started
- Fold state updates stop working after device orientation change
- Duplicate coroutines spawned on each Activity recreation

---

## Code Examples

Verified patterns from official Android Developer documentation:

### Example 1: Device Detection with WindowMetrics Fallback

```kotlin
// Source: Android Developers WindowMetrics API reference
private fun isPhone(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30
        try {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val widthDp = bounds.width() / resources.displayMetrics.density
            val heightDp = bounds.height() / resources.displayMetrics.density
            
            // Phone if smallest dimension is less than 600dp
            val smallestDp = minOf(widthDp, heightDp)
            smallestDp < 600
        } catch (e: Exception) {
            // Fallback to Configuration if getWindowMetrics fails
            Napier.w("WindowMetrics failed, falling back to Configuration", tag = "MainActivity")
            isPhoneViaConfiguration()
        }
    } else {
        // API 24-29: Use Configuration only
        isPhoneViaConfiguration()
    }
}

private fun isPhoneViaConfiguration(): Boolean {
    val configuration = resources.configuration
    val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
    val isTabletByScreenSize = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE
    val isTabletByWidth = configuration.smallestScreenWidthDp >= 600
    return !isTabletByScreenSize && !isTabletByWidth
}
```

### Example 2: Fold State Listener with Lifecycle Awareness

```kotlin
// Source: Android Developers - Make Your App Fold Aware guide
lifecycleScope.launch {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        WindowInfoTracker.getOrCreate(this@MainActivity)
            .windowLayoutInfo(this@MainActivity)
            .collect { layoutInfo ->
                val foldFeature = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                
                if (foldFeature != null) {
                    val state = when (foldFeature.state) {
                        FoldingFeature.State.FLAT -> "FLAT"
                        FoldingFeature.State.HALF_OPENED -> "HALF_OPENED"
                    }
                    val orientation = when (foldFeature.orientation) {
                        FoldingFeature.Orientation.HORIZONTAL -> "HORIZONTAL (book posture)"
                        FoldingFeature.Orientation.VERTICAL -> "VERTICAL (landscape posture)"
                    }
                    Napier.d("Fold: state=$state, orientation=$orientation, bounds=${foldFeature.bounds}", 
                        tag = "MainActivity")
                    
                    // Update composition local or state for layout adaptation
                    // val isTwoColumnLayout = foldFeature.state == FoldingFeature.State.HALF_OPENED &&
                    //     foldFeature.orientation == FoldingFeature.Orientation.VERTICAL
                } else {
                    Napier.d("No fold detected on this device", tag = "MainActivity")
                }
            }
    }
}
```

### Example 3: Conditional Orientation Lock (Android 15 vs 16+)

```kotlin
// Source: Android 16 Orientation Changes blog post
@SuppressLint("SourceLockedOrientationActivity") // Remove after version check implemented
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // ... existing service initialization ...

    // Conditional orientation locking based on API level
    if (Build.VERSION.SDK_INT < 36) { // Android 16 is API 36
        // Android 15 and earlier: Lock phones to portrait for better UX
        if (isPhone()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Napier.d("Android 15 portrait lock enabled for phone", tag = "MainActivity")
        } else {
            Napier.d("Android 15 tablet detected - orientation unlocked", tag = "MainActivity")
        }
    } else {
        // Android 16+: Respect system/user preference (large-screen policy compliance)
        // Note: requestedOrientation is ignored for large screens anyway
        Napier.d("Android 16+ detected - orientation managed by system", tag = "MainActivity")
    }
}
```

### Example 4: Material 3 Scaffold with Automatic Inset Handling

```kotlin
// Source: Android Developers - Material 3 Insets guide
@Composable
fun TimetablePage() {
    Scaffold(
        topBar = {
            // TopAppBar automatically applies top system bar insets
            TopAppBar(
                title = { Text("Timetable") },
                windowInsets = WindowInsets.systemBars // Explicit (Material 3 default)
            )
        },
        bottomBar = {
            // BottomAppBar automatically applies bottom system bar insets
            BottomAppBar {
                NavigationBar {
                    NavigationBarItem(
                        selected = true,
                        onClick = { },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) }
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding), // Consume Scaffold padding
            contentPadding = innerPadding // Apply Scaffold padding to content
        ) {
            items(lectureList.size) { index ->
                LectureItem(lectureList[index])
            }
        }
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Display.getMetrics()` for screen size | `Display.getWindowMetrics()` for drawable bounds | Android 11 (API 30) introduced; Android 12+ official recommendation | Accounts for system UI, fold position, multi-window; essential for modern devices |
| Manual padding calculation around system bars | `safeDrawingPadding()` modifier or Material 3 composables with auto-inset | Compose 1.1+, Material 3 stable | Automatic animation with IME, handles configuration changes, testable |
| `setStatusBarColor()` / `setNavigationBarColor()` | `enableEdgeToEdge()` + system theming | Deprecated in API 35; policy enforced in Android 16 | Simpler, respects system design language, future-proof |
| Manual orientation management everywhere | Version-conditional orientation locking | Android 16 (API 36) policy change | Large-screen devices ignore locks anyway; respecting that prevents UX issues |
| Custom foldable device detection | `androidx.window.FoldingFeature` API | Jetpack Window 1.0+ (2021), standard in Window 1.5.1 | Authoritative hinge position and state; reactive, testable |

**Deprecated/outdated:**
- **Display.getMetrics()**: Replaced by getWindowMetrics(). getMetrics() only reflects physical display, ignoring system UI and drawable area changes
- **Activity.setRequestedOrientation() for all devices**: Honored on phones; ignored on large screens in Android 16+. Use version checks instead of blanket orientation locks
- **Custom device size classification:** Replaced by Material 3 window size classes and `smallestScreenWidthDp` breakpoints. No guesswork needed

---

## Open Questions

1. **Inset handling for custom bottom sheet in documents view**
   - What we know: Material 3 ModalBottomSheet automatically applies bottom insets; app uses custom composables in some views
   - What's unclear: Whether custom bottom-sheet-like overlays in DocumentsPage need explicit inset padding
   - Recommendation: During implementation, test DocumentsPage in split-screen; if content is hidden behind nav bar, add `Modifier.safeDrawingPadding()` to custom sheet

2. **Fold state UI adaptation scope**
   - What we know: Decision D-03 limits scope to basic fold detection, not full hinge-aware layout
   - What's unclear: Which specific composables should respond to fold state changes (TimetablePage, GradesPage, others?)
   - Recommendation: During planning/implementation, determine if any page benefits from two-column layout on book posture; defer complex hinge-aware layouts to future phase

3. **Testing multi-window mode on emulator**
   - What we know: Resizable emulator supports multi-window; split-screen can be triggered via system UI
   - What's unclear: Exact ADB commands to automate split-screen testing in CI/CD pipeline
   - Recommendation: Manual testing during development; document exact steps in PLAN.md for verification phase; defer automation to future phase

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK 36 (Android 16) | Android 16 compliance testing | ✓ | API 36 | Test on API 35 emulator (Android 15) with version checks; Android 16 required for final validation |
| Android Studio emulator | Foldable device simulation | ✓ | Latest (2024.2+) | Physical Samsung Galaxy Z device; emulator with Galaxy Z skins essential for development |
| Galaxy Z Fold emulator skin | Z Fold posture testing (hinge angle, dual-screen) | ✓ | Z Fold 7 (2026) | Custom foldable AVD or physical device; standard skins available from Samsung Developer Portal |
| Galaxy Z Flip emulator skin | Z Flip posture testing (hinge angle) | ✓ | Z Flip 7 (2026) | Custom foldable AVD or physical device; standard skins available from Samsung Developer Portal |
| androidx.window library | FoldingFeature, WindowInfoTracker | ✗ (Not in project) | 1.5.1 (current stable) | — (Required; must add to dependencies) |

**Missing dependencies with no fallback:**
- androidx.window 1.5.1 — MUST add to gradle dependencies before implementation

**Missing dependencies with fallback:**
- Android 16 emulator — Can test on Android 15 emulator with `Build.VERSION.SDK_INT` checks; Android 16 needed for final compliance check

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4 (via Robolectric for unit tests) + Compose UI tests |
| Config file | `composeApp/build.gradle.kts` (androidUnitTest.dependencies section) |
| Quick run command | `./gradlew testAndroidDebugUnitTest --tests "*MainActivityTest*"` |
| Full suite command | `./gradlew testAndroidDebugUnitTest testAndroidDebugInstrumentedTest` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ANDROID-01 | `enableEdgeToEdge()` called before setContent | Unit | Reflection test on MainActivity.onCreate | ❌ Wave 0 |
| ANDROID-01 | No deprecated setStatusBarColor/setNavigationBarColor calls | Static | Grep for method calls in codebase | ✅ Can verify via grep |
| ANDROID-02 | `isPhone()` returns correct value for phone/tablet bounds | Unit | Test with mocked WindowMetrics | ❌ Wave 0 |
| ANDROID-02 | FoldingFeature listener starts on STARTED state | Unit | Test lifecycle scope launch conditions | ❌ Wave 0 |
| ANDROID-02 | No content hidden behind system bars on Android 15+ | Integration | Emulator screenshot comparison or manual verification | ❌ Manual only |
| ANDROID-03 | Orientation lock removed for Android 16+ (API 36+) | Unit | Test Build.VERSION.SDK_INT >= 36 branch | ❌ Wave 0 |
| ANDROID-03 | Multi-window mode works without UI corruption | Integration | Manual test in split-screen on emulator | ❌ Manual only |

### Sampling Rate

- **Per task commit:** `./gradlew testAndroidDebugUnitTest --tests "*MainActivityTest*"` (runs device detection and orientation tests)
- **Per wave merge:** Full suite: `./gradlew testAndroidDebugUnitTest testAndroidDebugInstrumentedTest` (all Android-specific tests)
- **Phase gate:** Full test suite green + manual emulator verification (screenshots on phone, tablet, Z Fold, Z Flip, split-screen)

### Wave 0 Gaps

- [ ] `tests/androidTest/kotlin/de/fampopprol/dhbwhorb/MainActivityTest.kt` — Unit tests for `isPhone()` with WindowMetrics mocking, orientation lock conditional logic
- [ ] `tests/androidTest/kotlin/de/fampopprol/dhbwhorb/FoldingFeatureTest.kt` — Unit tests for fold state listener lifecycle awareness, Flow collection
- [ ] `tests/androidTest/kotlin/de/fampopprol/dhbwhorb/WindowInsetsTest.kt` — Compose test to verify no content hidden behind system bars on Android 15+ emulator
- [ ] Manual emulator test plan: Phone (portrait/landscape), Tablet, Z Fold (folded/unfolded), Z Flip, split-screen mode with screenshots

*(If all tests in Wave 1 implementation: replace this section with "Wave 0 gaps: None — existing test infrastructure covers phase requirements"; currently no Android-specific tests exist in project)*

---

## Sources

### Primary (HIGH confidence)

- [Android Developers - Window Insets in Jetpack Compose](https://developer.android.com/develop/ui/compose/layouts/insets) - WindowInsets types, Material 3 automatic handling, edge-to-edge behavior on Android 15+
- [Android Developers - Set up Window Insets](https://developer.android.com/develop/ui/compose/system/insets-ui) - `safeDrawingPadding()`, `windowInsetsPadding()`, inset consumption, IME animation
- [Android Developers - Material 3 Insets](https://developer.android.com/develop/ui/compose/system/material-insets) - Scaffold, TopAppBar, BottomAppBar automatic inset handling, override patterns
- [Android Developers - FoldingFeature API Reference](https://developer.android.com/reference/androidx/window/layout/FoldingFeature) - state, orientation, occlusionType, isSeparating properties
- [Android Developers - Make Your App Fold Aware](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware) - WindowInfoTracker, windowLayoutInfo() Flow, lifecycle-aware collection, foldable postures
- [Android Developers - Build.VERSION_CODES](https://developer.android.com/reference/android/os/Build.VERSION_CODES) - API level constants; API 36 = Android 16, API 33 = TIRAMISU
- [Android Developers Blog - The Future is Adaptive: Orientation and Resizability Changes in Android 16](https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html) - Large-screen policy, requestedOrientation behavior, compliance requirements

### Secondary (MEDIUM confidence)

- [WebSearch verified with official docs](https://developer.android.com/reference/android/view/WindowMetrics) - WindowMetrics available API 30+, reflects drawable window bounds accounting for system UI and fold position
- [Android Developers - Support Multi-Window Mode](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-multi-window-mode) - Manifest resizableActivity configuration, testing split-screen, window size handling
- [Samsung Developer - Galaxy Z Emulator Skin](https://developer.samsung.com/galaxy-emulator-skin/galaxy-z.html) - Galaxy Z Fold 7, Z Flip 7 emulator skins, hinge angle simulation
- [Samsung Developer - Foldable Device Testing](https://developer.samsung.com/galaxy-z/testing.html) - Foldable testing tools, ADB hinge control, posture simulation

### Tertiary (LOW confidence, needs validation)

- [TedBlob - WindowMetrics Android Replacing Display Methods](https://www.tedblob.com/windowmetrics-android/) - Custom guide on WindowMetrics fallback patterns; not official but aligns with Android Developers guidance
- [Android Developer Training - Sensor-Based Orientation](https://google-developer-training.github.io/android-developer-advanced-course-practicals/unit-1-expand-the-user-experience/lesson-3-sensors/3-2-p-working-with-sensor-based-orientation/) - Position sensors for orientation; supplemental, not core to this phase

---

## Metadata

**Confidence breakdown:**

- **Standard stack (HIGH):** All libraries verified in official docs and project build.gradle; versions confirmed current as of 2026-04-10
- **Architecture patterns (HIGH):** All code examples sourced from official Android Developers guides, Material 3 documentation, and Jetpack WindowManager codelabs
- **Pitfalls (HIGH):** Derived from official best practices documentation and common issues documented in Android issue tracker
- **Environment availability (HIGH):** Emulator skins and tools verified through Samsung Developer Portal and Android Studio latest releases

**Research date:** 2026-04-10  
**Valid until:** 2026-05-10 (30 days; Android APIs stable; consider re-checking if new Android version released)

---
