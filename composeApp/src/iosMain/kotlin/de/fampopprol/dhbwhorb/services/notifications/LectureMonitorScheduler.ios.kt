/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import io.github.aakira.napier.Napier

/**
 * iOS background task scheduler for lecture change monitoring.
 *
 * Note: Actual BGTaskScheduler implementation requires Swift/Objective-C bridging.
 * This Kotlin class provides the interface, but the actual scheduling must be
 * configured in Info.plist and implemented in Swift code.
 *
 * Required Info.plist entries:
 * <key>BGTaskSchedulerPermittedIdentifiers</key>
 * <array>
 *     <string>de.joinside.dhbw.lecture-monitor</string>
 * </array>
 */
class LectureMonitorScheduler {

    companion object {
        private const val TAG = "LectureMonitorScheduler"
        const val TASK_IDENTIFIER = "de.joinside.dhbw.lecture-monitor"
    }

    /**
     * Schedule background task for lecture monitoring.
     * The actual implementation needs to be done in Swift/Objective-C:
     *
     * ```swift
     * import BackgroundTasks
     *
     * BGTaskScheduler.shared.register(
     *     forTaskWithIdentifier: "de.joinside.dhbw.lecture-monitor",
     *     using: nil
     * ) { task in
     *     // Perform monitoring work
     *     task.setTaskCompleted(success: true)
     * }
     *
     * let request = BGAppRefreshTaskRequest(identifier: "de.joinside.dhbw.lecture-monitor")
     * request.earliestBeginDate = Date(timeIntervalSinceNow: 5 * 60) // 5 minutes (for testing)
     * try? BGTaskScheduler.shared.submit(request)
     * ```
     */
    fun schedule() {
        Napier.d("iOS background task scheduling - must be implemented in Swift/Objective-C", tag = TAG)
        // Actual scheduling happens in native iOS code
    }

    /**
     * Cancel scheduled background task.
     */
    fun cancel() {
        Napier.d("iOS background task cancellation - must be implemented in Swift/Objective-C", tag = TAG)
        // Actual cancellation happens in native iOS code
    }
}

