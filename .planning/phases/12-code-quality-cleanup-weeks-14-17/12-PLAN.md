---
phase: 12-code-quality-cleanup-weeks-14-17
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt
  - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt
  - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt
  - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt
  - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt
  - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt
  - .planning/codebase/ARCHITECTURE.md
  - gradle/libs.versions.toml
  - composeApp/build.gradle.kts

autonomous: true
requirements: [CODE-01, CODE-02, CODE-03, CODE-04, CODE-05]

must_haves:
  truths:
    - "First frame renders 100ms faster on slow devices via theme caching"
    - "Theme preferences cached in CompositionLocal; multiple children read without repeated storage access"
    - "Dark mode toggles update theme instantly via LaunchedEffect Flow pattern"
    - "Database initialization failures auto-recover by deleting corrupted DB and retrying"
    - "Unrecoverable database failures show blocking error screen; user cannot navigate past"
    - "All database errors logged via Napier with timestamp, exception message, and DB file path"
    - "TimetableViewModel, GradesViewModel, DocumentsViewModel have separate StateFlows: isLoading, data, isRefreshing"
    - "All load operations serialize via Mutex; no concurrent fetches race-condition data updates"
    - "UI shows Loading → Loaded → Refreshing states in deterministic order; no flicker"
    - "Material3 version remains alpha (1.9.0-alpha04 or newer alpha); NOT updated to stable release"
    - "Initialization sequence and service ownership clearly documented in ARCHITECTURE.md"
    - "DI framework research (Koin) documented; adoption deferred to Phase 13"
  artifacts:
    - path: "composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt"
      provides: "LocalThemePrefs CompositionLocal definition and usage"
      exports: ["LocalThemePrefs", "DHBWHorbTheme"]
      min_lines: 30
    - path: "composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt"
      provides: "Theme LaunchedEffect initialization; theme preference flow subscription"
      exports: ["App"]
      contains: ["LaunchedEffect", "LocalThemePrefs", "Flow.collect"]
    - path: "composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt"
      provides: "Database initialization error recovery; try-catch with auto-delete-retry logic"
      exports: ["MainActivity", "DatabaseInitializer.initializeDatabaseAsync"]
      contains: ["try-catch", "File.delete()", "Napier.e"]
    - path: "composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt"
      provides: "Mutex-serialized data loading; separate StateFlows for isLoading, data, isRefreshing"
      exports: ["TimetableViewModel"]
      contains: ["Mutex", "StateFlow", "isLoading", "isRefreshing"]
    - path: "composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt"
      provides: "Mutex-serialized data loading; separate StateFlows for deterministic loading states"
      exports: ["GradesViewModel"]
      contains: ["Mutex", "StateFlow", "isLoading", "isRefreshing"]
    - path: "composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt"
      provides: "Mutex-serialized data loading; separate StateFlows for deterministic loading states"
      exports: ["DocumentsViewModel"]
      contains: ["Mutex", "StateFlow", "isLoading", "isRefreshing"]
    - path: ".planning/codebase/ARCHITECTURE.md"
      provides: "Lifecycle & Ownership section documenting initialization sequence, service ownership matrix, ViewModel lifecycle pattern, cleanup responsibilities"
      contains: ["## Lifecycle & Ownership", "Initialization Sequence", "Service Ownership Matrix", "ViewModel Lifecycle Pattern"]
      min_lines: 50
  key_links:
    - from: "App.kt LaunchedEffect"
      to: "Theme.kt LocalThemePrefs"
      via: "CompositionLocal provider"
      pattern: "CompositionLocalProvider(LocalThemePrefs)"
    - from: "SettingsPage dark mode toggle"
      to: "App.kt LaunchedEffect"
      via: "Theme preference Flow"
      pattern: "prefInteractor\\.darkMode\\.collect"
    - from: "MainActivity.initializeServicesAsync()"
      to: "DatabaseInitializer.initializeDatabaseAsync()"
      via: "try-catch with recovery"
      pattern: "try.*createRoomDatabase.*catch.*delete.*retry"
    - from: "TimetableViewModel.loadData()"
      to: "Database/API fetch"
      via: "Mutex.withLock()"
      pattern: "loadMutex\\.withLock"
    - from: "UI Composable"
      to: "ViewModel StateFlows"
      via: "Composed state values"
      pattern: "isLoading\\.value.*data\\.value.*isRefreshing\\.value"

---

<objective>
**Phase 12: Code Quality & Cleanup** improves code maintainability, error resilience, and consistency through:
1. Theme initialization optimization (100ms faster first frame via caching)
2. Database error recovery with auto-retry and blocking fallback
3. Lifecycle and service ownership documentation across platforms
4. Fixed race conditions in loading state transitions via Mutex + separate StateFlows
5. Confirmed Material3 version strategy (keep alpha for Expressive components)

**Purpose:** Ship a more stable, maintainable codebase with deterministic data loading and graceful error handling before v3.0 release.

**Output:** 
- Theme cached in CompositionLocal; loaded via LaunchedEffect
- Database errors auto-recover or show actionable error screen
- TimetableViewModel, GradesViewModel, DocumentsViewModel with Mutex + separate StateFlows
- ARCHITECTURE.md extended with Lifecycle & Ownership section
- Material3 alpha version documented; stable release deferred
</objective>

<execution_context>
@~/.copilot/get-shit-done/workflows/execute-plan.md
@~/.copilot/get-shit-done/templates/summary.md

## Key Prior Context

All changes in Phase 12 build on:
- Phase 8 (Critical Stability Fixes): Database async initialization, HttpClient async init, custom CoroutineScope cleanup pattern
- Phase 10 (Android API Compliance): WindowMetrics device detection, edge-to-edge display
- Phase 11 (Background Services): Conditional service init patterns, HttpClient lifecycle cleanup
</execution_context>

<context>
@.planning/ROADMAP.md (Phase 12, §Success Criteria, §Technical Approach)
@.planning/REQUIREMENTS.md (CODE-01 through CODE-05)
@.planning/STATE.md (Phase position, milestone structure)
@.planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md (Locked decisions D-01 through D-11)
@.planning/phases/12-code-quality-cleanup-weeks-14-17/12-DISCUSSION-LOG.md (Audit trail of gray area discussions)

