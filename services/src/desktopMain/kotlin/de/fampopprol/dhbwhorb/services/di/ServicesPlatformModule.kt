/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.di

import de.fampopprol.dhbwhorb.services.notifications.DesktopNotificationDispatcher
import de.fampopprol.dhbwhorb.services.notifications.DesktopLectureMonitorScheduler
import de.fampopprol.dhbwhorb.services.notifications.BadgeDisplay
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderScheduler
import de.fampopprol.dhbwhorb.services.reminders.NoLectureReminderScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun servicesPlatformModule(): Module = module {
    // The platform dispatcher, wrapped with the badge in servicesModule; see BadgingNotificationDispatcher.
    single<NotificationDispatcher>(PlatformNotificationDispatcher) { DesktopNotificationDispatcher() }
    single<BadgeDisplay> { BadgeDisplay.None }
    single { DesktopLectureMonitorScheduler(scope = get()) }
    single<LectureReminderScheduler> { NoLectureReminderScheduler() }
}
