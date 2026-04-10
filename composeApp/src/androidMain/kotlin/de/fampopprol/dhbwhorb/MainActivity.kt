package de.fampopprol.dhbwhorb

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
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
    
    // Lifecycle-aware manager for HttpClient for definitive cleanup
    private val httpClientManager = HttpClientManager()
    
    // Non-UI services
    private var notificationManager: NotificationManager? = null
    private var lectureMonitorScheduler: LectureMonitorScheduler? = null

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Register HttpClientManager for lifecycle-based cleanup
        lifecycle.addObserver(httpClientManager)
        setContent {
            Napier.d("Setting content with App(), isInitialized=$isInitialized", tag = "MainActivity")
            App(
                testAuthenticationService = authenticationService,
                database = database,
                notificationPreferencesInteractor = notificationPreferencesInteractor,
                sharedHttpClient = sharedHttpClient,
                sessionManager = sessionManager,
                isInitialized = isInitialized
            )
        }

        // Lock orientation to portrait for phones only (not tablets)
        if (isPhone()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            Napier.d("Device detected as phone - locking to portrait orientation", tag = "MainActivity")
        } else {
            Napier.d("Device detected as tablet - allowing all orientations", tag = "MainActivity")
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

        // TimetableViewModel is now lazily initialized in TimetablePage
        // and its instantiation has been removed from here

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

        // Observe BOTH preferences to start/stop scheduler
        // Combine both flows so scheduler reacts to changes in either toggle
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
                    scheduler.schedule()
                } else {
                    Napier.d("🛑 One or both toggles disabled → Stopping lecture monitoring scheduler...", tag = "MainActivity")
                    scheduler.cancel()
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
     * Tablets typically have screen size XLARGE or are at least 600dp wide.
     */
    private fun isPhone(): Boolean {
        val configuration = resources.configuration
        val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK

        // Check if it's a large or xlarge screen (tablet)
        val isTabletByScreenSize = screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE

        // Additionally check smallest screen width (sw600dp is typical tablet threshold)
        val smallestScreenWidthDp = configuration.smallestScreenWidthDp
        val isTabletByWidth = smallestScreenWidthDp >= 600

        return !isTabletByScreenSize && !isTabletByWidth
    }

    override fun onResume() {
        super.onResume()
        Napier.d("MainActivity onResume() called", tag = "MainActivity")
    }

    override fun onPause() {
        super.onPause()
        Napier.d("MainActivity onPause() called", tag = "MainActivity")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup services and ViewModels
        authenticationService?.logout() // Clear session cache if needed
        
        lifecycleScope.cancel()
        Napier.d("MainActivity onDestroy() called, services cleaned up and lifecycle scope cancelled", tag = "MainActivity")
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}