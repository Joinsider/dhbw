# Phase 8 Plan: Critical Stability Fixes

**Phase Goal:** Eliminate ANR (Application Not Responding) crashes and memory leaks by moving heavy initialization off the main thread and implementing proper coroutine cleanup in ViewModels.

**Success Criteria:**
1. App responds within 2 seconds on Android 12+ devices (time to first interaction)
2. Zero ANR reports in next release cycle
3. Memory usage remains stable after 5+ screen transitions without leaks
4. Visual loading indicator (skeleton UI) shown during initialization
5. All ViewModels have explicit cleanup() method implemented and called

**Status:** Ready for Execution

---

## Implementation Tasks

### Task Group 1: MainActivity Initialization Refactoring (Foundation)

#### 1.1: Refactor MainActivity.onCreate() for Early UI Rendering
**Decision Reference:** D-02 (Skeleton UI), D-08 (Async Database), D-09 (Eager HttpClient)

**Current Problem:**
- Lines 55-87 in MainActivity.kt: `initializeServices()` blocks main thread before `setContent()`
- Heavy I/O (database, HttpClient) happens synchronously
- User sees blank screen until all services initialize (500ms+ on slow devices)

**Task Steps:**
1. Move `setContent()` call to line 58 (immediately after `enableEdgeToEdge()` and `super.onCreate()`)
2. Refactor UI to render skeleton states for:
   - TimetablePage: empty week grid with placeholder rows
   - GradesPage: empty grade list with skeleton loaders
   - DocumentsPage: empty document list with skeleton loaders
3. Create `initializeServicesAsync()` coroutine-based method to replace synchronous `initializeServices()`
4. Launch `initializeServicesAsync()` via `lifecycleScope.launch` after `setContent()`

**Implementation Details:**
- Keep sync code path minimal (only NotificationDispatcher.initialize needed before UI render)
- Use `lifecycleScope.launch` (already used at line 195) for background initialization
- Preserve all logging via Napier for debugging initialization order

**Success Criteria:**
- `setContent()` called within 50ms of onCreate() start
- App UI renders with skeleton states in <500ms (verified via Profiler)
- No crash when pressing back before initialization completes
- All initialization happens in background (Dispatchers.IO)

**Files to Modify:**
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (lines 55-227)
- UI pages (TimetablePage, GradesPage, DocumentsPage) to add skeleton states

**Dependencies:** None (foundational task)

**Estimated:** 3-4 days

---

#### 1.2: Extract Database Initialization to Background Coroutine
**Decision Reference:** D-08 (Async Database Initialization)

**Current Problem:**
- Line 93-96: `createRoomDatabase()` blocks main thread
- Heavy disk I/O for Room setup happens synchronously

**Task Steps:**
1. Create `DatabaseInitializer` class in `data/storage/database/` with:
   ```kotlin
   suspend fun initializeDatabaseAsync(context: Context): AppDatabase
   ```
2. Move `createRoomDatabase()` and `getDatabaseBuilder()` calls into this function
3. Launch from MainActivity via `lifecycleScope.launch`:
   ```kotlin
   var database: AppDatabase? = null
   lifecycleScope.launch {
       database = DatabaseInitializer.initializeDatabaseAsync(applicationContext)
       Napier.d("Database ready", tag = "MainActivity")
   }
   ```
4. Implement timeout-based fallback: if database not ready after 500ms, render UI anyway
5. ViewModels that depend on database must check `if (database != null)` before querying

**Alternative for ViewModel Data Queries:**
- If database not yet initialized, wrap in try-catch and show "Loading..." state
- Retry query once database is ready via Flow/StateFlow from database initialization

**Success Criteria:**
- Database initialization happens on `Dispatchers.IO`
- No main thread blocking measured in Profiler
- App UI renders before database ready
- First ViewModel query waits for database if needed (no crash)
- Profiler shows <100ms main thread impact

