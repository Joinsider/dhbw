// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import io.github.aakira.napier.Napier
import kotlin.concurrent.Volatile

/**
 * Simple service locator for [WidgetTimetableUseCase].
 * Mirrors [de.fampopprol.dhbwhorb.services.notifications.NotificationServiceLocator]
 * and provides application-wide access from background workers.
 */
object WidgetServiceLocator {

    private const val TAG = "WidgetServiceLocator"

    @Volatile
    private var useCase: WidgetTimetableUseCase? = null

    /** Initialize with a fully constructed [WidgetTimetableUseCase]. Call once at app start. */
    fun initialize(instance: WidgetTimetableUseCase) {
        if (useCase != null) {
            Napier.w("WidgetTimetableUseCase already initialized, overwriting", tag = TAG)
        }
        useCase = instance
        Napier.d("WidgetTimetableUseCase initialized in WidgetServiceLocator", tag = TAG)
    }

    /** Returns the registered instance, or throws [IllegalStateException] if not initialized. */
    fun getUseCase(): WidgetTimetableUseCase =
        useCase ?: throw IllegalStateException(
            "WidgetTimetableUseCase not initialized. Call WidgetServiceLocator.initialize() first."
        )

    /** Returns `true` when [initialize] has been called. */
    fun isInitialized(): Boolean = useCase != null

    /** Clears the stored instance. Primarily for testing. */
    fun clear() {
        useCase = null
        Napier.d("WidgetTimetableUseCase cleared from WidgetServiceLocator", tag = TAG)
    }
}

