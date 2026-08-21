/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.services.widget.WidgetDataWriter
import de.fampopprol.dhbwhorb.services.widget.WidgetTimetableUseCase
import de.fampopprol.dhbwhorb.shared.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.Koin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

private const val TAG = "MainViewController"
private const val APP_GROUP = "group.de.fampopprol.dhbwhorb"

/**
 * Called from Swift.
 *
 * Koin has to be started **before** the controller composes anything: `App()` resolves its
 * dependencies during composition, while a `LaunchedEffect` only runs afterwards. Starting it
 * there crashes on the first frame with "KoinApplication has not been started".
 *
 * This mirrors the other platforms, where `Application.onCreate()` and `main()` both run before
 * the first frame too.
 */
fun MainViewController(): UIViewController {
    val koin = startKoinIfNeeded()

    return ComposeUIViewController {
        LaunchedEffect(Unit) {
            observeDatabaseForWidget(koin)
        }
        App()
    }
}

/**
 * Idempotent: Swift may create the hosting controller more than once, and starting Koin twice
 * throws.
 */
private fun startKoinIfNeeded(): Koin =
    KoinPlatform.getKoinOrNull() ?: run {
        Napier.base(DebugAntilog())
        Napier.d("Starting dependency graph", tag = TAG)
        initKoin(extraModules = listOf(iosWidgetModule)).koin
    }

/**
 * Keeps the App Group snapshot in step with the database, so the widget extension can render
 * without a session or a network call.
 */
private suspend fun observeDatabaseForWidget(koin: Koin) {
    val database: AppDatabase = koin.get()
    val widgetWriter: WidgetDataWriter = koin.get()
    val widgetUseCase: WidgetTimetableUseCase = koin.get()

    database.lectureDao().getAllFlow().collectLatest {
        try {
            widgetWriter.writeUpNextState(widgetUseCase.getUpNextState())
            widgetWriter.writeMultiDayState(widgetUseCase.getMultiDaySummaryState())
            widgetWriter.notifyWidgetDataUpdated()
        } catch (e: Exception) {
            Napier.e("Widget snapshot failed: ${e.message}", e, tag = TAG)
        }
    }
}

private val iosWidgetModule = module {
    single { WidgetDataWriter(appGroupSuiteName = APP_GROUP) }
}
