/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

/**
 * Coroutine-based periodic monitoring for lecture changes.
 *
 * Runs hourly, in step with Android and iOS. Only while the app is open — the desktop build has no
 * background service, so closing the window ends the monitoring with it.
 */
class LectureMonitorScheduler(private val scope: CoroutineScope) : KoinComponent {

    companion object {
        private const val TAG = "LectureMonitorScheduler"
        private val REPEAT_INTERVAL = 60.minutes
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
                    val notificationManager = getKoin().getOrNull<NotificationManager>()
                    if (notificationManager != null) {
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

