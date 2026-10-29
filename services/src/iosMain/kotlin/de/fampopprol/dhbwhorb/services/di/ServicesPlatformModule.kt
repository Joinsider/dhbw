/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.di

import de.fampopprol.dhbwhorb.services.notifications.IosNotificationDispatcher
import de.fampopprol.dhbwhorb.services.notifications.LectureMonitorScheduler
import de.fampopprol.dhbwhorb.services.notifications.AppleBadgeDisplay
import de.fampopprol.dhbwhorb.services.notifications.BadgeDisplay
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.services.reminders.AppleLectureReminderScheduler
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderScheduler
import de.fampopprol.dhbwhorb.services.widget.IosWidgetRefresher
import de.fampopprol.dhbwhorb.services.widget.WidgetRefresher
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun servicesPlatformModule(): Module = module {
    // The platform dispatcher, wrapped with the badge in servicesModule; see BadgingNotificationDispatcher.
    single<NotificationDispatcher>(PlatformNotificationDispatcher) { IosNotificationDispatcher() }
    single<BadgeDisplay> { AppleBadgeDisplay() }
    single { LectureMonitorScheduler(scope = get()) }

    // Bound as both types: the background check resolves the interface, and SharedApp needs the
    // concrete one to hand Swift's WidgetCenter call down into it.
    single { IosWidgetRefresher() }
    single<WidgetRefresher> { get<IosWidgetRefresher>() }
    single<LectureReminderScheduler> { AppleLectureReminderScheduler() }
}
