/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.Outcome
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * iOS background monitoring, on `BGTaskScheduler`.
 *
 * Until P8 this class logged "must be implemented in Swift" and did nothing, so the lecture-alert
 * switch in Settings promised something that never happened. `BGTaskScheduler` reaches Kotlin
 * through the platform libraries like any other iOS framework, so no Swift is needed beyond the
 * one call to [registerTaskHandler] — Apple requires registration to happen before the app
 * finishes launching, and only Swift knows when that is.
 *
 * The shape mirrors the Android scheduler: [schedule] asks the system for a run, [cancel] takes
 * the request back, and the work itself is one call to [NotificationManager.checkAndNotify].
 * How often it actually runs is iOS's decision, not ours — [EARLIEST_BEGIN_SECONDS] is a floor,
 * never a promise, and the system weighs it against how often the user opens the app.
 *
 * One request at a time: `BGAppRefreshTaskRequest` is not periodic, so the handler submits the
 * next one before doing its work. Submitting first also means a crash in the check does not end
 * the chain.
 */
@OptIn(ExperimentalForeignApi::class)
class LectureMonitorScheduler(private val scope: CoroutineScope) : KoinComponent {

    companion object {
        private const val TAG = "LectureMonitorScheduler"

        /** Must match `BGTaskSchedulerPermittedIdentifiers` in the app's Info.plist. */
        const val TASK_IDENTIFIER = "de.fampopprol.dhbwhorb.lecture-monitor"

        /**
         * The earliest the system may run the task — the same hour Android and Desktop use.
         *
         * A floor, not a schedule: iOS decides the real cadence from how the app is used, and it
         * is usually slower than this. Raising the floor therefore costs little here and saves
         * wake-ups on the two platforms that do honour it.
         */
        private const val EARLIEST_BEGIN_SECONDS = 60.0 * 60
    }

    private var handlerRegistered = false

    /**
     * Registers the launch handler. Call once, from the app's initialiser.
     *
     * iOS throws if an identifier is registered twice, and it refuses registrations that arrive
     * after launch has finished — both are why this is separate from [schedule], which the
     * settings screen calls whenever the switches change.
     */
    fun registerTaskHandler() {
        if (handlerRegistered) {
            Napier.d("Launch handler already registered", tag = TAG)
            return
        }
        handlerRegistered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = TASK_IDENTIFIER,
            usingQueue = null,
        ) { task -> if (task != null) runCheck(task) }

        if (handlerRegistered) {
            Napier.d("Registered background task $TASK_IDENTIFIER", tag = TAG)
        } else {
            // Almost always a missing BGTaskSchedulerPermittedIdentifiers entry.
            Napier.e("iOS refused the registration for $TASK_IDENTIFIER", tag = TAG)
        }
    }

    /** Asks iOS for a background run. Safe to call repeatedly; a resubmit replaces the request. */
    fun schedule() {
        if (!handlerRegistered) {
            // Submitting an unregistered identifier throws an NSInternalInconsistencyException,
            // which on Kotlin/Native terminates the process rather than surfacing as an exception.
            Napier.w("Not scheduling $TASK_IDENTIFIER: no launch handler registered", tag = TAG)
            return
        }
        submitRequest()
    }

    /** Withdraws the pending request. The handler stays registered for the rest of the launch. */
    fun cancel() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
        Napier.d("Cancelled background task $TASK_IDENTIFIER", tag = TAG)
    }

    private fun submitRequest() {
        val request = BGAppRefreshTaskRequest(TASK_IDENTIFIER).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(EARLIEST_BEGIN_SECONDS)
        }
        // The overload that reports into an NSError** is not exposed to Kotlin; a failure here is
        // logged by the system and leaves the app running, which is the same outcome either way.
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        Napier.d("Submitted $TASK_IDENTIFIER, earliest in ${EARLIEST_BEGIN_SECONDS.toInt()}s", tag = TAG)
    }

    /**
     * Runs one check inside the window iOS granted.
     *
     * The next request goes out first (see the class comment), and the expiration handler exists
     * because a task that overruns without calling `setTaskCompleted` counts against the app's
     * background budget — the checks would then get rarer and rarer.
     */
    private fun runCheck(task: BGTask) {
        submitRequest()

        val job = scope.launch {
            val manager = getKoin().getOrNull<NotificationManager>()
            val success = if (manager == null) {
                Napier.w("NotificationManager unavailable, skipping background check", tag = TAG)
                false
            } else {
                manager.checkAndNotify() is Outcome.Ok
            }
            Napier.d("Background check finished, success=$success", tag = TAG)
            task.setTaskCompletedWithSuccess(success)
        }

        task.expirationHandler = {
            Napier.w("iOS expired $TASK_IDENTIFIER before the check finished", tag = TAG)
            job.cancel()
            task.setTaskCompletedWithSuccess(false)
        }
    }
}
