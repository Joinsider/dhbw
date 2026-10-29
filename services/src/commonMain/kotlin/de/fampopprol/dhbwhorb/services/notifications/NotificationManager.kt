/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.domain.model.LectureCheck
import de.fampopprol.dhbwhorb.domain.model.LectureCheckOutcome
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderPlanner
import de.fampopprol.dhbwhorb.services.widget.WidgetRefresher
import io.github.aakira.napier.Napier
import kotlin.time.Clock

/**
 * One background run: check the preferences, check for changes, tell the user, refresh the widget.
 *
 * The three schedulers call nothing else, so this is where "what a run does" is defined once
 * rather than three times.
 *
 * @param reminders replanned on every run — the timetable it plans from may have moved since the
 *   last one, whether this run finds a change or not.
 * @param widgetRefresher optional on purpose — a platform without widgets binds nothing, and a
 *   widget that does not refresh must never fail the run.
 * @param clock the time a run is recorded at, as epoch millis; injectable for tests.
 */
class NotificationManager(
    private val monitor: LectureChangeMonitor,
    private val dispatcher: NotificationDispatcher,
    private val preferences: NotificationPreferencesInteractor,
    private val reminders: LectureReminderPlanner,
    private val widgetRefresher: WidgetRefresher? = null,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    companion object {
        private const val TAG = "NotificationManager"
    }

    /**
     * Runs one check and records how it ended.
     *
     * Every platform's scheduler goes through here, so this is the one place the "last check"
     * shown in Settings and the menu bar is written. Only a run that actually compared the timetable
     * against Dualis is recorded, successful or not: a run skipped because alerts are off or the
     * permission is missing did no check, and showing it as one would mislead the user. A run that
     * is cancelled part-way is not recorded either, because it did not finish.
     *
     * Returns [Outcome.Ok] both when something was found and when there was nothing to do —
     * including when the user has notifications switched off, which is not a failure. An
     * [Outcome.Err] carries the reason, so a caller can decide whether retrying sooner is worth it.
     */
    suspend fun checkAndNotify(): Outcome<Unit> {
        // Before anything else, and regardless of the change-alert switch: the reminders are a
        // separate setting, and this hourly wake-up is the only thing that keeps them in step with
        // a timetable the user changed by pulling to refresh.
        reminders.reschedule()

        if (!preferences.shouldProcessLectureAlerts()) {
            Napier.d("Lecture alerts are off, skipping the check", tag = TAG)
            return Outcome.Ok(Unit)
        }

        if (!dispatcher.hasPermission()) {
            Napier.w("No notification permission, skipping the check", tag = TAG)
            return Outcome.Ok(Unit)
        }

        val outcome: Outcome<Unit> = when (val result = monitor.checkForChanges()) {
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
        recordLectureCheck(outcome)
        return outcome
    }

    private fun recordLectureCheck(outcome: Outcome<Unit>) {
        preferences.recordLectureCheck(
            LectureCheck(
                epochMillis = clock(),
                outcome = when (outcome) {
                    is Outcome.Ok -> LectureCheckOutcome.SUCCESS
                    is Outcome.Err -> if (outcome.error is AppError.Offline) LectureCheckOutcome.OFFLINE else LectureCheckOutcome.FAILED
                },
            )
        )
    }

    /**
     * Resets the app-icon count to zero. Called when the app comes to the foreground — the
     * timetable it opens shows the state the notifications described — and on logout.
     */
    suspend fun markChangesSeen() {
        preferences.resetUnseenLectureChanges()
        dispatcher.setBadge(0)
    }

    /**
     * One change is worth spelling out; several are not — six notifications for one timetable
     * update is how an app gets its notifications switched off.
     *
     * The app-icon count is the number of changes notified since the app was last opened, so it
     * grows by the size of every notification, a single one included.
     */
    private suspend fun notify(changes: List<LectureChange>) {
        if (changes.size == 1) {
            val change = changes.first()
            val (title, message) = LectureNotificationTexts.single(change)
            Napier.d("Notifying: $title", tag = TAG)
            dispatcher.showNotification(title, message, change.notificationKey)
        } else {
            val (title, message) = LectureNotificationTexts.summary(changes)
            Napier.d("Notifying about ${changes.size} changes: $message", tag = TAG)
            dispatcher.showSummaryNotification(title, message, changes.size)
        }
        dispatcher.setBadge(preferences.addUnseenLectureChanges(changes.size))
    }
}