**Files to Modify:**
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/storage/database/DatabaseInitializer.kt` (NEW)
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (integrate async init)
- All ViewModels with database access (add null safety checks)

**Dependencies:** Task 1.1 (must have async initialization infrastructure)

**Estimated:** 2-3 days

---

#### 1.3: Extract HttpClient Initialization to Background Coroutine
**Decision Reference:** D-09 (Eager HttpClient), D-03 (Resource Cleanup)

**Current Problem:**
- Lines 99-114: HttpClient creation with OkHttp engine setup blocks main thread
- Network setup (DNS resolver, timeouts, cookies) happens synchronously

**Task Steps:**
1. Create `HttpClientInitializer` class in `data/network/` with:
   ```kotlin
   suspend fun initializeHttpClientAsync(): HttpClient
   ```
2. Move HttpClient creation (lines 99-114) into this function
3. Store HttpClient reference in MainActivity property:
   ```kotlin
   private var sharedHttpClient: HttpClient? = null
   ```
4. Launch initialization via lifecycleScope:
   ```kotlin
   lifecycleScope.launch {
       sharedHttpClient = HttpClientInitializer.initializeHttpClientAsync()
       Napier.d("HttpClient ready", tag = "MainActivity")
   }
   ```
5. API clients (DualisApiClient, AuthenticationService) created on-demand after HttpClient ready

**HttpClient Usage Pattern:**
- AuthenticationService checks `if (sharedHttpClient != null)` before making requests
- If not yet ready, defer first login attempt to after HttpClient initialization
- Provide public getter: `fun getHttpClient(): HttpClient?` (allows retry)

**Resource Cleanup (D-03):**
- Store HttpClient in MainActivity property
- In `onDestroy()` (line 256), add:
  ```kotlin
  sharedHttpClient?.close()
  Napier.d("HttpClient closed", tag = "MainActivity")
  ```

**Success Criteria:**
- HttpClient initialization on `Dispatchers.IO`
- No main thread blocking for HttpClient setup
- App interactive before HttpClient ready
- HttpClient properly closed in onDestroy()
- Profiler shows no "too many open connections" errors after app restart cycle

**Files to Modify:**
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/network/HttpClientInitializer.kt` (NEW)
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (integrate async init, add cleanup)
- `AuthenticationService` and API clients (add null safety for HttpClient)

**Dependencies:** Task 1.1 (must have async initialization infrastructure)

**Estimated:** 2-3 days

---

#### 1.4: Lazy-Load API Clients & Parsers per Feature
**Decision Reference:** D-01 (Lazy Per-Feature), D-04 (DocumentsViewModel Lazy Load)

**Current Problem:**
- Lines 129-147: All API clients, parsers, and services created synchronously
- DualisDocumentService, DualisLectureService created even if user never uses Documents
- TimetableParser, HtmlParser instantiated but unused until first load

**Task Steps:**

**Part A: TimetableViewModel API Client (Eager on First Access)**
1. Refactor TimetableViewModel constructor to accept lazy-loaded `LectureService`
2. Move DualisLectureService initialization into `LectureService`:
   ```kotlin
   class LectureService {
       private var dualisLectureService: DualisLectureService? = null
       
       suspend fun getDualisService(): DualisLectureService {
           if (dualisLectureService == null) {
               dualisLectureService = createDualisLectureService()
           }
           return dualisLectureService!!
       }
   }
   ```
3. First call to `LectureService.getLecturesForWeek()` triggers initialization
4. Subsequent calls reuse same service instance

**Part B: DocumentsViewModel API Client (Lazy on Tab Access)**
1. DocumentsViewModel NOT created in MainActivity.onCreate()
2. In DocumentsPage composable, use `rememberSaveable`:
   ```kotlin
   val documentsViewModel = rememberSaveable {
       DocumentsViewModel(documentService = createDocumentServiceOnDemand())
   }
   ```
3. DualisDocumentService created only when DocumentsPage first rendered
4. Service instance cached in ViewModel for reuse

**Part C: Parser Lazy Creation**
1. Move TimetableParser, HtmlParser instantiation into DualisLectureService
2. Create parsers only when first API call needs them (lazy delegate pattern)

**Success Criteria:**
- TimetableViewModel creates DualisLectureService on first lecture load (not onCreate)
- DocumentsViewModel created on first DocumentsPage render (not onCreate)
- DualisDocumentService never created if Documents tab never accessed
- Parsers created on-demand (lazy delegates)
- Startup time reduced by 15-20% for users who only use Timetable (verified via Profiler)
- No crashes when accessing features before services initialize

**Files to Modify:**
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/LectureService.kt` (add lazy service getter)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/DocumentService.kt` (create with lazy init)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/DualisLectureService.kt` (lazy parsers)
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (remove eager initialization)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/pages/DocumentsPage.kt` (add rememberSaveable)

**Dependencies:** Task 1.1 (foundation), Task 1.2 (database ready)

**Estimated:** 3-4 days

---

### Task Group 2: ViewModel Lifecycle & Coroutine Management (KMP-Aware)

#### 2.1: Implement cleanup() Method Pattern in All ViewModels
**Decision Reference:** D-06 (ViewModel CoroutineScope), D-07 (Scope Isolation)

**Current Implementation Status:**
- TimetableViewModel (line 33): Already has `CoroutineScope(Dispatchers.IO)` property
- Missing: `cleanup()` method to cancel scope on ViewModel disposal

**Task Steps:**

**Step 1: Add cleanup() to TimetableViewModel**
1. Add method to TimetableViewModel:
   ```kotlin
   fun cleanup() {
       coroutineScope.cancel()
       Napier.d("TimetableViewModel cleanup() called", tag = TAG)
   }
   ```
2. Verify all coroutines in ViewModel launched via `coroutineScope.launch`

