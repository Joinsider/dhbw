/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import android.content.Context
import androidx.work.*
import androidx.work.WorkerParameters
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.core.error.isTransient
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based periodic monitoring for lecture changes.
 *
 * Runs hourly, which is a deliberate step down from the 15 minutes it used to ask for. What costs
 * battery on Android is the wake-up, not the work: four times fewer of them, and a timetable
 * change that arrives up to an hour later than it might have — which is well inside the time it
 * takes anyone to act on one.
 */
class LectureMonitorScheduler(private val context: Context) {

    companion object {
        private const val TAG = "LectureMonitorScheduler"
        private const val WORK_NAME = "lecture_change_monitor"
        /**
         * How often to wake up. WorkManager's floor is 15 minutes; this asks for four times that.
         *
         * Kept in step with the desktop scheduler and the iOS background task by hand — the three
         * are separate implementations of the same intent, and an interval that drifts apart on
         * one platform is invisible until someone compares battery figures.
         */
        private const val REPEAT_INTERVAL_MINUTES = 60L
    }

    /**
     * Schedule periodic lecture monitoring work.
     */
    fun schedule() {
        // Network is the only constraint, so the job still runs in Doze maintenance windows and
        // on a locked device.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<LectureMonitorWorker>(
            REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES) // Wait 1 minute after app start
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // UPDATE, not KEEP: with KEEP an installation that already has the job keeps whatever
            // interval it was enqueued with, so changing the constant above would have moved
            // nothing for existing users and only shown up on fresh installs.
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Napier.d("Scheduled: every $REPEAT_INTERVAL_MINUTES minutes, first run in 1 minute", tag = TAG)
    }

    /**
     * Cancel scheduled lecture monitoring work.
     */
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Napier.d("Monitoring cancelled", tag = TAG)
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
        Napier.d("Background check starting", tag = TAG)

        return try {
            // The worker is created by WorkManager, so it resolves its dependencies itself.
            val notificationManager = getKoin().getOrNull<NotificationManager>()
            if (notificationManager == null) {
                Napier.e("NotificationManager not in the graph, cannot check", tag = TAG)
                return Result.failure()
            }

            when (val outcome = notificationManager.checkAndNotify()) {
                is Outcome.Ok -> {
                    Napier.d("Background check done", tag = TAG)
                    Result.success()
                }

                is Outcome.Err -> {
                    // Whether a retry can help is a property of the error, not of the words in
                    // its message — which is how this used to be decided.
                    val error = outcome.error
                    if (error.isTransient) {
                        Napier.w("Transient failure ($error), retrying later", tag = TAG)
                        Result.retry()
                    } else {
                        Napier.e("Permanent failure ($error), not retrying", tag = TAG)
                        Result.failure()
                    }
                }
            }

        } catch (e: Exception) {
            // Anything that escaped the typed error channel above. Retrying costs one wake-up and
            // might catch a transient condition the classification missed.
            Napier.e("Error during lecture change monitoring: ${e.message}", e, tag = TAG)
            Result.retry()
        }
    }
}

