/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.presentation.app.AppStore
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsStore
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableStore
import de.fampopprol.dhbwhorb.services.notifications.LectureMonitorScheduler
import de.fampopprol.dhbwhorb.services.notifications.LectureNotificationTexts
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderPlanner
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderScheduler
import de.fampopprol.dhbwhorb.services.reminders.TestReminder
import de.fampopprol.dhbwhorb.shared.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

private const val TAG = "SharedApp"

/**
 * Starts the object graph if it is not running yet, and returns it.
 *
 * Two processes call this: the app through [SharedApp.start], and the widget extension through
 * [WidgetSnapshotProvider]. They are separate processes with separate graphs — what they share is
 * the database file in the App Group container, not this object.
 *
 * Idempotent, because starting Koin twice throws and SwiftUI may build the root view more than
 * once (a scene reconnecting, a preview).
 */
internal fun startSharedKoin(): Koin = KoinPlatform.getKoinOrNull() ?: run {
    Napier.base(DebugAntilog())
    Napier.d("Starting dependency graph", tag = TAG)
    initKoin().koin
}

/**
 * Everything the SwiftUI app needs from Kotlin, behind one door.
 *
 * This is what `MainViewController` used to be — minus Compose. The Swift side asks for
 * [SharedApp.start] once and then reads the six bridges; it never touches Koin, because a
 * `KoinPlatform.getKoin().get()` from Swift resolves by Objective-C class and silently returns
 * the wrong binding when two share a supertype.
 *
 * **On SKIE:** the plan called for it, and it is not here. SKIE ships a compiler plugin built
 * against an exact Kotlin version, and its newest release (0.10.4) stops at Kotlin 2.2.0 while
 * this project is on 2.3.20 — the alternative would have been pinning Kotlin back, which
 * Compose 1.10.3 does not allow. So the two things SKIE would have given us are done by hand:
 * flows are collected in [FlowObserver] instead of bridged to `AsyncSequence`, and sealed
 * hierarchies stay Objective-C classes that Swift matches with `is` instead of becoming Swift
 * enums. Worth revisiting when SKIE catches up.
 */
object SharedApp {

    /** Starts the graph if needed and returns the accessor for it. */
    fun start(): SharedApp {
        koin = startSharedKoin()
        return this
    }

    private lateinit var koin: Koin
    private val observer = FlowObserver()
    private val scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()

    val app: AppStoreBridge by lazy { AppStoreBridge(koin.get<AppStore>()) }
    val auth: AuthStoreBridge by lazy { AuthStoreBridge(koin.get<AuthStore>()) }
    val timetable: TimetableStoreBridge by lazy { TimetableStoreBridge(koin.get<TimetableStore>()) }
    val grades: GradesStoreBridge by lazy { GradesStoreBridge(koin.get<GradesStore>()) }
    val documents: DocumentsStoreBridge by lazy { DocumentsStoreBridge(koin.get<DocumentsStore>()) }
    val settings: SettingsStoreBridge by lazy { SettingsStoreBridge(koin.get<SettingsStore>()) }

    /**
     * The background-refresh scheduler, for the two things only Swift can time: registering the
     * launch handler before the app finishes launching, and following the settings switches.
     *
     * `MainActivity` does the same on Android — the scheduler is a service, and deciding when it
     * runs belongs to the platform entry point.
     */
    val lectureMonitor: LectureMonitorScheduler by lazy { koin.get<LectureMonitorScheduler>() }

    /**
     * Replans the lecture reminders from the cached timetable.
     *
     * Swift calls this whenever the lead time changes, so a new choice takes effect immediately
     * instead of at the next hourly run. iOS then holds the reminders itself as pending
     * notification requests — nothing here has to be running for one to arrive.
     */
    fun rescheduleReminders(onDone: () -> Unit) {
        scope.launch {
            koin.get<LectureReminderPlanner>().reschedule()
            onDone()
        }
    }

    /**
     * Sends one notification through the real delivery path. Used by the debug-only test section.
     *
     * Permission is checked first, so a missing permission answers with `false` at once and
     * nothing is sent. [delaySeconds] of 0 sends now; otherwise this waits on the scope before
     * sending, which leaves time to switch to another app and watch the banner arrive.
     *
     * [onDone] runs on the main thread: the scope is `Dispatchers.Main`, and the dispatcher's
     * callbacks resume there.
     */
    fun sendTestNotification(delaySeconds: Int, onDone: (Boolean) -> Unit) {
        scope.launch {
            val dispatcher = koin.get<NotificationDispatcher>()
            if (!dispatcher.hasPermission()) {
                Napier.w("Test notification not sent: permission is not granted", tag = TAG)
                onDone(false)
                return@launch
            }
            if (delaySeconds > 0) delay(delaySeconds * 1_000L)

            val (title, message) = LectureNotificationTexts.testNotification()
            val now = TimeHelper.now()
            val key = "test_${now.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()}"
            dispatcher.showNotification(title, message, notificationKey = key)
            onDone(true)
        }
    }

    /**
     * Schedules one test reminder for the next whole minute, through the real reminder scheduler.
     *
     * Uses [LectureReminderScheduler.add], not a replan: the timetable's reminders stay exactly as
     * they are. A later replan removes the test reminder again, since it carries the reminder id
     * prefix. Like [sendTestNotification], [onDone] gets `false` at once when permission is missing.
     */
    fun sendTestReminder(onDone: (Boolean) -> Unit) {
        scope.launch {
            val granted = koin.get<NotificationDispatcher>().hasPermission()
            if (!granted) {
                Napier.w("Test reminder not scheduled: permission is not granted", tag = TAG)
                onDone(false)
                return@launch
            }
            val reminder = TestReminder.create(TimeHelper.now())
            koin.get<LectureReminderScheduler>().add(reminder)
            Napier.d("Test reminder ${reminder.id} fires at ${reminder.fireAt}", tag = TAG)
            onDone(true)
        }
    }

    /**
     * Installs the one thing Kotlin cannot do for itself on Apple platforms: reloading the widget.
     *
     * `WidgetCenter` is Swift-only, and the background check needs it — otherwise a lecture change
     * found while the app is closed updates the database and leaves the widget showing yesterday
     * until WidgetKit next feels like asking. Android has done this since before the rebuild.
     *
     * On macOS there is no widget extension, so the call is a no-op there (see
     * [installWidgetReload]).
     */
    fun setWidgetReload(reload: () -> Unit) {
        installWidgetReload(koin, reload)
    }

    /**
     * Calls [onChange] whenever the cached timetable changes, so Swift can reload the widget.
     *
     * The widget extension reads the same database file out of the App Group container, but it is
     * a separate process and nothing tells it that a fetch has landed — only `WidgetCenter` can,
     * and only from the app. What used to happen here instead was a whole JSON snapshot written
     * into `NSUserDefaults` on every change, with a Foundation notification behind it; the
     * snapshot is gone with P8 and this is what is left of it.
     */
    fun observeTimetableChanges(onChange: () -> Unit): ObservationHandle {
        val database: AppDatabase = koin.get()
        return observer.observe(database.lectureDao().getAllFlow()) { onChange() }
    }
}
