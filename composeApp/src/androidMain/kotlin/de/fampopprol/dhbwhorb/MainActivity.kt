package de.fampopprol.dhbwhorb

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.TimetableParser
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.network.CustomDnsResolver
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorage
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageWrapper
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.database.createRoomDatabase
import de.fampopprol.dhbwhorb.data.storage.database.getDatabaseBuilder
import de.fampopprol.dhbwhorb.data.network.HttpClientInitializer
import de.fampopprol.dhbwhorb.data.storage.database.DatabaseInitializer
import de.fampopprol.dhbwhorb.data.network.HttpClientManager
import de.fampopprol.dhbwhorb.services.LectureService
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.services.notifications.LectureChangeMonitor
import de.fampopprol.dhbwhorb.services.notifications.NotificationManager
import de.fampopprol.dhbwhorb.services.notifications.NotificationServiceLocator
import de.fampopprol.dhbwhorb.services.notifications.LectureMonitorScheduler
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.ui.schedule.viewModels.TimetableViewModel
import de.fampopprol.dhbwhorb.widget.sync.WidgetSyncWorker
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine


class MainActivity : ComponentActivity() {

    // Services initialized once - state-backed for early UI rendering
    private var isInitialized by mutableStateOf(false)
    private var authenticationService by mutableStateOf<AuthenticationService?>(null)
    private var database by mutableStateOf<AppDatabase?>(null)
    private var notificationPreferencesInteractor by mutableStateOf<NotificationPreferencesInteractor?>(null)
    private var sharedHttpClient by mutableStateOf<HttpClient?>(null)
    private var sessionManager by mutableStateOf<SessionManager?>(null)

    // ViewModel at activity scope - persists across activity recreation
    private lateinit var timetableViewModel: TimetableViewModel
    
    // Lifecycle-aware manager for HttpClient for definitive cleanup
    private val httpClientManager = HttpClientManager()
    
    // Non-UI services
    private var notificationManager: NotificationManager? = null
    private var lectureMonitorScheduler: LectureMonitorScheduler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge rendering: system bars drawn under app content with automatic inset handling.
        // No deprecated setStatusBarColor/setNavigationBarColor calls needed; system theme provides colors.
        enableEdgeToEdge()
        Napier.d("enableEdgeToEdge() called - system bars will draw under app content", tag = "MainActivity")
        super.onCreate(savedInstanceState)

        setContent {
            Napier.d("Setting content with App(), isInitialized=$isInitialized", tag = "MainActivity")
            App(
                testAuthenticationService = authenticationService,
                database = database,
                notificationPreferencesInteractor = notificationPreferencesInteractor,
                sharedHttpClient = sharedHttpClient,
                sessionManager = sessionManager,
                timetableViewModel = if (::timetableViewModel.isInitialized) timetableViewModel else null,
                isInitialized = isInitialized
            )
        }

        // Register HttpClientManager for lifecycle-based cleanup
        lifecycle.addObserver(httpClientManager)

