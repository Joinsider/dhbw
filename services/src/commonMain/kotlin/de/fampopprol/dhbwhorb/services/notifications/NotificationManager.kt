/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.services.widget.WidgetRefresher
import io.github.aakira.napier.Napier

/**
 * One background run: check the preferences, check for changes, tell the user, refresh the widget.
 *
 * The three schedulers call nothing else, so this is where "what a run does" is defined once
 * rather than three times.
 *
 * @param widgetRefresher optional on purpose — a platform without widgets binds nothing, and a
 *   widget that does not refresh must never fail the run.
 */
class NotificationManager(
    private val monitor: LectureChangeMonitor,
    private val dispatcher: NotificationDispatcher,
    private val preferences: NotificationPreferencesInteractor,
    private val widgetRefresher: WidgetRefresher? = null,
) {
    companion object {
        private const val TAG = "NotificationManager"
    }

    /**
     * Runs one check.
     *
     * Returns [Outcome.Ok] both when something was found and when there was nothing to do —
     * including when the user has notifications switched off, which is not a failure. An
     * [Outcome.Err] carries the reason, so a caller can decide whether retrying sooner is worth it.
     */
    suspend fun checkAndNotify(): Outcome<Unit> {
        if (!preferences.shouldProcessLectureAlerts()) {
            Napier.d("Lecture alerts are off, skipping the check", tag = TAG)
            return Outcome.Ok(Unit)
        }

        if (!dispatcher.hasPermission()) {
            Napier.w("No notification permission, skipping the check", tag = TAG)
            return Outcome.Ok(Unit)
        }

        return when (val result = monitor.checkForChanges()) {
            is MonitorResult.Changes -> {
                notify(result.changes)
                // The timetable in the shared database moved, and the widget reads that database.
                widgetRefresher?.requestRefresh()
                Outcome.Ok(Unit)
            }

            is MonitorResult.NoChanges -> {
                Napier.d("No changes (${result.lecturesChecked} lecture(s) compared)", tag = TAG)
                Outcome.Ok(Unit)
            }

            is MonitorResult.Error -> {
                Napier.e("Lecture check failed: ${result.error}", tag = TAG)
                Outcome.Err(result.error)
            }
        }
    }

    /**
     * One change is worth spelling out; several are not — six notifications for one timetable
     * update is how an app gets its notifications switched off.
     */
    private suspend fun notify(changes: List<LectureChange>) {
        if (changes.size == 1) {
            val change = changes.first()
            val (title, message) = LectureChangeMessages.single(change)
            Napier.d("Notifying: $title", tag = TAG)
            dispatcher.showNotification(title, message, change.notificationKey)
        } else {
            val (title, message) = LectureChangeMessages.summary(changes)
            Napier.d("Notifying about ${changes.size} changes: $message", tag = TAG)
            dispatcher.showSummaryNotification(title, message, changes.size)
        }
    }
}