**Step 2: Add cleanup() to GradesViewModel**
1. Locate `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt`
2. Add same `CoroutineScope` property if missing
3. Add `cleanup()` method

**Step 3: Add cleanup() to DocumentsViewModel**
1. Locate or create DocumentsViewModel in `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt`
2. Add `CoroutineScope(Dispatchers.IO)` property
3. Add `cleanup()` method

**Step 4: Template Other ViewModels**
1. Find all ViewModels in `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/**/viewModels/`
2. Apply same pattern: CoroutineScope property + cleanup() method
3. Document pattern in code comment

**Success Criteria:**
- All ViewModels have `cleanup()` method
- Method cancels their `coroutineScope`
- No runtime errors when cleanup() called
- Napier logs cleanup() calls for debugging

**Files to Modify:**
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt`
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/viewModels/GradesViewModel.kt`
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/viewModels/DocumentsViewModel.kt`
- All other ViewModels found in `ui/**/viewModels/` directory

**Dependencies:** None (independent task)

**Estimated:** 1-2 days

---

#### 2.2: Call ViewModel.cleanup() on Composable Disposal
**Decision Reference:** D-06 (Cleanup Responsibility), D-05 (Cancel In-Flight Operations)

**Current Problem:**
- ViewModels created in MainActivity but cleanup() never called
- Orphaned coroutines continue running after ViewModel no longer visible
- Memory leak: coroutineScope lives until MainActivity destroyed (not when ViewModel disposed)

**Task Steps:**

**For TimetableViewModel (MainActivity):**
1. In MainActivity `onDestroy()` (line 256), add:
   ```kotlin
   timetableViewModel.cleanup()
   ```
2. Order matters: cleanup before `lifecycleScope.cancel()`

**For GradesViewModel & DocumentsViewModel (Composables):**
1. In each page composable (TimetablePage, GradesPage, DocumentsPage):
   ```kotlin
   @Composable
   fun TimetablePage(timetableViewModel: TimetableViewModel) {
       DisposableEffect(timetableViewModel) {
           onDispose {
               timetableViewModel.cleanup()
           }
       }
       
       // Rest of composable...
   }
   ```
2. Apply same pattern to GradesPage and DocumentsPage
3. For DocumentsViewModel created lazily: cleanup in DocumentsPage's DisposableEffect

**For Navigation Safety (D-05):**
1. If user navigates away mid-load:
   - Composable's DisposableEffect triggers onDispose
   - ViewModel.cleanup() cancels all in-flight coroutines
   - No orphaned tasks continue running
2. Verify in Profiler that coroutines stop on back navigation

**Success Criteria:**
- Profiler CoroutineScope view shows no "CANCELLED" scopes hanging around
- No orphaned coroutine warnings in logs
- Memory stable after 5+ navigation cycles (back, forward, back, etc.)
- cleanup() called on app exit (verified in logs)

**Files to Modify:**
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (add cleanup in onDestroy)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/pages/TimetablePage.kt` (add DisposableEffect)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/grades/pages/GradesPage.kt` (add DisposableEffect)
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/documents/pages/DocumentsPage.kt` (add DisposableEffect)

**Dependencies:** Task 2.1 (cleanup() methods must exist)

**Estimated:** 2-3 days

---

#### 2.3: Add Timeout-Based Initialization Fallback
**Decision Reference:** D-08 (Async Database with Timeout), D-02 (Skeleton States)

**Current Problem:**
- If database or HttpClient initialization is slow, UI may appear stuck
- No mechanism to prevent "frozen on splash screen" on very slow devices

**Task Steps:**

**Timeout Mechanism:**
1. After `setContent()` in MainActivity, add timeout check:
   ```kotlin
   lifecycleScope.launch {
       val timeoutJob = launch {
           delay(500) // 500ms timeout
           if (database == null) {
               Napier.w("Database not ready after 500ms, proceeding with skeleton UI", tag = "MainActivity")
           }
       }
       
       // Database initialization continues...
       database = DatabaseInitializer.initializeDatabaseAsync(applicationContext)
       timeoutJob.cancel()
   }
   ```
2. Skeleton UI renders immediately (no blocking)
3. If database ready before timeout, UI updates gracefully
4. If database still loading after timeout, continue showing skeleton state until ready

**Implementation in ViewModels:**
1. TimetableViewModel checks database availability:
   ```kotlin
   private suspend fun getLecturesWithFallback(weekOffset: Int): List<LectureEventEntity> {
       return try {
           lectureService.getLecturesForWeek(weekOffset)
       } catch (e: Exception) {
           // Database not ready or error
           Napier.d("Database query failed: ${e.message}, retrying...", tag = TAG)
           // Show loading state and retry
           emptyList()
       }
   }
   ```

**Success Criteria:**
- App renders UI within 500ms regardless of database/HttpClient status
- UI transitions smoothly from skeleton to real data without jank
- No "stuck on splash" experience on Snapdragon 680 or slower
- Timeout logged for debugging slow device issues

