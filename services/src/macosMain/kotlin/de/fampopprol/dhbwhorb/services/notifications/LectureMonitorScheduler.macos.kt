/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.Outcome
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

/**
 * macOS coroutine-based periodic scheduler for lecture change monitoring.
 * Runs hourly, in step with the other platforms, and only while the app is open.
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
            Napier.d("Already running, not starting a second loop", tag = TAG)
            return
        }

        monitorJob = scope.launch {
            Napier.d("Monitoring started, every $REPEAT_INTERVAL", tag = TAG)

            while (isActive) {
                try {
                    val notificationManager = getKoin().getOrNull<NotificationManager>()
                    if (notificationManager != null) {
                        if (notificationManager.checkAndNotify() is Outcome.Ok) {
                            Napier.d("Check done, next one in $REPEAT_INTERVAL", tag = TAG)
                        } else {
                            Napier.w("Check failed, retrying on the next interval", tag = TAG)
                        }
                    } else {
                        Napier.w("NotificationManager not in the graph, skipping the check", tag = TAG)
                    }

                } catch (e: CancellationException) {
                    Napier.d("Cancelled", tag = TAG)
                    throw e // Re-throw to stop the loop
                } catch (e: Exception) {
                    Napier.e("Error during lecture monitoring: ${e.message}", e, tag = TAG)
                }

                delay(REPEAT_INTERVAL)
            }
        }

    }

    /**
     * Cancel scheduled lecture monitoring.
     */
    fun cancel() {
        monitorJob?.cancel()
        monitorJob = null
        Napier.d("Monitoring cancelled", tag = TAG)
    }

    /**
     * Check if monitoring is currently active.
     */
    fun isScheduled(): Boolean = monitorJob?.isActive == true
}

