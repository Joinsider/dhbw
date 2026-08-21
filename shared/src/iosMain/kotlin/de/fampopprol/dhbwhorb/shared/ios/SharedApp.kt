/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.shared.ios

import de.fampopprol.dhbwhorb.data.storage.database.AppDatabase
import de.fampopprol.dhbwhorb.presentation.app.AppStore
import de.fampopprol.dhbwhorb.presentation.auth.AuthStore
import de.fampopprol.dhbwhorb.presentation.documents.DocumentsStore
import de.fampopprol.dhbwhorb.presentation.grades.GradesStore
import de.fampopprol.dhbwhorb.presentation.settings.SettingsStore
import de.fampopprol.dhbwhorb.presentation.timetable.TimetableStore
import de.fampopprol.dhbwhorb.services.widget.WidgetDataWriter
import de.fampopprol.dhbwhorb.services.widget.WidgetTimetableUseCase
import de.fampopprol.dhbwhorb.shared.initKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.Koin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

private const val TAG = "SharedApp"
private const val APP_GROUP = "group.de.fampopprol.dhbwhorb"

/**
 * Everything the SwiftUI app needs from Kotlin, behind one door.
 *
 * This is what `MainViewController` used to be — minus Compose. The Swift side asks for
 * [SharedApp.start] once and then reads the six bridges; it never touches Koin, because a
 * `KoinPlatform.getKoin().get()` from Swift resolves by Objective-C class and silently returns
 * the wrong binding when two share a supertype.
 *
 * **On SKIE:** the plan called for it, and it is not here. SKIE ships a compiler plugin built
 * against an exact Kotlin version, and its newest release (0.10.4) stops at Kotlin 2.2.0 while
 * this project is on 2.3.20 — the alternative would have been pinning Kotlin back, which
 * Compose 1.10.3 does not allow. So the two things SKIE would have given us are done by hand:
 * flows are collected in [FlowObserver] instead of bridged to `AsyncSequence`, and sealed
 * hierarchies stay Objective-C classes that Swift matches with `is` instead of becoming Swift
 * enums. Worth revisiting when SKIE catches up.
 */
object SharedApp {

    private val scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()

    /**
     * Starts the object graph if it is not running, and returns the accessor for it.
     *
     * Idempotent: SwiftUI may build the root view more than once (a scene reconnecting, a
     * preview), and starting Koin twice throws.
     */
    fun start(): SharedApp {
        val koin = KoinPlatform.getKoinOrNull() ?: run {
            Napier.base(DebugAntilog())
            Napier.d("Starting dependency graph", tag = TAG)
            initKoin(extraModules = listOf(iosWidgetModule)).koin.also { started ->
                scope.launch { observeDatabaseForWidget(started) }
            }
        }
        this.koin = koin
        return this
    }

    private lateinit var koin: Koin

    val app: AppStoreBridge by lazy { AppStoreBridge(koin.get<AppStore>()) }
    val auth: AuthStoreBridge by lazy { AuthStoreBridge(koin.get<AuthStore>()) }
    val timetable: TimetableStoreBridge by lazy { TimetableStoreBridge(koin.get<TimetableStore>()) }
    val grades: GradesStoreBridge by lazy { GradesStoreBridge(koin.get<GradesStore>()) }
    val documents: DocumentsStoreBridge by lazy { DocumentsStoreBridge(koin.get<DocumentsStore>()) }
    val settings: SettingsStoreBridge by lazy { SettingsStoreBridge(koin.get<SettingsStore>()) }
}

/**
 * Keeps the App Group snapshot in step with the database, so the widget extension can render
 * without a session or a network call.
 *
 * Moved here from `MainViewController` unchanged. P8 replaces it: since P6 the database itself
 * lives in the App Group container, so the widget can read it directly and this JSON detour
 * disappears together with [WidgetDataWriter].
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
