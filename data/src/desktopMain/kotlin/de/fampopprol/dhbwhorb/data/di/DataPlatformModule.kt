/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.di

import de.fampopprol.dhbwhorb.data.storage.credentials.DesktopSecureStorage
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.data.storage.database.createRoomDatabase
import de.fampopprol.dhbwhorb.data.storage.database.getDatabaseBuilder
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun dataPlatformModule(): Module = module {
    single<SecureStorageInterface> { DesktopSecureStorage() }
    single<AppDatabase> { createRoomDatabase(getDatabaseBuilder()) }
}