## Codebase References

@.planning/codebase/CONVENTIONS.md (Naming: PascalCase for @Composable, error handling via Result wrappers, Napier logging)
@.planning/codebase/ARCHITECTURE.md (Current layered MVVM; will be extended with Lifecycle & Ownership section)

## Key Implementation Files

@composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt (DI initialization, where LaunchedEffect for theme will integrate)
@composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt (initializeServicesAsync at line ~170; database error recovery goes here)
@composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt (Theme composition; LocalThemePrefs integration)
@composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt (Example ViewModel with staged loading; add Mutex + StateFlows here)
@composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt (Similar pattern; apply same Mutex + StateFlows)
@composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt (Similar pattern; apply same Mutex + StateFlows)
@gradle/libs.versions.toml (Verify Material3 version constraint)
@composeApp/build.gradle.kts (Add Material3 Expressive dependency comment)
</context>

<tasks>

<task type="auto" tdd="false">
  <name>Task 1: Create Theme CompositionLocal and LaunchedEffect loader (CODE-01 Theme Initialization)</name>
  <files>
    composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt
    composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt
  </files>
  
  <read_first>
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt (lines 1-120)
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt (lines 70-150, current DI setup)
    - .planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md (D-01, D-02)
  </read_first>
  
  <action>
**Step 1: Define LocalThemePrefs CompositionLocal in Theme.kt**
- After line 88 (expect fun SystemAppearance), add:
```kotlin
import androidx.compose.runtime.compositionLocalOf

// LocalThemePrefs: Cached theme preferences to prevent prop drilling and repeated storage reads
// Used by App and all child composables to access current theme mode without reading storage
val LocalThemePrefs = compositionLocalOf<ThemePreferences?> { null }

// Data class for cached theme preferences
data class ThemePreferences(
    val darkMode: Boolean = false,
    val useMaterialYou: Boolean = true
)
```

**Step 2: Update DHBWHorbTheme to use CompositionLocal**
- Modify DHBWHorbTheme function signature to accept darkTheme parameter (already does at line 91)
- Wrap content in CompositionLocalProvider providing LocalThemePrefs
- Replace line 100-105 (after colorScheme creation) with:
```kotlin
CompositionLocalProvider(
    LocalThemePrefs provides ThemePreferences(
        darkMode = darkTheme,
        useMaterialYou = useMaterialYou
    )
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.Default,
        content = content
    )
}
```

**Step 3: Add LaunchedEffect for theme preload in App.kt**
- Add import at top of App.kt:
```kotlin
import de.fampopprol.dhbwhorb.ui.theme.LocalThemePrefs
import de.fampopprol.dhbwhorb.ui.theme.ThemePreferences
import kotlinx.coroutines.flow.StateFlow
```

- Inside App composable (line 68-81), after Napier initialization LaunchedEffect (line 92), add new LaunchedEffect:
```kotlin
// Load theme preferences from storage and cache in CompositionLocal
// This runs once on App composition to ensure theme is available before any child renders
var themePrefs by remember { mutableStateOf<ThemePreferences?>(null) }
var isDarkMode by remember { mutableStateOf(false) }

LaunchedEffect(Unit) {
    // Read initial theme preference from storage (SecureStorageWrapper.getPreference)
    val darkModeFromStorage = actualSecureStorage.getPreference("dark_mode", "false").toBoolean()
    isDarkMode = darkModeFromStorage
    themePrefs = ThemePreferences(
        darkMode = darkModeFromStorage,
        useMaterialYou = true
    )
    Napier.d("Theme preferences loaded: darkMode=$darkModeFromStorage", tag = "App")
}

// Subscribe to theme changes from notificationPreferencesInteractor.darkMode Flow
LaunchedEffect(notificationPreferencesInteractor) {
    notificationPreferencesInteractor?.darkMode?.collect { darkMode ->
        isDarkMode = darkMode
        themePrefs = ThemePreferences(
            darkMode = darkMode,
            useMaterialYou = true
        )
        Napier.d("Theme updated from storage: darkMode=$darkMode", tag = "App")
    }
}
```

**Step 4: Wrap content with CompositionLocalProvider in App.kt**
- Around line 200+ (where DHBWHorbTheme wraps current content), wrap the DHBWHorbTheme call:
```kotlin
CompositionLocalProvider(LocalThemePrefs provides (themePrefs ?: ThemePreferences())) {
    DHBWHorbTheme(
        darkTheme = isDarkMode,
        useMaterialYou = true,
        seedColor = Purple40,
        content = { /* ... existing content ... */ }
    )
}
```

**Rationale (from D-01, D-02):**
- Theme preloads in LaunchedEffect (KMP-compatible), not Application.onCreate
- CompositionLocal prevents prop drilling; all children access via LocalThemePrefs.current
- Batch updates: Flow subscription in LaunchedEffect re-reads and updates once per change, avoiding multiple recompositions
- First-frame render faster because theme is cached, not fetched from storage on every child composition
  </action>
  
  <verify>
    <automated>
grep -n "LocalThemePrefs" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt && \
grep -n "compositionLocalOf" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt && \
grep -n "ThemePreferences" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt && \
grep -n "CompositionLocalProvider(LocalThemePrefs" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt && \
grep -n "LaunchedEffect.*darkMode" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt
    </automated>
  </verify>
  
  <done>
    - LocalThemePrefs CompositionLocal defined in Theme.kt and exported
    - ThemePreferences data class created (darkMode, useMaterialYou)
    - App.kt has LaunchedEffect that reads theme from storage on compose startup
    - App.kt subscribes to notificationPreferencesInteractor.darkMode Flow for runtime updates
    - CompositionLocalProvider wraps content to provide cached theme to all children
    - Napier logs theme load and updates at DEBUG level
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 2: Implement database error recovery with auto-retry and blocking error screen (CODE-02 Database Error Recovery)</name>
  <files>
    composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt
    composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt
  </files>
  
  <read_first>
    - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt (lines 170-180, initializeServicesAsync)
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/storage/database/ (locate createRoomDatabase or DatabaseInitializer)
    - .planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md (D-03, D-04, D-05)
  </read_first>
  
  <action>
