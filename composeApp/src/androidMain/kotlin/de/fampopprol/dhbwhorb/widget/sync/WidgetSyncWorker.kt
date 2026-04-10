// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.sync

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.fampopprol.dhbwhorb.data.storage.database.createRoomDatabase
import de.fampopprol.dhbwhorb.data.storage.database.getDatabaseBuilder
import de.fampopprol.dhbwhorb.services.widget.DatabaseWidgetRepository
import de.fampopprol.dhbwhorb.services.widget.WidgetServiceLocator
import de.fampopprol.dhbwhorb.services.widget.WidgetTimetableUseCase
import de.fampopprol.dhbwhorb.widget.TimetableGlanceWidget
import de.fampopprol.dhbwhorb.widget.state.TimetableWidgetState
import de.fampopprol.dhbwhorb.widget.state.WidgetStateCodec
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * WorkManager-backed worker that fetches all widget states from [WidgetServiceLocator],
 * stores them via [WidgetStateCodec], then triggers a Glance UI refresh.
 *
 * If the app process was cold-started solely for this worker (i.e. no Activity has run),
 * [WidgetServiceLocator] may not be initialised yet. In that case the worker bootstraps
 * a DB-only [WidgetTimetableUseCase] itself before proceeding.
 */
class WidgetSyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WidgetSyncWorker"
        private const val WORK_NAME_PERIODIC = "widget_sync_periodic"
        private const val WORK_NAME_ONE_TIME  = "widget_sync_immediate"
        /**
         * WorkManager enforces a minimum of 15 minutes for periodic work (Android API constraint).
         * Widget sync interval set to 30 minutes as a reasonable balance:
         * - Long enough to minimize battery impact
         * - Short enough to keep widgets reasonably fresh
         * - Can be easily adjusted here for testing without code hunt
         *
         * NOT user-facing in Settings (v3.0) — this constant allows testers to verify behavior
         * at different intervals by changing one line and rebuilding.
         *
         * Rationale: Widget updates are less critical than immediate data; 30-minute interval
         * provides good responsiveness without excessive battery drain. Users with no widgets
         * won't trigger this work at all (see smart scheduling in MainActivity.initializeServicesAsync).
         */
        private const val REPEAT_INTERVAL_MINUTES = 30L

        /** Enqueue a periodic background sync (every 30 min, network required). */
        fun schedulePeriodicSync(context: Context) {
            // Smart scheduling: only schedule if user has active widgets
            // Rationale: Battery efficiency; users without widgets never trigger background work
            val widgetManager = GlanceAppWidgetManager(context)
            val hasActiveWidgets = try {
                val widgetIds = runBlocking {
                    widgetManager.getGlanceIds(TimetableGlanceWidget::class.java)
                }
                widgetIds.isNotEmpty()
            } catch (e: Exception) {
                Napier.w("Failed to check active widgets: ${e.message}", tag = TAG)
                // On error, assume widgets may exist — err on the side of scheduling
                true
            }

            if (!hasActiveWidgets) {
                Napier.d("No active widgets detected — skipping periodic sync scheduling", tag = TAG)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
                REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Napier.d("✓ Periodic widget sync scheduled every $REPEAT_INTERVAL_MINUTES min (active widgets detected)", tag = TAG)
        }

        /** Enqueue an immediate one-time sync (e.g. from onUpdate). */
        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            Napier.d("Immediate widget sync enqueued", tag = TAG)
        }
    }

    override suspend fun doWork(): Result {
        Napier.d("Widget sync worker started", tag = TAG)

        // Bootstrap the ServiceLocator if the app process was cold-started for this worker.
        if (!WidgetServiceLocator.isInitialized()) {
            Napier.w("WidgetServiceLocator not initialised – bootstrapping DB-only use case", tag = TAG)
            try {
                val db = createRoomDatabase(getDatabaseBuilder(context))
                WidgetServiceLocator.initialize(
                    WidgetTimetableUseCase(repository = DatabaseWidgetRepository(db.lectureDao()))
                )
            } catch (e: Exception) {
                Napier.e("Failed to bootstrap WidgetServiceLocator: ${e.message}", e, tag = TAG)
                // Retry later – WorkManager will back off automatically.
                return Result.retry()
            }
        }

        return try {
            val useCase  = WidgetServiceLocator.getUseCase()
            val upNext   = useCase.getUpNextState()
            val day0     = useCase.getDaySummaryState()
            val multiDay = useCase.getMultiDaySummaryState()
            val day1     = multiDay.getOrNull(1)

            val successState = TimetableWidgetState.Success(
                upNext = upNext,
                day0   = day0,
                day1   = day1,
            )

            pushStateToWidgets(successState)
            Napier.d("Widget state updated successfully", tag = TAG)
            Result.success()

        } catch (e: Exception) {
            Napier.e("Widget sync failed: ${e.message}", e, tag = TAG)
            pushStateToWidgets(TimetableWidgetState.Error(e.message ?: "Sync-Fehler"))
            Result.retry()
        }
    }

    /** Writes [state] into every active Glance widget instance and triggers recomposition. */
    private suspend fun pushStateToWidgets(state: TimetableWidgetState) {
        try {
            val manager   = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TimetableGlanceWidget::class.java)

            for (id in glanceIds) {
                updateAppWidgetState(context, id) { prefs: MutablePreferences ->
                    WidgetStateCodec.encode(prefs, state)
                }
            }
            TimetableGlanceWidget().updateAll(context)
        } catch (e: Exception) {
            Napier.e("Failed to push state to widgets: ${e.message}", e, tag = TAG)
        }
    }
}
