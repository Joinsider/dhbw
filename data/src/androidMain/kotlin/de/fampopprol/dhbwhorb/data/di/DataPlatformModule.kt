/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.di

import de.fampopprol.dhbwhorb.data.storage.credentials.AndroidSecureStorage
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.database.createRoomDatabase
import de.fampopprol.dhbwhorb.data.storage.database.getDatabaseBuilder
import de.fampopprol.dhbwhorb.data.storage.settings.AndroidPlatformSettings
import de.fampopprol.dhbwhorb.data.storage.settings.PlatformSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun dataPlatformModule(): Module = module {
    single<PlatformSettings> { AndroidPlatformSettings(androidContext()) }
    single<SecureStorageInterface> { AndroidSecureStorage(androidContext()) }
    single<AppDatabase> { createRoomDatabase(getDatabaseBuilder(androidContext())) }
}
