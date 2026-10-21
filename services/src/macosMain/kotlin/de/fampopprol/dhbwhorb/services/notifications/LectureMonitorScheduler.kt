/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.Outcome
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import platform.Foundation.NSBackgroundActivityResultDeferred
import platform.Foundation.NSBackgroundActivityResultFinished
import platform.Foundation.NSBackgroundActivityScheduler
import platform.Foundation.NSQualityOfServiceUtility

/**
 * Hourly lecture-change checks on macOS, scheduled by the system through `NSBackgroundActivityScheduler`.
 *
 * Replaces the coroutine loop on this platform. The system decides the exact moment within
 * [TOLERANCE_SECONDS] of each hour, defers the run when the Mac is under load or on battery, and
 * does not fire during sleep — so the check costs far less than a loop that wakes on a timer.
 *
 * The activity lives in the app process, so it runs while the app runs. With background mode on
 * (a login item that starts the app without a window, see `BackgroundMode.swift`) that is the whole
 * time the user is logged in; without it, only while the app is open.
 *
 * Every run goes through [NotificationManager.checkAndNotify], which also replans the lecture
 * reminders first — the same path iOS takes, so the two platforms cannot drift apart on reminders.
 */
@OptIn(ExperimentalForeignApi::class)
class LectureMonitorScheduler(private val scope: CoroutineScope) : KoinComponent {

    companion object {
        private const val TAG = "LectureMonitorScheduler"

        /** Reverse-DNS identifier the system keys the activity by. Matches the iOS task identifier. */
        const val ACTIVITY_IDENTIFIER = "de.fampopprol.dhbwhorb.lecture-monitor"

        private const val INTERVAL_SECONDS = 60.0 * 60
        private const val TOLERANCE_SECONDS = 10.0 * 60
    }

    private var activity: NSBackgroundActivityScheduler? = null

    /**
     * Registers the launch handler. A no-op here.
     *
     * Exists so Swift can call the same name on every Apple platform. iOS needs a `BGTaskScheduler`
     * registration before launch finishes; macOS needs nothing of the kind — the activity is simply
     * created by [schedule].
     */
    fun registerTaskHandler() {
        Napier.d("No launch handler on macOS, the activity is created by schedule()", tag = TAG)
    }

    /**
     * Starts periodic monitoring, and runs one check right away as the old loop did.
     *
     * Calling it again while an activity exists does nothing, so the settings observer can call it
     * on every change without starting a second schedule.
     */
    fun schedule() {
        if (activity != null) {
            Napier.d("Already scheduled, not creating a second activity", tag = TAG)
            return
        }

        val scheduler = NSBackgroundActivityScheduler(ACTIVITY_IDENTIFIER).apply {
            repeats = true
            interval = INTERVAL_SECONDS
            tolerance = TOLERANCE_SECONDS
            qualityOfService = NSQualityOfServiceUtility
        }

        scheduler.scheduleWithBlock { completion ->
            // The system asks first when it would rather run us later (load, battery). Deferring
            // keeps the activity and asks again soon, without spending a check now.
            if (scheduler.shouldDefer) {
                Napier.d("macOS asked to defer the check", tag = TAG)
                completion?.invoke(NSBackgroundActivityResultDeferred)
                return@scheduleWithBlock
            }
            // The completion handler has to be called exactly once, or the system stops treating
            // the activity as finished — `finally` keeps that true when the check throws.
            scope.launch {
                try {
                    runMonitoringCheck()
                } finally {
                    completion?.invoke(NSBackgroundActivityResultFinished)
                }
            }
        }

        activity = scheduler
        Napier.d("Monitoring scheduled, every ${INTERVAL_SECONDS.toInt()}s with ${TOLERANCE_SECONDS.toInt()}s tolerance", tag = TAG)

        scope.launch { runMonitoringCheck() }
    }

    /** Stops the activity. The system will not run it again until [schedule] is called. */
    fun cancel() {
        activity?.invalidate()
        activity = null
        Napier.d("Monitoring cancelled", tag = TAG)
    }

    /** True while the activity exists, which is from [schedule] until [cancel]. */
    fun isScheduled(): Boolean = activity != null

    private suspend fun runMonitoringCheck() {
        try {
            val notificationManager = getKoin().getOrNull<NotificationManager>()
            if (notificationManager == null) {
                Napier.w("NotificationManager not in the graph, skipping the check", tag = TAG)
                return
            }
            when (notificationManager.checkAndNotify()) {
                is Outcome.Ok -> Napier.d("Check done, next one in about an hour", tag = TAG)
                is Outcome.Err -> Napier.w("Check failed, retrying on the next interval", tag = TAG)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Error during lecture monitoring: ${e.message}", e, tag = TAG)
        }
    }
}
