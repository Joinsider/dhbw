/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.services.notifications

import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

/**
 * Desktop coroutine-based periodic scheduler for lecture change monitoring.
 * Runs every 2 hours using coroutine delay in a background scope.
 */
class LectureMonitorScheduler(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "LectureMonitorScheduler"
        private val REPEAT_INTERVAL = 15.minutes // Changed to 5 minutes for testing
    }

    private var monitorJob: Job? = null

    /**
     * Start periodic lecture monitoring.
     */
    fun schedule() {
        if (monitorJob?.isActive == true) {
            Napier.d("⚠️  Lecture monitoring already running, not starting again", tag = TAG)
            return
        }

        Napier.d("🖥️  Desktop Scheduler: Starting coroutine-based monitoring...", tag = TAG)
        Napier.d("   ✓ Interval: $REPEAT_INTERVAL", tag = TAG)

        monitorJob = scope.launch {
            Napier.d("╔════════════════════════════════════════════════════════════════════╗", tag = TAG)
            Napier.d("║  🖥️  Desktop Scheduler: Starting (every $REPEAT_INTERVAL)         ║", tag = TAG)
            Napier.d("╚════════════════════════════════════════════════════════════════════╝", tag = TAG)

            while (isActive) {
                try {
                    Napier.d("⏰ Scheduler tick - checking for changes...", tag = TAG)

                    // Check if NotificationManager is initialized
                    if (NotificationServiceLocator.isInitialized()) {
                        val notificationManager = NotificationServiceLocator.getNotificationManager()
                        Napier.d("🚀 Calling notificationManager.checkAndNotify()...", tag = TAG)
                        val success = notificationManager.checkAndNotify()
                        if (success) {
                            Napier.d("✅ Check completed successfully, waiting $REPEAT_INTERVAL until next check", tag = TAG)
                        } else {
                            Napier.w("⚠️  Check failed, will retry on next interval", tag = TAG)
                        }
                    } else {
                        Napier.w("⚠️  NotificationManager not initialized, skipping check", tag = TAG)
                    }

                } catch (e: CancellationException) {
                    Napier.d("🛑 Scheduler cancelled", tag = TAG)
                    throw e // Re-throw to stop the loop
                } catch (e: Exception) {
                    Napier.e("❌ Error during lecture monitoring: ${e.message}", e, tag = TAG)
                }

                Napier.d("💤 Sleeping for $REPEAT_INTERVAL...", tag = TAG)
                delay(REPEAT_INTERVAL)
            }
        }

        Napier.d("✅ Lecture monitoring coroutine started", tag = TAG)
    }

    /**
     * Cancel scheduled lecture monitoring.
     */
    fun cancel() {
        Napier.d("🛑 Desktop Scheduler: Cancelling monitoring coroutine...", tag = TAG)
        monitorJob?.cancel()
        monitorJob = null
        Napier.d("✅ Lecture monitoring cancelled", tag = TAG)
    }

    /**
     * Check if monitoring is currently active.
     */
    fun isScheduled(): Boolean = monitorJob?.isActive == true
}

