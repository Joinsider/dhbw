package de.fampopprol.dhbwhorb.integration

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import de.fampopprol.dhbwhorb.MainActivity
import de.fampopprol.dhbwhorb.widget.TimetableGlanceWidget
import de.fampopprol.dhbwhorb.widget.sync.WidgetSyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Instrumented tests for the Android background work around the widget.
 *
 * Runs on a device, so it is outside the gate — `testDebugUnitTest` and `desktopTest` never see it.
 * That is exactly why it had rotted: eight tests, six of which ended in `assertTrue(true)` and
 * could not fail. P9 kept the three that can, and dropped the rest rather than leaving green
 * placeholders that look like coverage. What went, and why it could not be saved:
 *
 * * the two widget-sync tests that accepted either outcome — replaced by one that pins the
 *   outcome to the actual widget count,
 * * "the sync interval is 30 minutes" — the constant is private and `WorkInfo` does not carry the
 *   period, so nothing about it is observable from here,
 * * "the monitoring interval is the shipped one" — same problem, and its comment had already
 *   drifted: the interval moved from 15 minutes to an hour without the test noticing.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundServicesIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    /**
     * Periodic sync is scheduled if and only if the device actually has a widget placed.
     *
     * Asserted against the widget count rather than against a fixed expectation, because an
     * emulator may or may not have one — but whichever it is, the scheduling has to match it.
     */
    @Test
    fun periodicSyncIsScheduledExactlyWhenAWidgetExists() {
        val widgetCount = runBlocking {
            GlanceAppWidgetManager(context).getGlanceIds(TimetableGlanceWidget::class.java).size
        }

        WidgetSyncWorker.schedulePeriodicSync(context)

        val scheduled = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("widget_sync_periodic").get()

        assertEquals(
            widgetCount > 0,
            scheduled.isNotEmpty(),
            "Periodic sync should be enqueued for $widgetCount widget(s), but got ${scheduled.size} work item(s)"
        )
    }

    /** An immediate sync is enqueued unconditionally — there is no widget check on this path. */
    @Test
    fun immediateSyncIsEnqueued() {
        WidgetSyncWorker.enqueueImmediate(context)

        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("widget_sync_immediate").get()

        assertEquals(1, work.size, "Exactly one immediate widget sync should be enqueued")
    }

    /**
     * The app reaches RESUMED, and does so quickly.
     *
     * The launch itself is the assertion: `ActivityScenario.launch` fails the test if anything in
     * `MainActivity.onCreate` throws — which is how the Koin start order is covered here at all.
     */
    @Test
    fun mainActivityStartsWithinFiveSeconds() {
        val startTime = System.currentTimeMillis()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val launchTime = System.currentTimeMillis() - startTime

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertTrue(launchTime < 5000, "App should launch within 5 seconds (was $launchTime ms)")
        }
    }
}
