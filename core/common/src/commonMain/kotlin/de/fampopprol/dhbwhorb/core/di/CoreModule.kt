/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.core.di

import de.fampopprol.dhbwhorb.core.appCoroutineScope
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val coreModule = module {
    single<CoroutineScope> { appCoroutineScope() }
}