        // Add fold state detection for foldable devices
        // IMPORTANT: Launch without repeatOnLifecycle to avoid pause/resume cycles on screen wake
        // The listener will persist as long as the activity exists, which is correct behavior
        lifecycleScope.launch {
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
                        // TODO: Future enhancement — use foldingFeature.bounds to adapt layouts
                        // (e.g., two-column on book posture, avoid hinge in HALF_OPENED state)
                    } else {
                        Napier.d("No fold detected (phone or non-foldable tablet)", tag = "MainActivity")
                    }
                }
        }

        // Orientation lock: Portrait for phones, free rotation for tablets/foldables
        // Decision based on device form factor, NOT Android version
        if (isPhone()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Napier.d("Phone detected - locking to portrait orientation", tag = "MainActivity")
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            Napier.d("Tablet or foldable detected - allowing free rotation", tag = "MainActivity")
        }

        // Test logging to verify Napier is working
        Napier.d("MainActivity onCreate() called", tag = "MainActivity")
        Napier.i("App is starting...", tag = "MainActivity")

        // Initialize NotificationDispatcher with Android context
        NotificationDispatcher.initialize(this)
        Napier.d("NotificationDispatcher initialized", tag = "MainActivity")

        // 2. Launch initialization in background (Refer to .planning/phases/08-critical-stability/SERVICE_INITIALIZATION_ORDER.md)
        lifecycleScope.launch {
            // Task 2.3: Add Timeout-Based Initialization Fallback (500ms)
            val timeoutJob = launch {
                delay(500)
                if (!isInitialized) {
                    Napier.w("MainActivity: Initialization timed out (500ms), forcing UI render", tag = "MainActivity")
                    isInitialized = true
                }
            }

            initializeServicesAsync()
            
            timeoutJob.cancel()
            isInitialized = true
            Napier.i("MainActivity: Initialization complete, isInitialized set to true", tag = "MainActivity")
        }
    }

    private suspend fun initializeServicesAsync() = withContext(Dispatchers.IO) {
        Napier.d("Initializing services asynchronously...", tag = "MainActivity")

        // 1. Initialize database using DatabaseInitializer
        val db = DatabaseInitializer.initializeDatabaseAsync(getDatabaseBuilder(applicationContext))
        database = db

        // 2. Initialize HttpClient using HttpClientInitializer
        val client = HttpClientInitializer.initializeHttpClientAsync()
        httpClientManager.setClient(client)
        sharedHttpClient = client

        // Create session manager
        val secureStorage = SecureStorage()
        val secureStorageWrapper = SecureStorageWrapper(secureStorage)
        val session = SessionManager(secureStorageWrapper)
        sessionManager = session

        // Create authentication service
        val authService = AuthenticationService(
            sessionManager = session,
            client = client
        )
        authenticationService = authService
        Napier.d("AuthenticationService initialized", tag = "MainActivity")

        // Create API client
        val dualisApiClient = DualisApiClient(client = client)

        // Task 1.4: Lazy-Load API Clients & Parsers per Feature
        // Parsers are now lazy-loaded inside services

        // Create Dualis lecture service (parsers now lazy-loaded inside)
        val dualisLectureService = DualisLectureService(
            apiClient = dualisApiClient,
            sessionManager = session,
            authenticationService = authService,
            lectureEventDao = db.lectureDao(),
            lecturerDao = db.lecturerDao(),
            lectureLecturerCrossRefDao = db.lectureLecturerCrossRefDao()
        )
        Napier.d("DualisLectureService initialized", tag = "MainActivity")

        // Create lecture service with factory for lazy loading
        val lectureService = LectureService(
            database = db,
            dualisLectureServiceFactory = { dualisLectureService }
        )

        Napier.d("LectureService initialized", tag = "MainActivity")

        // Create TimetableViewModel at activity scope (persists across activity recreation)
        timetableViewModel = TimetableViewModel(
            lectureService = lectureService,
            lecturerDao = db.lecturerDao(),
            lectureLecturerCrossRefDao = db.lectureLecturerCrossRefDao()
        )
        Napier.d("TimetableViewModel initialized at activity scope", tag = "MainActivity")

        // Initialize notification preferences
        val notificationPreferences = NotificationPreferences(secureStorageWrapper)
        val prefInteractor = NotificationPreferencesInteractor(notificationPreferences)
        notificationPreferencesInteractor = prefInteractor
        Napier.d("NotificationPreferencesInteractor initialized", tag = "MainActivity")

        // Create LectureChangeMonitor with factory for lazy loading
        val lectureChangeMonitor = LectureChangeMonitor(
            dualisLectureServiceFactory = { dualisLectureService },
            lectureEventDao = db.lectureDao(),
            lectureLecturerCrossRefDao = db.lectureLecturerCrossRefDao()
        )
        Napier.d("LectureChangeMonitor initialized", tag = "MainActivity")

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

        // Observe BOTH preferences to start/stop scheduler (only if scheduler was initialized)
        // Combine both flows so scheduler reacts to changes in either toggle
        if (lectureMonitorScheduler != null) {
            launch {
                combine(
                    prefInteractor.notificationsEnabled,
                    prefInteractor.lectureAlertsEnabled
                ) { notificationsEnabled, lectureAlertsEnabled ->
                    Pair(notificationsEnabled, lectureAlertsEnabled)
                }.collect { (notificationsEnabled, lectureAlertsEnabled) ->
                    val shouldSchedule = notificationsEnabled && lectureAlertsEnabled

                    Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = "MainActivity")
                    Napier.d("📱 PREFERENCE CHANGE DETECTED (Android)", tag = "MainActivity")
                    Napier.d("   Master notifications toggle: $notificationsEnabled", tag = "MainActivity")
                    Napier.d("   Lecture alerts toggle: $lectureAlertsEnabled", tag = "MainActivity")
                    Napier.d("   → Should schedule: $shouldSchedule", tag = "MainActivity")
                    Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                    if (shouldSchedule) {
                        Napier.d("✅ Both toggles enabled → Starting lecture monitoring scheduler...", tag = "MainActivity")
                        lectureMonitorScheduler?.schedule()
                    } else {
                        Napier.d("🛑 One or both toggles disabled → Stopping lecture monitoring scheduler...", tag = "MainActivity")
                        lectureMonitorScheduler?.cancel()
                    }
                }
            }
        }

        Napier.i("All services initialized successfully in background!", tag = "MainActivity")

        // Schedule periodic widget background sync
        WidgetSyncWorker.schedulePeriodicSync(applicationContext)
        Napier.d("Widget periodic sync scheduled", tag = "MainActivity")
    }

    /**
     * Determines if the device is a phone (not a tablet) based on screen size.
     *
     * Primary: API 30+ uses WindowMetrics for real-time bounds (works correctly on foldables
     * and split-screen where Configuration may report incorrect values).
     * Fallback: API 24-29 uses Configuration API (SCREENLAYOUT_SIZE_* and smallestScreenWidthDp).
     *
     * A device is considered a phone if its smallest dimension is < 600dp.
     * Foldables return phone=true when folded (~370dp) and phone=false when unfolded (~840dp).
     */
    private fun isPhone(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: Use WindowMetrics for real-time bounds
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val widthDp = bounds.width() / resources.displayMetrics.density
            val heightDp = bounds.height() / resources.displayMetrics.density

            // Consider phone if smallest dimension < 600dp
            val smallestDimensionDp = minOf(widthDp, heightDp)
            val result = smallestDimensionDp < 600
            Napier.d(
                "isPhone() detected: $result (WindowMetrics: ${bounds.width()}x${bounds.height()} → ${smallestDimensionDp}dp)",
                tag = "MainActivity"
            )
            result
        } else {
            // Fallback: Configuration API (API 24-29)
            val configuration = resources.configuration
            val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
            val isTabletByScreenSize = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE
            val isTabletByWidth = configuration.smallestScreenWidthDp >= 600
            val result = !isTabletByScreenSize && !isTabletByWidth
            Napier.d(
                "isPhone() detected: $result (Configuration fallback: screenLayout=${screenLayout}, sw${configuration.smallestScreenWidthDp}dp)",
                tag = "MainActivity"
            )
            result
        }
    }

    override fun onResume() {
        super.onResume()
        Napier.d("MainActivity onResume() called - checking if services are still initialized", tag = "MainActivity")
        // Verify authentication state after screen wake to detect unexpected logout
        if (sessionManager?.isAuthenticated() == false && isInitialized) {
            Napier.w("⚠️ UNEXPECTED LOGOUT DETECTED on onResume - session was cleared", tag = "MainActivity")
        }
    }

    override fun onPause() {
        super.onPause()
        Napier.d("MainActivity onPause() called", tag = "MainActivity")
    }

    override fun onDestroy() {
        super.onDestroy()

        // CRITICAL: Do NOT call logout() here. onDestroy() is called on configuration changes
        // (screen rotation, fold state changes, screen wake, etc.), not just on app termination.
        // Calling logout() on every onDestroy() would log out the user on screen wake.
        //
        // Logout should ONLY happen via explicit user action (SettingsPage logout button).
        // Session data is persisted in SecureStorage and survives activity recreation.
        //
        // Only cleanup resources that need active management:
        lifecycleScope.cancel()
        Napier.d("MainActivity onDestroy() called, lifecycle scope cancelled (NOT logging out)", tag = "MainActivity")
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}