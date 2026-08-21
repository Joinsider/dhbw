/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared

import de.fampopprol.dhbwhorb.core.di.coreModule
import de.fampopprol.dhbwhorb.data.di.dataModule
import de.fampopprol.dhbwhorb.data.di.dataPlatformModule
import de.fampopprol.dhbwhorb.services.di.servicesModule
import de.fampopprol.dhbwhorb.services.di.servicesPlatformModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * The one place the object graph is assembled.
 *
 * Before this existed, `MainActivity`, `desktopMain/main.kt` and `MainViewController` each wired
 * the same services by hand — and had drifted apart, which is why iOS ran without an HTTP timeout
 * and without a registered notification manager. Every entry point now calls this instead.
 *
 * Deliberately assembles only core, data and services. `presentationModule` is contributed by the
 * caller instead: depending on it here would link the Compose runtime into `Shared.framework` and
 * undo the Compose-free property. From P4 the stores are Compose-free and can move in.
 *
 * @param extraModules what the entry point contributes — `presentationModule`, and on Android the
 *   Glance-based widget refresher. Tests pass overrides here.
 */
fun initKoin(
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinApplication.() -> Unit = {}
) = startKoin {
    appDeclaration()
    modules(
        coreModule,
        dataModule,
        dataPlatformModule(),
        servicesModule,
        servicesPlatformModule(),
        *extraModules.toTypedArray()
    )
}