**Files to Modify:**
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (add timeout logic)
- TimetableViewModel, GradesViewModel (add fallback for early access)

**Dependencies:** Task 1.2, 1.3 (async initialization must exist)

**Estimated:** 1-2 days

---

### Task Group 3: Service Initialization Ordering & Safety

#### 3.1: Document Service Initialization Order
**Decision Reference:** All D-01 through D-09

**Current Problem:**
- No clear documentation of service initialization order
- Risk: services depend on others in undefined order
- Example: TimetableViewModel depends on LectureService depends on DualisLectureService depends on HttpClient

**Task Steps:**
1. Create `SERVICE_INITIALIZATION_ORDER.md` in `.planning/phases/08-critical-stability/`:
   ```
   # Service Initialization Order (Phase 8)
   
   ## Sync Initialization (on main thread, before setContent())
   1. NotificationDispatcher.initialize(context) — Android context required, fast
   
   ## Async Initialization (after setContent(), on Dispatchers.IO)
   2. Database initialization (createRoomDatabase) — Can be slow, no dependency
   3. HttpClient initialization — Can be slow, no dependency
   4. AuthenticationService (depends on HttpClient)
   5. DualisLectureService (lazy, depends on HttpClient)
   6. DualisDocumentService (lazy, depends on HttpClient)
   7. LectureMonitorScheduler (depends on database, notification service)
   
   ## Lazy Initialization (on first feature access)
   - DualisLectureService: initialized on first TimetableViewModel.loadLectures()
   - DualisDocumentService: initialized on first DocumentsPage render
   - Parsers: initialized when first API call needs them
   
   ## Critical Dependencies
   - ✅ Safe: UI renders before services ready (skeleton states handle missing data)
   - ✅ Safe: ViewModels check for service/database availability before querying
   - ✅ Safe: In-flight operations cancelled if ViewModel disposed (cleanup())
   ```

2. Document in code:
   - Add comment in MainActivity.onCreate() linking to this document
   - Add comment in each service class showing its dependencies

**Success Criteria:**
- SERVICE_INITIALIZATION_ORDER.md created and complete
- Each service/ViewModel has dependency comments
- Initialization order follows documented plan
- No circular dependencies

**Files to Modify:**
- `.planning/phases/08-critical-stability/SERVICE_INITIALIZATION_ORDER.md` (NEW)
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` (add comment link)
- All service classes (add dependency comments)

**Dependencies:** All tasks in Groups 1-2 (must understand final order)

**Estimated:** 1 day

---

#### 3.2: Add Null Safety Checks for Late-Initialized Services
**Decision Reference:** D-08 (Database may not be ready), D-09 (HttpClient may not be ready)

**Current Problem:**
- AuthenticationService, API clients depend on HttpClient (may be null early)
- TimetableViewModel depends on database (may be null early)
- No guards against null reference exceptions

**Task Steps:**

**AuthenticationService:**
1. Add null-check wrapper:
   ```kotlin
   fun login(username: String, password: String): Result<SessionInfo>? {
       return client?.let {
           // Login logic
       } ?: run {
           Napier.w("HttpClient not initialized yet", tag = TAG)
           null
       }
   }
   ```
2. Callers check if Result is null before proceeding

**DualisApiClient:**
1. Add guard in each API method:
   ```kotlin
   suspend fun getTimetable(): Result<List<Lecture>> {
       return if (client != null) {
           // API call
       } else {
           Napier.w("HttpClient not ready", tag = TAG)
           Result.failure(Exception("HttpClient not initialized"))
       }
   }
   ```

**ViewModels (Database):**
1. TimetableViewModel.loadLecturesForWeek():
   ```kotlin
   if (lectureService == null) {
       uiState = uiState.copy(
           isLoading = false,
           error = "Database not yet initialized"
       )
       return
   }
   ```

**Success Criteria:**
- No null reference exceptions when accessing services early
- Graceful error messages in UI if service not ready
- Logs show which service wasn't ready

**Files to Modify:**
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/DualisApiClient.kt`
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/data/dualis/remote/services/AuthenticationService.kt`
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/services/LectureService.kt`
- All ViewModels accessing database

**Dependencies:** Task 1.2, 1.3, 3.1 (must know final initialization order)

**Estimated:** 2 days

---

### Task Group 4: Memory Leak Prevention & Verification

#### 4.1: Verify No Orphaned Coroutines (Profiler Testing)
**Decision Reference:** D-05 (Cancel In-Flight Operations), D-06 (Cleanup on Disposal)

**Current Problem:**
- Cannot verify coroutine cleanup without profiler validation
- Risk: cleanup() method not actually cancelling coroutines

