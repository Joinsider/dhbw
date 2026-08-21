/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import android.content.Context
import androidx.work.*
import androidx.work.WorkerParameters
import de.fampopprol.dhbwhorb.services.widget.WidgetRefresher
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import java.util.concurrent.TimeUnit

/**
 * Android WorkManager-based periodic scheduler for lecture change monitoring.
 * Runs every 2 hours with network and authentication constraints.
 */
class LectureMonitorScheduler(private val context: Context) {

    companion object {
        private const val TAG = "LectureMonitorScheduler"
        private const val WORK_NAME = "lecture_change_monitor"
        /**
         * WorkManager enforces a minimum of 15 minutes for periodic work (Android API constraint).
         * This value is locked at 15 minutes for lecture monitoring. This interval is less
         * battery-sensitive than widget sync and provides reasonable responsiveness for lecture
         * change notifications.
         *
         * Rationale: Lecture checks happen less frequently than widget updates; 15-minute
         * minimum is acceptable for this use case. Not user-configurable in v3.0.
         */
        private const val REPEAT_INTERVAL_MINUTES = 15L
    }

    /**
     * Schedule periodic lecture monitoring work.
     */
    fun schedule() {
        Napier.d("📱 Android Scheduler: Scheduling WorkManager job...", tag = TAG)

        // Use more permissive constraints that work even when device is locked
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        Napier.d("   ✓ Constraints: Network required (works in Doze maintenance windows)", tag = TAG)

        val workRequest = PeriodicWorkRequestBuilder<LectureMonitorWorker>(
            REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES) // Wait 1 minute after app start
            .build()
        Napier.d("   ✓ Work request created: every $REPEAT_INTERVAL_MINUTES minutes", tag = TAG)
        Napier.d("   ✓ Initial delay: 1 minute", tag = TAG)
        Napier.d("   ℹ️  Note: Job will run during Doze maintenance windows even when device is locked", tag = TAG)

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing work if already scheduled
            workRequest
        )

        Napier.d("✅ Lecture monitoring scheduled successfully", tag = TAG)
    }

    /**
     * Cancel scheduled lecture monitoring work.
     */
    fun cancel() {
        Napier.d("🛑 Android Scheduler: Cancelling WorkManager job...", tag = TAG)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Napier.d("✅ Lecture monitoring cancelled", tag = TAG)
    }

    /**
     * Get work status information.
     */
    fun getWorkInfo() = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)
}

/**
 * Worker that performs the actual lecture change monitoring.
 */
class LectureMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    companion object {
        private const val TAG = "LectureMonitorWorker"
    }

    override suspend fun doWork(): Result {
        Napier.d("╔════════════════════════════════════════════════════════════════════╗", tag = TAG)
        Napier.d("║  🔔 Background Worker: Starting lecture change monitoring work    ║", tag = TAG)
        Napier.d("╚════════════════════════════════════════════════════════════════════╝", tag = TAG)

        return try {
            // The worker is created by WorkManager, so it resolves its dependencies itself.
            val notificationManager = getKoin().getOrNull<NotificationManager>()
            if (notificationManager == null) {
                Napier.e("❌ NotificationManager not available, cannot perform background check", tag = TAG)
                Napier.d("╚════════════════════════════════════════════════════════════════════╝", tag = TAG)
                return Result.failure()
            }
            Napier.d("✅ NotificationManager retrieved successfully", tag = TAG)

            // Perform the monitoring check
            Napier.d("🚀 Calling notificationManager.checkAndNotify()...", tag = TAG)
            val success = notificationManager.checkAndNotify()

            if (!success) {
                Napier.w("⚠️  Check failed, scheduling retry", tag = TAG)
                Napier.d("╔════════════════════════════════════════════════════════════════════╗", tag = TAG)
                Napier.d("║  ⏭️  Background Worker: Retrying due to error                      ║", tag = TAG)
                Napier.d("╚════════════════════════════════════════════════════════════════════╝", tag = TAG)
                return Result.retry()
            }

            // ENHANCEMENT per D-01: Trigger immediate widget refresh on successful check
            Napier.d("✓ Check succeeded — triggering immediate widget sync to keep widgets fresh", tag = TAG)
            getKoin().getOrNull<WidgetRefresher>()?.requestRefresh()

            Napier.d("╔════════════════════════════════════════════════════════════════════╗", tag = TAG)
            Napier.d("║  ✅ Background Worker: Completed successfully                      ║", tag = TAG)
            Napier.d("╚════════════════════════════════════════════════════════════════════╝", tag = TAG)
            Result.success()

        } catch (e: Exception) {
            Napier.e("╔════════════════════════════════════════════════════════════════════╗", tag = TAG)
            Napier.e("║  ❌ Background Worker: ERROR                                       ║", tag = TAG)
            Napier.e("╚════════════════════════════════════════════════════════════════════╝", tag = TAG)
            Napier.e("Error during lecture change monitoring: ${e.message}", e, tag = TAG)
            Napier.e("Stack trace: ${e.stackTraceToString()}", tag = TAG)

            // Retry on transient errors (network, auth)
            val shouldRetry = e.message?.contains("network", ignoreCase = true) == true ||
                e.message?.contains("auth", ignoreCase = true) == true ||
                e.message?.contains("connection", ignoreCase = true) == true

            if (shouldRetry) {
                Napier.d("⏭️  Transient error detected, scheduling retry", tag = TAG)
                Result.retry()
            } else {
                Napier.e("❌ Permanent failure, not retrying", tag = TAG)
                Result.failure()
            }
        }
    }
}