**Step 1: Update MainActivity.initializeServicesAsync() to wrap database init with error recovery**
- At line 173-175, find:
```kotlin
val db = DatabaseInitializer.initializeDatabaseAsync(getDatabaseBuilder(applicationContext))
database = db
```

- Replace with:
```kotlin
// Attempt database initialization with auto-recovery on corruption
val db = try {
    DatabaseInitializer.initializeDatabaseAsync(getDatabaseBuilder(applicationContext))
} catch (e: Exception) {
    Napier.e(
        "Database initialization failed: ${e.message}\nAttempting auto-recovery (delete and retry)",
        tag = "MainActivity"
    )
    
    try {
        // Step 1: Delete corrupted database file
        val dbFile = applicationContext.getDatabasePath("dhbw_horb")
        if (dbFile.exists()) {
            dbFile.delete()
            Napier.i("Corrupted database file deleted: ${dbFile.absolutePath}", tag = "MainActivity")
        }
        
        // Step 2: Retry initialization with fresh database
        val recoveredDb = DatabaseInitializer.initializeDatabaseAsync(getDatabaseBuilder(applicationContext))
        Napier.i("Database recovered successfully after deletion", tag = "MainActivity")
        recoveredDb
    } catch (recoveryError: Exception) {
        Napier.e(
            "Database auto-recovery failed: ${recoveryError.message}\nDatabase file path: ${applicationContext.getDatabasePath("dhbw_horb").absolutePath}",
            tag = "MainActivity"
        )
        null
    }
}

if (db == null) {
    // Unrecoverable failure: set flag to show blocking error screen in App
    database = null
    // Signal to App composable to show error screen (see Task 3)
}

database = db
```

**Step 2: Create a mutableState to signal database error to App.kt**
- At line 62-67 (after other mutableStateOf declarations), add:
```kotlin
private var databaseError by mutableStateOf<String?>(null)
```

- Modify the error handling above to set databaseError when recovery fails:
```kotlin
if (db == null) {
    databaseError = "Database error. Please reinstall the app or contact support."
    database = null
}
```

**Step 3: Update App() composable call to pass databaseError**
- At line 93-101 (setContent block), pass the databaseError:
```kotlin
App(
    testAuthenticationService = authenticationService,
    database = database,
    notificationPreferencesInteractor = notificationPreferencesInteractor,
    sharedHttpClient = sharedHttpClient,
    sessionManager = sessionManager,
    timetableViewModel = if (::timetableViewModel.isInitialized) timetableViewModel else null,
    isInitialized = isInitialized,
    databaseErrorMessage = databaseError  // NEW
)
```

**Step 4: Handle database error in App.kt**
- Add parameter to App() signature at line 71:
```kotlin
fun App(
    testAuthenticationService: AuthenticationService? = null,
    testCredentialsProvider: CredentialsStorageProvider? = null,
    testSecureStorage: de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface? = null,
    timetableViewModel: TimetableViewModel? = null,
    database: AppDatabase? = null,
    notificationPreferencesInteractor: NotificationPreferencesInteractor? = null,
    sharedHttpClient: HttpClient? = null,
    sessionManager: SessionManager? = null,
    isInitialized: Boolean = true,
    databaseErrorMessage: String? = null  // NEW
)
```

- Add early return to show blocking error screen if database failed:
```kotlin
// Handle unrecoverable database errors (show blocking error screen)
if (databaseErrorMessage != null) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Database Error",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = databaseErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Please restart the app. If the problem persists, please reinstall.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    return  // Block all navigation past this screen
}
```

**Rationale (from D-03, D-04, D-05):**
- Silent auto-recovery: Most database corruption is transient; deletion + retry catches it without user friction
- Blocking error screen: Unrecoverable corruption is rare; forces clean state via reinstall and ensures data consistency
- Structured Napier logging: Timestamp + "Database initialization failed" + exception message + DB file path for debugging
  </action>
  
  <verify>
    <automated>
grep -n "try.*DatabaseInitializer\\.initializeDatabaseAsync" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && \
grep -n "dbFile.delete()" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && \
grep -n "Napier.e.*Database initialization failed" composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt && \
grep -n "databaseErrorMessage" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt && \
grep -n "Database Error" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt
    </automated>
  </verify>
  
  <done>
    - MainActivity.initializeServicesAsync() wraps database init in try-catch
    - First catch block logs error and attempts auto-recovery (delete file + retry)
    - Second catch block logs unrecoverable error with database file path
    - databaseErrorMessage state added to MainActivity and passed to App()
    - App.kt shows blocking error screen if databaseErrorMessage is set
    - Error screen displays actionable message; blocks all navigation
    - All database errors logged via Napier.e() at ERROR level with structured information
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 3: Add Mutex + separate StateFlows to TimetableViewModel for deterministic loading (CODE-05 Race Condition Fix)</name>
  <files>
    composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt
  </files>
  
  <read_first>
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt (lines 1-150, current state management)
    - .planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md (D-08, D-09, D-10)
  </read_first>
  
  <action>
**Step 1: Add Mutex and separate StateFlow properties**
- Add imports at top (after line 26):
```kotlin
import kotlinx.coroutines.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
```

- Inside TimetableViewModel class (after line 44, after currentWeekOffset declaration), add:
```kotlin
// Race condition prevention: Mutex serializes all load operations
// Prevents concurrent initial fetch + refresh from race-conditioning data updates
private val loadMutex = Mutex()

// Three separate StateFlows for deterministic loading state management
// - isLoading: true during initial data fetch (skeleton data shown)
// - data: current data (empty initially, skeleton data, then full data)
// - isRefreshing: true during pull-to-refresh or background sync
private val _isLoading = MutableStateFlow<Boolean>(true)
val isLoading: StateFlow<Boolean> = _isLoading

private val _data = MutableStateFlow<List<LectureModel>>(emptyList())
val data: StateFlow<List<LectureModel>> = _data

private val _isRefreshing = MutableStateFlow<Boolean>(false)
val isRefreshing: StateFlow<Boolean> = _isRefreshing
```