**Task Steps:**
1. Setup Android Profiler (Profiler tab in Android Studio)
2. Open app, navigate to Timetable page
3. Wait for lectures to load
4. Navigate back / close app
5. Check Profiler > Coroutines tab:
   - ✅ Expected: CoroutineScope shows "CANCELLED" state after cleanup()
   - ✗ Bug: CoroutineScope shows "RUNNING" — cleanup() not working

**Verification Procedure:**
1. Take baseline profiler dump with current code
2. Implement Task 2.1 (cleanup() methods)
3. Implement Task 2.2 (call cleanup in composables)
4. Rerun profiler test — should show "CANCELLED"
5. Repeat for GradesViewModel, DocumentsViewModel

**Success Criteria:**
- All ViewModels show "CANCELLED" state after DisposableEffect cleanup
- No "RUNNING" coroutines after back navigation
- Profiler shows 0 orphaned scopes after app exit
- Document findings in test report

**Test Report Location:**
- `.planning/phases/08-critical-stability/TEST_RESULTS_PROFILER.md`

**Dependencies:** Task 2.1, 2.2 (must have cleanup code to test)

**Estimated:** 1-2 days (profiler work)

---

#### 4.2: Verify Memory Stability (Memory Profiler)
**Decision Reference:** Phase 8 Success Criteria #3

**Current Problem:**
- Cannot verify "memory stable after 5+ screen transitions" without measurement
- Risk: hidden memory leaks in database queries, coroutines, or service instances

**Task Steps:**
1. Setup Memory Profiler in Android Studio
2. Open app and let it fully initialize
3. Record baseline heap snapshot
4. Perform 5+ navigation cycles:
   - Timetable → Grades → Documents → Timetable (repeat)
5. Record heap snapshots after each transition
6. Check for memory growth:
   - ✅ Expected: Heap stabilizes after 2-3 cycles (garbage collection)
   - ✗ Bug: Heap grows by >10MB per cycle — memory leak

**Analysis Criteria:**
1. Heap size after cycle N should be <5% larger than cycle N-1
2. No class instances show increasing count on repeat navigation
3. CoroutineScope instances released after cleanup()
4. Database connections returned to pool

**Success Criteria:**
- Memory stable within 5% after 5+ navigation cycles
- No instance count growth for ViewModels, Services
- Heap profiler shows garbage collection working
- Document findings in test report

**Test Report Location:**
- `.planning/phases/08-critical-stability/TEST_RESULTS_MEMORY.md`

**Dependencies:** Task 1.1-1.4 (all initialization code), Task 2.1-2.2 (cleanup code)

**Estimated:** 2-3 days (profiler work)

---

### Task Group 5: ANR Verification & Startup Performance

#### 5.1: Verify ANR Elimination via SystemTrace Profiler
**Decision Reference:** Phase 8 Success Criteria #1, #2

**Current Problem:**
- Cannot claim "zero ANR" without profiler validation
- Need to measure "time to first interaction" and verify <2 seconds

**Task Steps:**
1. Setup Android Profiler > System Trace
2. Record trace from app launch to first interaction point:
   - App appears on screen
   - User can tap buttons
   - Timetable renders with real data
3. Check System Trace for:
   - ✅ Expected: Main thread not blocked >500ms
   - ✗ Bug: Main thread has 1+ second block — ANR risk

**Measurement Points:**
1. **Time to First Paint (T1):** onCreate() starts to setContent() renders
   - Expected: <500ms
   - Measured: Mark in trace where Composable first paints to screen

2. **Time to First Interaction (T2):** setContent() to UI becomes tappable
   - Expected: <2 seconds
   - Measured: Mark in trace where main thread idle and can handle user input

3. **Time to Full Data (T3):** First interaction to timetable loads real data
   - Expected: <3 seconds
   - Measured: Mark where timetable has real lectures (not skeletons)

**Test Procedure:**
1. Build APK in release mode (not debug)
2. Run on physical device (Android 12+):
   - Pixel 6 (baseline)
   - Pixel 4A (older device to check compatibility)
   - Or Galaxy A12 (budget device)
3. Record 3+ runs and average results
4. Compare against current baseline (if exists)

**Success Criteria:**
- T1 (first paint) <500ms
- T2 (first interaction) <2 seconds
- T3 (full data) <3 seconds
- No main thread blocks >500ms in trace
- Zero ANR marks in SystemTrace

**Test Report Location:**
- `.planning/phases/08-critical-stability/TEST_RESULTS_STARTUP.md`

**Documentation:**
- Include SystemTrace screenshots showing main thread timeline
- Note device, OS version, data conditions

**Dependencies:** Task 1.1-1.4 (all refactoring complete)

**Estimated:** 2-3 days (multiple test runs)

---

#### 5.2: Document Startup Performance Baseline
**Decision Reference:** D-01 (Lazy loading should reduce startup for Timetable-only users)

**Current Problem:**
- Cannot measure "15-20% startup reduction" without baseline
- Need to compare lazy loading impact

