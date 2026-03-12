// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import de.fampopprol.dhbwhorb.services.widget.models.WidgetDayState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.notificationWithName

/**
 * iOS-spezifische Implementierung, die Widget-View-States in den gemeinsamen
 * App-Group-[NSUserDefaults]-Container schreibt, damit der [TimetableWidget]-
 * Extension-Prozess sie aus einem separaten Prozess lesen kann.
 *
 * Nach dem Schreiben wird eine Foundation-Notification gepostet, sodass
 * [ContentView.swift] `WidgetCenter.shared.reloadAllTimelines()` aufrufen kann.
 *
 * **Voraussetzung:** Die App-Group-Capability muss in beiden Xcode-Targets
 * (Haupt-App + Widget-Extension) aktiviert sein, damit `NSUserDefaults(suiteName:)`
 * nicht `nil` zurückgibt.
 *
 * @param appGroupSuiteName App-Group-Bezeichner, der in beiden Xcode-Targets
 *                          konfiguriert ist, z. B. `"group.de.fampopprol.dhbwhorb"`.
 */
@OptIn(ExperimentalForeignApi::class)
class WidgetDataWriter(private val appGroupSuiteName: String) {

    companion object {
        private const val TAG = "WidgetDataWriter"

        /**
         * NSNotification-Name, der nach dem Schreiben der Widget-Daten gepostet wird.
         * Wird in [ContentView.swift] beobachtet, um `WidgetCenter.reloadAllTimelines()`
         * auszulösen.
         */
        const val NOTIFICATION_WIDGET_DATA_UPDATED =
            "de.fampopprol.dhbwhorb.widgetDataUpdated"
    }

    private val defaults: NSUserDefaults = NSUserDefaults(suiteName = appGroupSuiteName)

    init {
        Napier.d("WidgetDataWriter bereit (suite=$appGroupSuiteName)", tag = TAG)
    }

    // ─── Schreiboperationen ───────────────────────────────────────────────────

    /**
     * Serialisiert und persistiert den „Up Next"-Widget-State.
     */
    fun writeUpNextState(state: WidgetUpNextState) {
        val json = WidgetDataSerializer.serializeUpNextState(state)
        defaults.setObject(json, forKey = WidgetDataSerializer.KEY_UP_NEXT)
        Napier.d("Up-Next-Daten geschrieben (Typ=${state::class.simpleName})", tag = TAG)
    }

    /**
     * Serialisiert und persistiert den „Multi Day"-Widget-State.
     */
    fun writeMultiDayState(days: List<WidgetDayState>) {
        val json = WidgetDataSerializer.serializeMultiDayState(days)
        defaults.setObject(json, forKey = WidgetDataSerializer.KEY_MULTI_DAY)
        Napier.d("Multi-Day-Daten geschrieben (${days.size} Tag(e))", tag = TAG)
    }

    /**
     * Postet eine Foundation-Notification, damit der Host-App-Code
     * `WidgetCenter.shared.reloadAllTimelines()` aufrufen kann.
     * Der Aufruf ist Thread-sicher (NSNotificationCenter ist thread-sicher).
     */
    fun notifyWidgetDataUpdated() {
        NSNotificationCenter.defaultCenter.postNotification(
            NSNotification.notificationWithName(
                aName = NOTIFICATION_WIDGET_DATA_UPDATED,
                `object` = null,
            ),
        )
        Napier.d("Widget-Data-Updated-Notification gepostet", tag = TAG)
    }
}

