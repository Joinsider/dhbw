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
            Napier.d("Already running, not starting a second loop", tag = TAG)
            return
        }

        monitorJob = scope.launch {
            Napier.d("Monitoring started, every $REPEAT_INTERVAL", tag = TAG)

            while (isActive) {
                runMonitoringCheck()
                delay(REPEAT_INTERVAL)
            }
        }
    }

    private suspend fun runMonitoringCheck() {
        try {
            checkForLectureChanges()
        } catch (e: CancellationException) {
            Napier.d("Cancelled", tag = TAG)
            throw e // Re-throw to stop the loop
        } catch (e: Exception) {
            Napier.e("Error during lecture monitoring: ${e.message}", e, tag = TAG)
        }
    }

    private suspend fun checkForLectureChanges() {
        val notificationManager = getKoin().getOrNull<NotificationManager>()
        if (notificationManager == null) {
            Napier.w("NotificationManager not in the graph, skipping the check", tag = TAG)
            return
        }

        if (notificationManager.checkAndNotify() is Outcome.Ok) {
            Napier.d("Check done, next one in $REPEAT_INTERVAL", tag = TAG)
        } else {
            Napier.w("Check failed, retrying on the next interval", tag = TAG)
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

