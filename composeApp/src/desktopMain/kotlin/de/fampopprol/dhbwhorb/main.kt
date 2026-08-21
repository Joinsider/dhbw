/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.presentation.di.presentationModule
import de.fampopprol.dhbwhorb.services.notifications.LectureMonitorScheduler
import de.fampopprol.dhbwhorb.shared.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

private const val TAG = "Main"

/**
 * Desktop entry point. Same object graph as Android and iOS, assembled by the same [initKoin].
 */
fun main() {
    Napier.base(DebugAntilog())

    val koin = initKoin(extraModules = listOf(presentationModule)).koin

    val notificationPreferences: NotificationPreferencesInteractor = koin.get()
    val lectureMonitorScheduler: LectureMonitorScheduler = koin.get()
    val appScope: CoroutineScope = koin.get()

    // Background monitoring runs only while both toggles are on.
    appScope.launch {
        combine(
            notificationPreferences.notificationsEnabled,
            notificationPreferences.lectureAlertsEnabled
        ) { notificationsEnabled, lectureAlertsEnabled ->
            notificationsEnabled && lectureAlertsEnabled
        }.collect { shouldSchedule ->
            if (shouldSchedule) {
                Napier.d("Notifications enabled, scheduling lecture monitoring", tag = TAG)
                lectureMonitorScheduler.schedule()
            } else {
                Napier.d("Notifications disabled, cancelling lecture monitoring", tag = TAG)
                lectureMonitorScheduler.cancel()
            }
        }
    }

    application {
        Window(
            onCloseRequest = {
                lectureMonitorScheduler.cancel()
                GlobalContext.stopKoin()
                exitApplication()
            },
            title = "dhbw",
        ) {
            App()
        }
    }
}