**Step 2: Update loadLecturesForCurrentWeek to use Mutex**
- Find loadLecturesForCurrentWeek() function (around line 99), and wrap the coroutine launch with Mutex:
```kotlin
fun loadLecturesForCurrentWeek() {
    coroutineScope.launch {
        loadMutex.withLock {
            _isLoading.value = true
            try {
                val lectures = getDataWithRetry("loadLecturesForCurrentWeek") {
                    lectureService?.getWeeklyLectures(currentWeekOffset)
                }
                _data.value = lectures?.map { /* conversion */ } ?: emptyList()
                Napier.d("Loaded ${_data.value.size} lectures for current week", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Failed to load lectures for current week: ${e.message}", tag = TAG)
                _data.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

**Step 3: Add refresh and pagination methods with Mutex**
- Add new functions after cleanup():
```kotlin
fun refreshLectures() {
    coroutineScope.launch {
        loadMutex.withLock {
            _isRefreshing.value = true
            try {
                val lectures = getDataWithRetry("refreshLectures") {
                    lectureService?.getWeeklyLectures(currentWeekOffset)
                }
                _data.value = lectures?.map { /* conversion */ } ?: _data.value
                Napier.d("Refreshed ${_data.value.size} lectures", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Failed to refresh lectures: ${e.message}", tag = TAG)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

fun loadNextWeek() {
    coroutineScope.launch {
        loadMutex.withLock {
            _isLoading.value = true
            try {
                currentWeekOffset += 1
                val lectures = getDataWithRetry("loadNextWeek") {
                    lectureService?.getWeeklyLectures(currentWeekOffset)
                }
                _data.value = lectures?.map { /* conversion */ } ?: emptyList()
                Napier.d("Loaded ${_data.value.size} lectures for week offset $currentWeekOffset", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Failed to load next week: ${e.message}", tag = TAG)
                currentWeekOffset -= 1  // Revert offset on failure
                _data.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

**Step 4: Update UI consumers to use new StateFlows**
- In TimetablePage.kt (or wherever TimetableViewModel is used), update to consume three StateFlows:
```kotlin
val isLoading = timetableViewModel.isLoading.collectAsState()
val lectureData = timetableViewModel.data.collectAsState()
val isRefreshing = timetableViewModel.isRefreshing.collectAsState()

// UI logic:
if (isLoading.value) {
    // Show skeleton loading state
} else if (lectureData.value.isNotEmpty()) {
    // Show actual data
    LazyColumn {
        items(lectureData.value) { lecture ->
            LectureItem(lecture)
        }
    }
}

if (isRefreshing.value) {
    // Show refresh spinner in top-right or pull-to-refresh indicator
}
```

**Rationale (from D-08, D-09, D-10):**
- Mutex serializes all load operations; prevents concurrent initial fetch + refresh from race-conditioning updates
- Three StateFlows (isLoading, data, isRefreshing) provide flexible, testable state management
- Deterministic transitions: Loading → Loaded → Refreshing; no flicker between skeleton and full data
- Prevents polling: Flow-based reactive pattern instead of second fetch polling
  </action>
  
  <verify>
    <automated>
grep -n "private val loadMutex = Mutex()" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt && \
grep -n "MutableStateFlow.*isLoading" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt && \
grep -n "MutableStateFlow.*isRefreshing" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt && \
grep -n "loadMutex.withLock" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt && \
grep -n "fun refreshLectures()" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt && \
grep -n "fun loadNextWeek()" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt
    </automated>
  </verify>
  
  <done>
    - Mutex property added; all load operations wrap with withLock()
    - Three separate StateFlows added: isLoading, data, isRefreshing
    - loadLecturesForCurrentWeek() updated to use Mutex and StateFlows
    - refreshLectures() method created (pull-to-refresh support)
    - loadNextWeek() method created (pagination support)
    - All load operations set isLoading/isRefreshing appropriately
    - UI consumers can now distinguish initial load vs refresh vs loaded state
    - No concurrent operations; deterministic state transitions guaranteed
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 4: Add Mutex + separate StateFlows to GradesViewModel (CODE-05 Race Condition Fix)</name>
  <files>
    composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt
  </files>
  
  <read_first>
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt (lines 1-150, current state management)
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt (as reference for Mutex + StateFlow pattern from Task 3)
    - .planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md (D-08, D-09, D-10)
  </read_first>
  
  <action>
**Apply the same Mutex + StateFlow pattern from Task 3 to GradesViewModel**

**Step 1: Add imports**
```kotlin
import kotlinx.coroutines.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
```

**Step 2: Add Mutex and three StateFlows after constructor initialization**
- After the initial state declarations (similar to TimetableViewModel), add:
```kotlin
// Race condition prevention: Mutex serializes all load operations
private val loadMutex = Mutex()

// Three separate StateFlows for deterministic loading state
private val _isLoading = MutableStateFlow<Boolean>(true)
val isLoading: StateFlow<Boolean> = _isLoading

private val _data = MutableStateFlow<GradeList?>(null)
val data: StateFlow<GradeList?> = _data

private val _isRefreshing = MutableStateFlow<Boolean>(false)
val isRefreshing: StateFlow<Boolean> = _isRefreshing
```

**Step 3: Update loadGrades() to use Mutex**
- Wrap existing loadGrades logic with Mutex:
```kotlin
fun loadGrades() {
    coroutineScope.launch {
        loadMutex.withLock {
            _isLoading.value = true
            try {
                val grades = getDataWithRetry("loadGrades") {
                    /* existing grade fetch logic */
                }
                _data.value = grades
                Napier.d("Loaded grades successfully", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Failed to load grades: ${e.message}", tag = TAG)
                _data.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

**Step 4: Add refresh method with Mutex**
```kotlin
fun refreshGrades() {
    coroutineScope.launch {
        loadMutex.withLock {
            _isRefreshing.value = true
            try {
                val grades = getDataWithRetry("refreshGrades") {
                    /* existing grade fetch logic */
                }
                _data.value = grades
                Napier.d("Refreshed grades successfully", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Failed to refresh grades: ${e.message}", tag = TAG)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
```

**Step 5: Update UI in GradesPage to consume three StateFlows**
- Replace any existing single state with:
```kotlin
val isLoading = gradesViewModel.isLoading.collectAsState()
val gradeData = gradesViewModel.data.collectAsState()
val isRefreshing = gradesViewModel.isRefreshing.collectAsState()
```

**Rationale (from D-08, D-09, D-10):**
- Consistent pattern across all ViewModels (TimetableViewModel, GradesViewModel, DocumentsViewModel)
- Prevents race conditions between initial load and refresh
- Clear state semantics: loading vs refreshing vs loaded
  </action>
  
  <verify>
    <automated>
grep -n "private val loadMutex = Mutex()" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt && \
grep -n "MutableStateFlow.*isLoading" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt && \
grep -n "MutableStateFlow.*isRefreshing" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt && \
grep -n "loadMutex.withLock" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt && \
grep -n "fun refreshGrades()" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt
    </automated>
  </verify>
  
  <done>
    - Mutex property added to GradesViewModel
    - Three separate StateFlows added: isLoading, data, isRefreshing
    - loadGrades() wrapped with Mutex and StateFlow updates
    - refreshGrades() method created for pull-to-refresh support
    - GradesPage UI updated to consume three StateFlows
    - Consistent pattern with TimetableViewModel
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 5: Add Mutex + separate StateFlows to DocumentsViewModel (CODE-05 Race Condition Fix)</name>
  <files>
    composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt
  </files>
  
  <read_first>
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt (lines 1-150, current state management)
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt (as reference for Mutex + StateFlow pattern)
    - .planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md (D-08, D-09, D-10)
  </read_first>
  
  <action>
**Apply the same Mutex + StateFlow pattern from Tasks 3-4 to DocumentsViewModel**

**Step 1: Add imports**
```kotlin
import kotlinx.coroutines.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
```

**Step 2: Add Mutex and three StateFlows after constructor initialization**
```kotlin
// Race condition prevention: Mutex serializes all load operations
private val loadMutex = Mutex()

// Three separate StateFlows for deterministic loading state
private val _isLoading = MutableStateFlow<Boolean>(true)
val isLoading: StateFlow<Boolean> = _isLoading

private val _data = MutableStateFlow<List<DocumentMetadata>>(emptyList())
val data: StateFlow<List<DocumentMetadata>> = _data

private val _isRefreshing = MutableStateFlow<Boolean>(false)
val isRefreshing: StateFlow<Boolean> = _isRefreshing
```

**Step 3: Update loadDocuments() to use Mutex**
```kotlin
fun loadDocuments() {
    coroutineScope.launch {
        loadMutex.withLock {
            _isLoading.value = true
            try {
                val documents = getDataWithRetry("loadDocuments") {
                    /* existing document fetch logic */
                }
                _data.value = documents ?: emptyList()
                Napier.d("Loaded ${_data.value.size} documents", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Failed to load documents: ${e.message}", tag = TAG)
                _data.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

**Step 4: Add refresh method with Mutex**
```kotlin
fun refreshDocuments() {
    coroutineScope.launch {
        loadMutex.withLock {
            _isRefreshing.value = true
            try {
                val documents = getDataWithRetry("refreshDocuments") {
                    /* existing document fetch logic */
                }
                _data.value = documents ?: _data.value
                Napier.d("Refreshed documents", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Failed to refresh documents: ${e.message}", tag = TAG)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
```

**Step 5: Update UI in DocumentsPage to consume three StateFlows**
```kotlin
val isLoading = documentsViewModel.isLoading.collectAsState()
val documentData = documentsViewModel.data.collectAsState()
val isRefreshing = documentsViewModel.isRefreshing.collectAsState()
```

**Rationale (from D-08, D-09, D-10):**
- Consistent pattern across all ViewModels
- Prevents race conditions in document list loading and refresh
- Clear state semantics for loading, refreshing, and loaded states
  </action>
  
  <verify>
    <automated>
grep -n "private val loadMutex = Mutex()" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt && \
grep -n "MutableStateFlow.*isLoading" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt && \
grep -n "MutableStateFlow.*isRefreshing" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt && \
grep -n "loadMutex.withLock" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt && \
grep -n "fun refreshDocuments()" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt
    </automated>
  </verify>
  
  <done>
    - Mutex property added to DocumentsViewModel
    - Three separate StateFlows added: isLoading, data, isRefreshing
    - loadDocuments() wrapped with Mutex and StateFlow updates
    - refreshDocuments() method created for pull-to-refresh support
    - DocumentsPage UI updated to consume three StateFlows
    - Consistent pattern with TimetableViewModel and GradesViewModel
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 6: Extend ARCHITECTURE.md with Lifecycle & Ownership documentation (CODE-03 Lifecycle Management)</name>
  <files>
    .planning/codebase/ARCHITECTURE.md
  </files>
  
  <read_first>
    - .planning/codebase/ARCHITECTURE.md (current sections)
    - composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt (initializeServicesAsync, lifecycle observer setup)
    - composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt (DI initialization)
    - .planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md (D-06, D-07)
  </read_first>
  
  <action>
**Add a new "Lifecycle & Ownership" section to ARCHITECTURE.md after the "Cross-Cutting Concerns" section (around line 100)**

Insert this section:

```markdown
## Lifecycle & Ownership (Phase 12)

### Initialization Sequence

The app initializes in the following order to prevent race conditions and ensure services are available when needed:

1. **MainActivity.onCreate()** (Android entry point)
   - Calls `enableEdgeToEdge()` for system bar inset handling (Phase 10)
   - Registers `HttpClientManager` as a lifecycle observer for resource cleanup
   - Sets content with `App()` composable
   - Launches `initializeServicesAsync()` on `lifecycleScope` (Dispatchers.IO)

2. **initializeServicesAsync()** (lines 170-280 in MainActivity.kt)
   - **Database:** `DatabaseInitializer.initializeDatabaseAsync()`
     - Wrapped in try-catch with auto-recovery: delete corrupted DB, retry (Phase 12, D-03)
     - On failure: logs ERROR via Napier; sets `databaseError` state
   - **HttpClient:** `HttpClientInitializer.initializeHttpClientAsync()`
     - Passed to `httpClientManager.setClient()` for lifecycle-aware cleanup
   - **SecureStorage & SessionManager:** Created once; reused for all services
   - **AuthenticationService:** Created with SessionManager
   - **API Clients:** DualisApiClient, DualisLectureService, etc.
   - **Parsers:** Lazy-loaded inside services (not on main thread)
   - **Notification System:** Conditional initialization based on `notificationPreferencesInteractor.notificationsEnabled`
     - If notifications disabled, NotificationManager and LectureMonitorScheduler skip initialization (Phase 11, BG-01)

3. **App.kt Composition**
   - **Theme Initialization (Phase 12, D-01, D-02):**
     - LaunchedEffect reads theme preference from storage once
     - CompositionLocal provides cached theme to all children
     - Subscription to `notificationPreferencesInteractor.darkMode` Flow updates theme on user toggle
   - **View Model Initialization:** TimetableViewModel created at activity scope (persists across rotations)
   - **UI Composition:** Pages rendered with initialized services and ViewModels

### Service Ownership Matrix

| Service | Owner | Lifecycle | Cleanup | Phase |
|---------|-------|-----------|---------|-------|
| HttpClient | MainActivity | Activity lifecycle | `httpClientManager.onDestroy()` | 8, 11 |
| AppDatabase | MainActivity | Application lifetime | Automatic (Room) | 8 |
| SessionManager | MainActivity | Activity scope | Automatic (GC) | 8 |
| AuthenticationService | MainActivity | Activity scope | Automatic (GC) | 8 |
| TimetableViewModel | MainActivity | Activity scope (persists rotation) | `cleanup()` on new instance | 8 |
| GradesViewModel | App scope | Composable lifetime | `cleanup()` on dispose | 8 |
| DocumentsViewModel | App scope | Composable lifetime | `cleanup()` on dispose | 8 |
| NotificationManager | MainActivity | Conditional (notifications enabled) | Activity lifecycle | 11 |
| LectureMonitorScheduler | MainActivity | Conditional (notifications enabled) | WorkManager cleanup | 11 |
| WidgetSyncWorker | System (WorkManager) | Conditional (active widgets) | WorkManager cleanup | 11 |
| NotificationDispatcher | MainActivity | Activity lifecycle | Automatic (GC) | 8 |
| LectureChangeMonitor | MainActivity | Activity lifetime | Automatic (GC) | 8 |
| LectureService | MainActivity | Activity scope | Factory cleanup | 8 |

### ViewModel Lifecycle Pattern (Phase 8)

All ViewModels implement a **custom CoroutineScope + cleanup()** pattern instead of lifecycle-managed `viewModelScope`:

**Rationale:** Kotlin Multiplatform compatibility. `viewModelScope` is Android-only; custom scope works across Android, iOS, macOS, and Desktop.

**Implementation:**
```kotlin
class MyViewModel(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    fun cleanup() {
        Napier.d("Cleaning up MyViewModel", tag = TAG)
        coroutineScope.cancel()
    }
}
```

**Lifecycle Attachment (for TimetableViewModel only, as activity-scoped):**
- TimetableViewModel created in `MainActivity.initializeServicesAsync()`
- On activity recreation: new instance created, previous instance cleaned up
- Caller (MainActivity or activity manager) must call `viewModel.cleanup()` when discarding

**Lifecycle Attachment (for GradesViewModel, DocumentsViewModel, etc., as composable-scoped):**
- Created via `rememberSaveable { GradesViewModel(...) }`
- On composable disposal: `.cleanup()` called
- Recomposition without disposal: same instance reused

### Cleanup Responsibilities

| Component | Resource | Responsible Party | When |
|-----------|----------|-------------------|------|
| HttpClient | Connection pool | HttpClientManager (observer) | Activity onDestroy |
| Database | Query cursors | Room (automatic) | App shutdown |
| CoroutineScopes | Active jobs | ViewModel.cleanup() | Activity/Composable disposal |
| Preferences Flow | Subscription | LaunchedEffect cleanup | Composable disposal |
| WorkManager jobs | Tasks | WorkManager (automatic) | User disables feature or app uninstall |

### DI Framework Evaluation (Phase 12, D-06)

**Current Status (v3.0):** Manual dependency injection via constructor parameters and `remember` blocks in App.kt.

**Why not adopted:**
- Explicit and debuggable; service creation order is clear
- KMP compatibility; no platform-specific DI framework
- Late-cycle adoption risk; manual DI is proven stable

**Candidate Frameworks (Research Only):**
- **Koin:** KMP support; ServiceLocator pattern; easy to adopt
- **Hilt:** Android-only; tightly integrated with Lifecycle; excellent for Android but not KMP

**Decision:** Maintain manual DI in v3.0. Plan Koin evaluation and adoption for Phase 13 (post-v3.0 cleanup phase).

**Next Steps (Phase 13):**
- Research Koin integration with existing manual DI
- Prototype Koin configuration for viewModels
- Measure adoption complexity and test coverage impact
- If promising: plan Koin adoption for v3.1

---

*Lifecycle & Ownership documentation added in Phase 12*
*Last updated: 2026-04-10*
```

**Rationale (from D-06, D-07):**
- Clear initialization sequence prevents future confusion and race condition bugs
- Service ownership matrix makes lifecycle responsibilities explicit
- ViewModel pattern explanation justifies custom CoroutineScope for KMP
- DI framework evaluation documented; adoption deferred to Phase 13 per user decision
  </action>
  
  <verify>
    <automated>
grep -n "## Lifecycle & Ownership" .planning/codebase/ARCHITECTURE.md && \
grep -n "### Initialization Sequence" .planning/codebase/ARCHITECTURE.md && \
grep -n "### Service Ownership Matrix" .planning/codebase/ARCHITECTURE.md && \
grep -n "### ViewModel Lifecycle Pattern" .planning/codebase/ARCHITECTURE.md && \
grep -n "### Cleanup Responsibilities" .planning/codebase/ARCHITECTURE.md && \
grep -n "### DI Framework Evaluation" .planning/codebase/ARCHITECTURE.md
    </automated>
  </verify>
  
  <done>
    - New "Lifecycle & Ownership" section added to ARCHITECTURE.md
    - Initialization sequence documented with line references and phase citations
    - Service ownership matrix created (13 entries covering all major services)
    - ViewModel lifecycle pattern explained with rationale for custom CoroutineScope
    - Cleanup responsibilities table documents who owns cleanup for each resource
    - DI framework evaluation section explains decision to defer Koin to Phase 13
    - All sections reference specific file locations and line numbers for executor reference
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 7: Verify Material3 version and add Expressive dependency documentation (CODE-04 Material3 Version Management)</name>
  <files>
    gradle/libs.versions.toml
    composeApp/build.gradle.kts
  </files>
  
  <read_first>
    - gradle/libs.versions.toml (Material3 version entry)
    - composeApp/build.gradle.kts (Material3 dependency declaration)
    - .planning/phases/12-code-quality-cleanup-weeks-14-17/12-CONTEXT.md (D-11)
  </read_first>
  
  <action>
**Step 1: Verify Material3 version in gradle/libs.versions.toml**
- Find the line with `material3 = "..."` (likely around line 20-40)
- Current version should be `"1.9.0-alpha04"` or similar alpha version
- Do NOT update to `1.9.0` (stable) when released
- If version is already stable, revert to latest alpha that includes Expressive components
- Verify against Material3 releases: https://github.com/androidx/androidx/releases

- If update is needed, change to a more recent alpha if available (e.g., `"1.9.0-alpha05"` or `"1.9.0-alpha06"`)
- Log the decision in Napier or as a code comment

**Step 2: Add Expressive dependency comment in composeApp/build.gradle.kts**
- Find the Material3 dependency declaration (likely `androidx.compose.material3:material3`)
- Add an inline comment or a multi-line KDoc comment above it:
```gradle
// Material3 Expressive Components:
// App uses Material3 Expressive (advanced animations, typography variations) which is only available in alpha releases.
// Do NOT upgrade to stable (1.9.0) release — it will break Expressive component imports.
// Keep current alpha version (1.9.0-alpha04 or newer alpha) for Expressive functionality.
// See Phase 12 decision D-11 for rationale.
implementation("androidx.compose.material3:material3:${libs.versions.material3.get()}")
```

**Step 3: Document the decision in code**
- In App.kt, add a comment near theme initialization (around line 14, with Material3 import):
```kotlin
// Material3 Expressive Components (Phase 12, D-11):
// App uses Material3 Expressive which requires alpha releases.
// Stable 1.9.0 release will break Expressive components.
// Keep alpha version; update only to newer alphas if needed for bug fixes.
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
```

**Step 4: Verify no breaking changes in material3 alpha**
- Grep for any deprecated or removed APIs between current version and next alpha:
```bash
grep -r "ExperimentalMaterial3ExpressiveApi" composeApp/src --include="*.kt"
```
- Ensure all Expressive API calls still work with current version
- If breaking changes found: document in Napier.w() and plan upgrade for Phase 13

**Rationale (from D-11):**
- Material3 Expressive components are alpha-only
- Stable 1.9.0 would break Expressive functionality
- Functionality > version stability in this case
- Keep alpha indefinitely until Expressive reaches stable release (likely v2.0+)
  </action>
  
  <verify>
    <automated>
grep -n "material3" gradle/libs.versions.toml && \
grep -n "Expressive" composeApp/build.gradle.kts && \
grep -n "ExperimentalMaterial3ExpressiveApi" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/App.kt && \
grep -n "MaterialExpressiveTheme" composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/theme/Theme.kt
    </automated>
  </verify>
  
  <done>
    - Material3 version verified in gradle/libs.versions.toml (alpha version confirmed)
    - Material3 dependency comment added to composeApp/build.gradle.kts explaining Expressive requirement
    - Code comment added in App.kt near Expressive imports documenting Phase 12 decision D-11
    - No stable 1.9.0 upgrade applied; alpha version maintained
    - All Expressive API calls verified to work with current alpha version
    - Decision to defer Material3 stable update documented
  </done>
</task>

</tasks>

<verification>
**Phase 12 Success Verification Checklist**

All must-haves verified by tasks:

✓ **Theme Initialization (CODE-01, Tasks 1):**
- [ ] LocalThemePrefs CompositionLocal defined in Theme.kt and exported
- [ ] App.kt LaunchedEffect reads theme preference once on startup
- [ ] App.kt subscribes to notificationPreferencesInteractor.darkMode Flow for runtime updates
- [ ] CompositionLocalProvider wraps content to provide cached theme to all children
- [ ] Verify first frame renders 100ms faster (manual test on Snapdragon 680 device)

✓ **Database Error Recovery (CODE-02, Task 2):**
- [ ] MainActivity.initializeServicesAsync() wraps database init in try-catch
- [ ] First catch auto-recovers: delete DB file + retry immediately
- [ ] Second catch logs ERROR and sets databaseError state for blocking error screen
- [ ] App.kt shows blocking error screen if databaseErrorMessage is set
- [ ] Error screen blocks all navigation
- [ ] All database errors logged via Napier.e() with timestamp, exception message, DB file path

✓ **Race Condition Fix (CODE-05, Tasks 3-5):**
- [ ] TimetableViewModel has Mutex, three StateFlows (isLoading, data, isRefreshing)
- [ ] GradesViewModel has Mutex, three StateFlows (isLoading, data, isRefreshing)
- [ ] DocumentsViewModel has Mutex, three StateFlows (isLoading, data, isRefreshing)
- [ ] All load operations acquire Mutex before executing
- [ ] refresh() methods added to all ViewModels with Mutex serialization
- [ ] UI composes three StateFlows correctly (shows Loading → Loaded → Refreshing)
- [ ] No concurrent fetches race-condition data updates (Mutex guarantees serialization)
- [ ] Manual test: no UI flicker between skeleton data and full data

✓ **Lifecycle Documentation (CODE-03, Task 6):**
- [ ] ARCHITECTURE.md extended with "Lifecycle & Ownership" section
- [ ] Initialization sequence documented with line references (MainActivity.onCreate → initializeServicesAsync → App composition)
- [ ] Service ownership matrix created (13 services with owner, lifecycle, cleanup info)
- [ ] ViewModel lifecycle pattern explained (custom CoroutineScope + cleanup() for KMP compatibility)
- [ ] Cleanup responsibilities table documents who owns cleanup for each resource
- [ ] DI framework evaluation section explains decision to defer Koin to Phase 13

✓ **Material3 Version (CODE-04, Task 7):**
- [ ] Material3 version verified in gradle/libs.versions.toml (alpha confirmed)
- [ ] NOT updated to stable 1.9.0 release
- [ ] Expressive dependency comment added to composeApp/build.gradle.kts
- [ ] Phase 12 decision D-11 documented in code comments
- [ ] All Expressive API calls verified to work with current alpha version

**Cross-Cutting Verification:**
- [ ] All 5 CODE-01 through CODE-05 requirements addressed in plan
- [ ] No new dependencies added (Mutex is stdlib; StateFlow is stdlib)
- [ ] All code follows CONVENTIONS.md patterns (camelCase, Napier logging, Result wrappers)
- [ ] All manual DI dependencies tracked in ARCHITECTURE.md Service Ownership Matrix
- [ ] Phase 8, 10, 11 decisions (async init, lifecycle-aware resources) still respected
</verification>

<success_criteria>
**Phase 12 Complete When:**

1. ✓ Theme cached in CompositionLocal; loaded via LaunchedEffect; first frame 100ms faster
2. ✓ Database errors auto-recover or show blocking error screen with actionable message
3. ✓ TimetableViewModel, GradesViewModel, DocumentsViewModel implement Mutex + 3 StateFlows
4. ✓ All load operations serialize via Mutex; no concurrent race conditions
5. ✓ UI shows deterministic Loading → Loaded → Refreshing state transitions
6. ✓ No UI flicker between skeleton data and full data
7. ✓ ARCHITECTURE.md extended with Lifecycle & Ownership section (50+ lines)
8. ✓ Service ownership matrix documents 13+ major services
9. ✓ DI framework research (Koin) documented; adoption deferred to Phase 13
10. ✓ Material3 alpha version maintained; stable release deferred indefinitely
11. ✓ All database errors logged via Napier with structured format
12. ✓ All code follows CONVENTIONS.md (naming, error handling, logging)
13. ✓ All 5 requirements (CODE-01 through CODE-05) fully addressed
14. ✓ Phase 11 and prior optimizations (async init, resource cleanup) maintained
</success_criteria>

<output>
After completion, create `.planning/phases/12-code-quality-cleanup-weeks-14-17/12-PLAN-01-SUMMARY.md` with:
- Objective recap
- Files modified (list with line counts)
- Requirements addressed (CODE-01 through CODE-05 with task mapping)
- Key decisions implemented (D-01 through D-11)
- Testing recommendations
- Next phase dependencies (Phase 13 blocked by this; ready for Phase 13 planning)
</output>

---

## PLANNING COMPLETE

**Phase:** 12-code-quality-cleanup-weeks-14-17  
**Plan:** 01  
**Wave:** 1 (autonomous, no checkpoints)  
**Effort:** 3-4 weeks  
**Dependencies:** None (ready to execute immediately after Phases 8-11)

### Summary

Phase 12 Plan 01 delivers all 5 CODE requirements through 7 focused tasks:

| Task | Requirement | Objective | Effort |
|------|-------------|-----------|--------|
| 1 | CODE-01 | Theme CompositionLocal + LaunchedEffect loader | 2-3h |
| 2 | CODE-02 | Database error recovery with auto-retry + blocking screen | 2-3h |
| 3 | CODE-05 | TimetableViewModel: Mutex + 3 StateFlows | 2-3h |
| 4 | CODE-05 | GradesViewModel: Mutex + 3 StateFlows | 1.5-2h |
| 5 | CODE-05 | DocumentsViewModel: Mutex + 3 StateFlows | 1.5-2h |
| 6 | CODE-03 | ARCHITECTURE.md: Lifecycle & Ownership section | 2-3h |
| 7 | CODE-04 | Material3 version verification + documentation | 1-1.5h |

**Total Effort:** ~13-18 hours (2-3 days for experienced executor)

### Key Decisions Implemented

- **D-01, D-02:** Theme loads in LaunchedEffect (not Application.onCreate); cached in LocalThemePrefs CompositionLocal
- **D-03, D-04, D-05:** Database auto-recovers silently or shows blocking error; all failures logged via Napier
- **D-06, D-07:** Manual DI maintained in v3.0; Koin research planned for Phase 13; ARCHITECTURE.md extended
- **D-08, D-09, D-10:** Mutex + separate StateFlows for all ViewModels; no concurrent race conditions
- **D-11:** Material3 alpha version maintained; stable release deferred for Expressive component support

### Files Modified

9 files across 5 areas:
- **Theme:** 2 files (Theme.kt, App.kt)
- **Database:** 1 file (MainActivity.kt)
- **ViewModels:** 3 files (TimetableViewModel, GradesViewModel, DocumentsViewModel)
- **Documentation:** 1 file (ARCHITECTURE.md)
- **Build Config:** 2 files (gradle/libs.versions.toml, composeApp/build.gradle.kts)

### Next Steps

1. Execute Plan 01 (all tasks autonomous, no blocking checkpoints)
2. Run unit tests for Mutex + StateFlow implementation (executor creates tests)
3. Manual testing on Snapdragon 680+ device to verify theme performance (100ms improvement)
4. Manual testing on Android 14, 15, 16; iOS; Desktop for Material3 consistency
5. Phase 13 ready for planning after Phase 12 completion

---

*Created: 2026-04-10*  
*Mode: Standard Phase Planning*  
*Planner: GSD Phase Planning*
