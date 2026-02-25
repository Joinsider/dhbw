package de.joinside.dhbw

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import de.joinside.dhbw.data.dualis.remote.DualisApiClient
import de.joinside.dhbw.data.dualis.remote.parser.HtmlParser
import de.joinside.dhbw.data.dualis.remote.parser.TimetableParser
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.dualis.remote.services.DualisLectureService
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.network.CustomDnsResolver
import de.joinside.dhbw.data.storage.credentials.SecureStorage
import de.joinside.dhbw.data.storage.credentials.SecureStorageWrapper
import de.joinside.dhbw.data.storage.database.createRoomDatabase
import de.joinside.dhbw.data.storage.database.getDatabaseBuilder
import de.joinside.dhbw.services.LectureService
import de.joinside.dhbw.services.notifications.NotificationDispatcher
import de.joinside.dhbw.services.notifications.LectureChangeMonitor
import de.joinside.dhbw.services.notifications.NotificationManager
import de.joinside.dhbw.services.notifications.NotificationServiceLocator
import de.joinside.dhbw.services.notifications.LectureMonitorScheduler
import de.joinside.dhbw.data.storage.preferences.NotificationPreferences
import de.joinside.dhbw.data.storage.preferences.NotificationPreferencesInteractor
import de.joinside.dhbw.ui.schedule.viewModels.TimetableViewModel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine


class MainActivity : ComponentActivity() {

    // Services initialized once
    private lateinit var timetableViewModel: TimetableViewModel
    private lateinit var authenticationService: AuthenticationService
    private lateinit var database: de.joinside.dhbw.data.storage.database.AppDatabase
    private lateinit var notificationManager: NotificationManager
    private lateinit var lectureMonitorScheduler: LectureMonitorScheduler
    private lateinit var notificationPreferencesInteractor: NotificationPreferencesInteractor

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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

        // Initialize services
        initializeServices()

        setContent {
            Napier.d("Setting content with App()", tag = "MainActivity")
            App(
                testAuthenticationService = authenticationService,
                timetableViewModel = timetableViewModel,
                database = database,
                notificationPreferencesInteractor = notificationPreferencesInteractor
            )
        }
    }

    private fun initializeServices() {
        Napier.d("Initializing services...", tag = "MainActivity")

        // Create database with Android context
        database = createRoomDatabase(
            getDatabaseBuilder(applicationContext)
        )
        Napier.d("Database initialized", tag = "MainActivity")

        // Create shared HttpClient for cookie sharing with custom DNS resolver
        val sharedHttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpCookies)
            install(HttpTimeout) {
                socketTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                requestTimeoutMillis = 30000
            }

            // Configure OkHttp engine with custom DNS resolver
            engine {
                config {
                    dns(CustomDnsResolver())
                }
            }
        }
        Napier.d("Shared HttpClient created with custom DNS resolver", tag = "MainActivity")

        // Create session manager
        val secureStorage = SecureStorage()
        val secureStorageWrapper = SecureStorageWrapper(secureStorage)
        val sessionManager = SessionManager(secureStorageWrapper)

        // Create authentication service
        authenticationService = AuthenticationService(
            sessionManager = sessionManager,
            client = sharedHttpClient
        )
        Napier.d("AuthenticationService initialized", tag = "MainActivity")

        // Create API client
        val dualisApiClient = DualisApiClient(client = sharedHttpClient)

        // Create parsers
        val htmlParser = HtmlParser()
        val timetableParser = TimetableParser()

        // Create Dualis lecture service
        val dualisLectureService = DualisLectureService(
            apiClient = dualisApiClient,
            sessionManager = sessionManager,
            authenticationService = authenticationService,
            timetableParser = timetableParser,
            htmlParser = htmlParser,
            lectureEventDao = database.lectureDao(),
            lecturerDao = database.lecturerDao(),
            lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
        )
        Napier.d("DualisLectureService initialized", tag = "MainActivity")

        // Create lecture service
        val lectureService = LectureService(
            database = database,
            dualisLectureService = dualisLectureService
        )
        Napier.d("LectureService initialized", tag = "MainActivity")

        // Create timetable ViewModel
        timetableViewModel = TimetableViewModel(
            lectureService = lectureService,
            lecturerDao = database.lecturerDao(),
            lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
        )
        Napier.d("TimetableViewModel initialized", tag = "MainActivity")

        // Initialize notification preferences
        val notificationPreferences = NotificationPreferences(secureStorageWrapper)
        notificationPreferencesInteractor = NotificationPreferencesInteractor(notificationPreferences)
        Napier.d("NotificationPreferencesInteractor initialized", tag = "MainActivity")

        // Create LectureChangeMonitor
        val lectureChangeMonitor = LectureChangeMonitor(
            dualisLectureService = dualisLectureService,
            lectureEventDao = database.lectureDao(),
            lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao()
        )
        Napier.d("LectureChangeMonitor initialized", tag = "MainActivity")

        // Create NotificationManager
        val notificationDispatcher = NotificationDispatcher()
        notificationManager = NotificationManager(
            monitor = lectureChangeMonitor,
            dispatcher = notificationDispatcher,
            preferences = notificationPreferencesInteractor
        )

        // Register NotificationManager in ServiceLocator for Worker access
        NotificationServiceLocator.initialize(notificationManager)
        Napier.d("NotificationManager initialized and registered", tag = "MainActivity")

        // Initialize scheduler
        lectureMonitorScheduler = LectureMonitorScheduler(applicationContext)
        Napier.d("LectureMonitorScheduler initialized", tag = "MainActivity")

        // Observe BOTH preferences to start/stop scheduler
        // Combine both flows so scheduler reacts to changes in either toggle
        lifecycleScope.launch {
            combine(
                notificationPreferencesInteractor.notificationsEnabled,
                notificationPreferencesInteractor.lectureAlertsEnabled
            ) { notificationsEnabled, lectureAlertsEnabled ->
                Pair(notificationsEnabled, lectureAlertsEnabled)
            }.collect { (notificationsEnabled, lectureAlertsEnabled) ->
                val shouldSchedule = notificationsEnabled && lectureAlertsEnabled

                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = "MainActivity")
                Napier.d("📱 PREFERENCE CHANGE DETECTED (Android)", tag = "MainActivity")
                Napier.d("   Master notifications toggle: $notificationsEnabled", tag = "MainActivity")
                Napier.d("   Lecture alerts toggle: $lectureAlertsEnabled", tag = "MainActivity")
                Napier.d("   → Should schedule: $shouldSchedule", tag = "MainActivity")
                Napier.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", tag = "MainActivity")

                if (shouldSchedule) {
                    Napier.d("✅ Both toggles enabled → Starting lecture monitoring scheduler...", tag = "MainActivity")
                    lectureMonitorScheduler.schedule()
                } else {
                    Napier.d("🛑 One or both toggles disabled → Stopping lecture monitoring scheduler...", tag = "MainActivity")
                    lectureMonitorScheduler.cancel()
                }
            }
        }

        Napier.i("All services initialized successfully!", tag = "MainActivity")
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
        lifecycleScope.cancel()
        Napier.d("MainActivity onDestroy() called, lifecycle scope cancelled", tag = "MainActivity")
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}