/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared

import de.fampopprol.dhbwhorb.core.di.coreModule
import de.fampopprol.dhbwhorb.data.di.dataModule
import de.fampopprol.dhbwhorb.data.di.dataPlatformModule
import de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsInstallGuard
import de.fampopprol.dhbwhorb.services.di.servicesModule
import de.fampopprol.dhbwhorb.presentation.di.presentationModule
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
 * `presentationModule` is part of it since P4: the stores expose `StateFlow` and carry no Compose
 * runtime, so including them here keeps `Shared.framework` Compose-free while giving Swift the
 * same graph the Compose UI gets.
 *
 * @param extraModules what a platform contributes on top — on Android the Glance-based widget
 *   refresher, on iOS the widget writer. Tests pass overrides here.
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
        presentationModule,
        *extraModules.toTypedArray()
    )
}.also { application ->
    // Deleting an app takes its account with it. The Keychain does not play along by itself —
    // it outlives the app on purpose — so this is where credentials from a previous installation
    // are dropped. Here rather than in the four entry points: forgetting it in one of them is not
    // a compile error, and P2 is the story of what that costs.
    application.koin.get<CredentialsInstallGuard>().purgeIfReinstalled()
}
