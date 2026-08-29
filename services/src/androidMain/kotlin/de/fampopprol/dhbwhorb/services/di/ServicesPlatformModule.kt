/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.di

import de.fampopprol.dhbwhorb.services.notifications.AndroidNotificationDispatcher
import de.fampopprol.dhbwhorb.services.notifications.LectureMonitorScheduler
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import de.fampopprol.dhbwhorb.services.reminders.AndroidLectureReminderScheduler
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderScheduler
import de.fampopprol.dhbwhorb.services.session.AndroidCachedFileCleaner
import de.fampopprol.dhbwhorb.services.session.CachedFileCleaner
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun servicesPlatformModule(): Module = module {
    single<NotificationDispatcher> { AndroidNotificationDispatcher(androidContext()) }
    single { LectureMonitorScheduler(androidContext()) }
    single<LectureReminderScheduler> { AndroidLectureReminderScheduler(androidContext(), settings = get()) }

    // Only Android writes documents to a cache directory; see AndroidCachedFileCleaner.
    single<CachedFileCleaner> { AndroidCachedFileCleaner(androidContext()) }
}