**Task Steps:**
1. From Task 5.1, extract startup metrics:
   - Full app startup with all services
   - Startup with Documents feature never accessed
2. Compare lazy-loading impact:
   - Time with eager DocumentsViewModel init (D-04 violation)
   - Time with lazy DocumentsViewModel init (D-04 correct)
   - Expected difference: 100-200ms

3. Document in performance report

**Success Criteria:**
- Baseline metrics recorded
- Lazy loading shows measurable improvement
- Report shows device + OS version used
- Metrics reproducible on test device

**Files to Modify:**
- `.planning/phases/08-critical-stability/TEST_RESULTS_STARTUP.md` (add lazy load comparison)

**Dependencies:** Task 1.4 (lazy loading), Task 5.1 (profiler setup)

**Estimated:** 1 day

---

### Task Group 6: Integration Testing & Sign-Off

#### 6.1: Create Phase 8 Integration Test Suite
**Current Problem:**
- No automated tests verifying initialization order, cleanup, or memory behavior
- Manual profiler testing is time-consuming and error-prone

**Task Steps:**

**Unit Tests (in `composeApp/src/commonTest/`):**
1. Test ViewModel cleanup() cancels coroutineScope:
   ```kotlin
   @Test
   fun testTimetableViewModelCleanupCancelsScope() {
       val viewModel = TimetableViewModel(...)
       viewModel.cleanup()
       // Verify coroutineScope is cancelled
       assertTrue(viewModel.coroutineScope.isCancelled)
   }
   ```

2. Test lazy-loading of API clients:
   ```kotlin
   @Test
   fun testLectureServiceLazyLoadsApiClient() {
       val lectureService = LectureService(...)
       assertTrue(lectureService.isDualisServiceInitialized() == false)
       
       // First access initializes
       runBlocking { lectureService.getDualisService() }
       assertTrue(lectureService.isDualisServiceInitialized() == true)
       
       // Second access reuses
       // ... verify same instance
   }
   ```

3. Test timeout fallback:
   ```kotlin
   @Test
   fun testMainActivityRendersBeforeDatabaseReady() {
       // Simulate slow database
       val mainActivity = createMainActivity()
       // Verify setContent called before DatabaseInitializer finishes
       assertTrue(uiRenderedTime < databaseInitTime)
   }
   ```

**Integration Tests (in `composeApp/src/androidTest/`):**
1. Test full app startup:
   ```kotlin
   @Test
   fun testAppStartupWithinTimeLimit() {
       val startTime = System.currentTimeMillis()
       launchActivity(MainActivity::class.java)
       
       // UI should be responsive within 2 seconds
       val interactiveTime = System.currentTimeMillis() - startTime
       assertTrue(interactiveTime < 2000)
   }
   ```

2. Test memory leak on navigation:
   ```kotlin
   @Test
   fun testNoMemoryLeakOnNavigation() {
       // Navigate multiple times
       // Assert heap size stable
   }
   ```

**Success Criteria:**
- Unit tests pass (all cleanup verified)
- Integration tests pass (timing verified)
- Tests reproducible on CI/CD pipeline
- Document test setup in TEST_SETUP.md

**Files to Create:**
- `composeApp/src/commonTest/kotlin/.../ViewModelCleanupTest.kt`
- `composeApp/src/commonTest/kotlin/.../LazyLoadingTest.kt`
- `composeApp/src/androidTest/kotlin/.../StartupPerformanceTest.kt`
- `.planning/phases/08-critical-stability/TEST_SETUP.md`

**Dependencies:** Task 1.1-1.4, 2.1-2.2 (code to test)

**Estimated:** 2-3 days

---

#### 6.2: Phase 8 Verification Checklist & Sign-Off
**Decision Reference:** All Phase 8 Success Criteria

**Task Steps:**
1. Complete verification checklist:
   ```
   Startup Performance (Task 5.1):
   - [ ] T1 (first paint) <500ms
   - [ ] T2 (first interaction) <2 seconds
   - [ ] T3 (full data) <3 seconds
   - [ ] No main thread blocks >500ms
   - [ ] Android 12+ tested
   
   ANR Elimination (Task 5.1):
   - [ ] SystemTrace shows no ANR conditions
   - [ ] No "Application Not Responding" in Play Console (next release)
   
   Memory Stability (Task 4.2):
   - [ ] Memory <5% growth after 5+ transitions
   - [ ] No orphaned coroutines (Profiler)
   - [ ] ViewModel instances released on cleanup
   
   ViewModel Cleanup (Task 2.1-2.2):
   - [ ] All ViewModels have cleanup() method
   - [ ] cleanup() called on composable disposal
   - [ ] cleanup() called in MainActivity.onDestroy()
   
   Service Initialization (Task 1.1-1.4):
   - [ ] setContent() called <50ms from onCreate() start
   - [ ] Database initialized async (Task 1.2)
   - [ ] HttpClient initialized async (Task 1.3)
   - [ ] API clients lazy-loaded (Task 1.4)
   - [ ] DocumentsViewModel lazy-loaded (Task 1.4)
   
   Integration Tests Pass (Task 6.1):
   - [ ] Unit tests pass (cleanup, lazy load, timeout)
   - [ ] Integration tests pass (startup, memory)
   ```

