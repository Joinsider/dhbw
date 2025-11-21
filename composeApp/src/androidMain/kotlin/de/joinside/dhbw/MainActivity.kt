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
import de.joinside.dhbw.data.dualis.remote.DualisApiClient
import de.joinside.dhbw.data.dualis.remote.parser.HtmlParser
import de.joinside.dhbw.data.dualis.remote.parser.TimetableParser
import de.joinside.dhbw.data.dualis.remote.services.AuthenticationService
import de.joinside.dhbw.data.dualis.remote.services.DualisLectureService
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.storage.credentials.SecureStorage
import de.joinside.dhbw.data.storage.credentials.SecureStorageWrapper
import de.joinside.dhbw.data.storage.database.createRoomDatabase
import de.joinside.dhbw.data.storage.database.getDatabaseBuilder
import de.joinside.dhbw.services.LectureService
import de.joinside.dhbw.ui.schedule.viewModels.TimetableViewModel
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies

class MainActivity : ComponentActivity() {

    // Services initialized once
    private lateinit var timetableViewModel: TimetableViewModel
    private lateinit var authenticationService: AuthenticationService
    private lateinit var database: de.joinside.dhbw.data.storage.database.AppDatabase

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

        // Initialize services
        initializeServices()

        setContent {
            Napier.d("Setting content with App()", tag = "MainActivity")
            App(
                testAuthenticationService = authenticationService,
                timetableViewModel = timetableViewModel,
                database = database
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

        // Create shared HttpClient for cookie sharing
        val sharedHttpClient = HttpClient {
            expectSuccess = false
            install(HttpCookies)
        }
        Napier.d("Shared HttpClient created", tag = "MainActivity")

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
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}