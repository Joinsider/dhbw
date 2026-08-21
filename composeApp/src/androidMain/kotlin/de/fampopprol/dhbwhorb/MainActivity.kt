/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.services.notifications.LectureMonitorScheduler
import de.fampopprol.dhbwhorb.widget.sync.WidgetSyncWorker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The activity owns the window, not the object graph — `DualisApplication` starts Koin before any
 * activity exists, so there is nothing to initialise or wait for here.
 */
class MainActivity : ComponentActivity() {

    private val notificationPreferences: NotificationPreferencesInteractor by inject()
    private val lectureMonitorScheduler: LectureMonitorScheduler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // System bars draw under app content; insets are handled by the theme.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent { App() }

        // Portrait on phones, free rotation on tablets and foldables.
        requestedOrientation = if (isPhone()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_USER
        }

        observeNotificationPreferences()

        // Skips itself when no widget is placed.
        WidgetSyncWorker.schedulePeriodicSync(applicationContext)
    }

    /**
     * Background monitoring runs only while both toggles are on. Combining the flows means a change
     * to either one re-evaluates the schedule.
     */
    private fun observeNotificationPreferences() {
        lifecycleScope.launch {
            combine(
                notificationPreferences.notificationsEnabled,
                notificationPreferences.lectureAlertsEnabled
            ) { notificationsEnabled, lectureAlertsEnabled ->
                notificationsEnabled && lectureAlertsEnabled
            }.collect { shouldSchedule ->
                if (shouldSchedule) {
                    Napier.d("Notifications enabled, scheduling lecture monitoring", tag = TAG)
                    lectureMonitorScheduler.schedule()
                } else {
                    Napier.d("Notifications disabled, cancelling lecture monitoring", tag = TAG)
                    lectureMonitorScheduler.cancel()
                }
            }
        }
    }

    /**
     * A device counts as a phone when its smallest dimension is below 600dp. A folded foldable is
     * a phone, an unfolded one is not.
     *
     * API 30+ reads WindowMetrics, which stays correct in split-screen and on foldables where the
     * Configuration values can lag behind.
     */
    private fun isPhone(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            val density = resources.displayMetrics.density
            minOf(bounds.width() / density, bounds.height() / density) < 600
        } else {
            val configuration = resources.configuration
            val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
            screenLayout < Configuration.SCREENLAYOUT_SIZE_LARGE &&
                configuration.smallestScreenWidthDp < 600
        }
    }

    // Note: no logout in onDestroy(). It also runs on configuration changes — rotation, fold state,
    // screen wake — so logging out here would sign the user out on screen wake. Logout happens only
    // through the settings screen.

    private companion object {
        private const val TAG = "MainActivity"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