2. Create final sign-off document

**Files to Modify:**
- `.planning/phases/08-critical-stability/VERIFICATION_CHECKLIST.md` (NEW)
- `.planning/phases/08-critical-stability/SIGN_OFF.md` (NEW)

**Dependencies:** All prior tasks in this plan

**Estimated:** 1 day

---

## Integration Points Summary

| File | Change Type | Risk Level | Blocking Dependencies | Notes |
|------|-------------|-----------|---------------------|-------|
| MainActivity.kt | Major Refactor | HIGH | None | Split sync+async init; early setContent(); add cleanup calls |
| TimetableViewModel.kt | Add cleanup() | LOW | Database ready | Add cleanup() method; call from DisposableEffect |
| GradesViewModel.kt | Add cleanup() | LOW | Database ready | Same pattern as TimetableViewModel |
| DocumentsViewModel.kt | Add cleanup() + lazy load | MEDIUM | HttpClient ready | Lazy create via rememberSaveable; add cleanup() |
| App.kt (composable) | Add DisposableEffect | LOW | ViewModel cleanup() exists | Call cleanup on page disposal |
| TimetablePage.kt | Add DisposableEffect | LOW | ViewModel cleanup() exists | Wrap ViewModel in DisposableEffect |
| GradesPage.kt | Add DisposableEffect | LOW | ViewModel cleanup() exists | Same pattern |
| DocumentsPage.kt | Add DisposableEffect | LOW | ViewModel cleanup() exists | Same pattern |
| DualisApiClient.kt | Add null checks | LOW | HttpClient initialized | Guard against null HttpClient early |
| AuthenticationService.kt | Add null checks | LOW | HttpClient initialized | Guard against null HttpClient early |
| LectureService.kt | Add lazy getter | MEDIUM | D-01 decision | Lazy initialize DualisLectureService |
| DatabaseInitializer.kt | NEW | MEDIUM | None | Extract database init logic |
| HttpClientInitializer.kt | NEW | MEDIUM | None | Extract HttpClient init logic |
| SERVICE_INITIALIZATION_ORDER.md | NEW | LOW | All tasks | Document final initialization order |

---

## Critical Dependencies & Blockers

### Database Initialization
- **Blocker:** Database must be thread-safe and support concurrent access
- **Risk:** Early database queries before initialization complete
- **Mitigation:** ViewModels check database availability; defer queries if not ready

### HttpClient Initialization
- **Blocker:** HttpClient must be thread-safe (OkHttp is thread-safe)
- **Risk:** API calls before HttpClient ready; connection pool exhaustion
- **Mitigation:** API clients check HttpClient availability; explicit close() in onDestroy()

### ViewModel Cleanup & Navigation
- **Blocker:** DisposableEffect must fire before ViewModel garbage collected
- **Risk:** Cleanup called after ViewModel already removed from composition
- **Mitigation:** Call cleanup in both DisposableEffect (composable) AND onDestroy (Activity)

### Initialization Order Chain
```
setContent() MUST happen before:
├─ Database initialization (separate background coroutine)
├─ HttpClient initialization (separate background coroutine)
└─ All service initialization

ViewModel creation depends on:
├─ Database ready (for DB access)
├─ HttpClient ready (for API calls)
└─ API client creation (lazy)
```

---

## Verification Checklist

### Phase 8 Success Criteria Verification

- [ ] **Startup Performance:** App responds within 2 seconds (T2 from Task 5.1)
  - Measured on Android 12+ device (Pixel 6, Galaxy A12)
  - SystemTrace shows main thread not blocked >500ms
  - Tested 3+ times, results averaged

- [ ] **ANR Elimination:** Zero ANR reports in next release cycle
  - Pre-Phase 8: Establish baseline ANR count from Play Console
  - Post-Phase 8: Compare against new baseline
  - SystemTrace shows no ANR conditions at startup

- [ ] **Memory Stability:** Memory <5% growth after 5+ transitions
  - Memory Profiler shows heap stabilizes after 2-3 cycles
  - ViewModel, Service instances released on navigation
  - Profiler CoroutineScope shows "CANCELLED" after cleanup

- [ ] **Visual Loading Feedback:** Skeleton UI renders before data ready
  - App visible within 500ms (Task 1.1)
  - Skeleton states for Timetable, Grades, Documents pages
  - Smooth transition from skeleton to real data

- [ ] **Test Coverage:** Unit + integration tests pass
  - Test ViewModel cleanup() cancels scope
  - Test lazy-loading initializes services on-demand
  - Test timeout fallback renders UI before database ready
  - Integration test verifies startup performance within limits

