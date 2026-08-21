/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.di

import de.fampopprol.dhbwhorb.services.notifications.LectureMonitorScheduler
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun servicesPlatformModule(): Module = module {
    single { NotificationDispatcher() }
    single { LectureMonitorScheduler(scope = get()) }
}