### Task-Level Verification

Each task above has "Success Criteria" section. Verify all before sign-off:
- Task 1.1: UI renders <500ms before services ready
- Task 1.2: Database initialization async, no main thread block
- Task 1.3: HttpClient initialization async, proper cleanup in onDestroy()
- Task 1.4: Lazy-loading reduces startup 15-20% for Timetable-only users
- Task 2.1: All ViewModels have cleanup() method
- Task 2.2: cleanup() called on composable disposal and MainActivity.onDestroy()
- Task 2.3: UI renders even if database/HttpClient delayed (timeout fallback)
- Task 3.1: SERVICE_INITIALIZATION_ORDER.md created with all dependencies documented
- Task 3.2: API clients and ViewModels handle null services gracefully
- Task 4.1: Profiler shows no orphaned coroutines after cleanup
- Task 4.2: Memory Profiler shows stable heap after 5+ transitions
- Task 5.1: SystemTrace confirms T1 <500ms, T2 <2s, T3 <3s
- Task 5.2: Baseline metrics documented, lazy loading impact measured
- Task 6.1: Unit + integration tests created and passing
- Task 6.2: Verification checklist completed, sign-off document created

---

## Risk Mitigations

| Risk | Impact | Likelihood | Mitigation Strategy | Assigned Task |
|------|--------|-----------|---------------------|----------------|
| Database init not async | Critical | Medium | Use Dispatchers.IO, verify with Profiler | Task 1.2 |
| HttpClient cleanup fails | High | Low | Explicit close() in onDestroy(), Profiler check | Task 1.3 |
| ViewModel cleanup never called | High | Medium | Use DisposableEffect in composables + onDestroy | Task 2.2 |
| Orphaned coroutines persist | High | Medium | Verify with Profiler CoroutineScope tab | Task 4.1 |
| Memory leaks on navigation | High | Low | Memory Profiler after 5+ cycles | Task 4.2 |
| App appears frozen on slow devices | Medium | Low | Timeout-based fallback, skeleton UI | Task 2.3 |
| API calls before HttpClient ready | Medium | Medium | Null checks in API clients | Task 3.2 |
| Initialization order undefined | Medium | Medium | Document SERVICE_INITIALIZATION_ORDER.md | Task 3.1 |
| Database not ready for first query | Medium | Low | Try-catch in ViewModel, retry logic | Task 3.2 |
| Test flakiness on CI/CD | Medium | Low | Run tests 3+ times, average results | Task 6.1 |

---

## Implementation Schedule

**Recommended Wave Pattern (4 weeks):**

**Week 1:**
- Day 1-2: Task 1.1 (early setContent, skeleton UI)
- Day 3-4: Task 2.1 (add cleanup() methods)
- Day 5: Task 3.1 (document initialization order)

**Week 2:**
- Day 1-2: Task 1.2 (async database init)
- Day 3-4: Task 1.3 (async HttpClient init)
- Day 5: Task 2.3 (timeout fallback)

**Week 3:**
- Day 1-2: Task 1.4 (lazy-load API clients)
- Day 3-4: Task 2.2 (call cleanup in composables)
- Day 5: Task 3.2 (null safety checks)

**Week 4:**
- Day 1: Task 4.1 (verify coroutine cleanup with Profiler)
- Day 2: Task 4.2 (verify memory stability)
- Day 3-4: Task 5.1 + 5.2 (startup performance baseline)
- Day 5: Task 6.1 + 6.2 (integration tests + sign-off)

---

## References

**Architecture Decisions:**
- D-01: API Clients & Parsers – Lazy Per-Feature
- D-02: Visual Loading Feedback – Skeleton/Placeholder States
- D-03: HttpClient Resource Cleanup – MainActivity.onDestroy()
- D-04: Documents Feature – Lazy Load on Tab Access
- D-05: Navigation Safety – Cancel In-Flight Operations
- D-06: ViewModel CoroutineScope Pattern – KMP-Aware
- D-07: Scope Isolation Strategy – Single Shared Scope
- D-08: Database Blocking Strategy – Async + Timeout Fallback
- D-09: HttpClient Timing – Eager on Background Thread

**Input Files:**
- `.planning/phases/08-critical-stability/08-CONTEXT.md` — Phase context and decisions
- `.planning/ROADMAP.md` — Phase 8 goals and success criteria
- `composeApp/src/androidMain/kotlin/de/fampopprol/dhbwhorb/MainActivity.kt` — Current initialization code
- `composeApp/src/commonMain/kotlin/de/fampopprol/dhbwhorb/ui/schedule/viewModels/TimetableViewModel.kt` — ViewModel pattern

---

**Plan Status:** Ready for Execution  
**Created:** 2026-04-10  
**Prepared by:** Software Architect (GSD Planner)

