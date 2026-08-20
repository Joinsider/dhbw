// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import de.fampopprol.dhbwhorb.services.widget.models.WidgetDayState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import kotlinx.serialization.json.Json

/**
 * Serialisiert Widget-View-States mithilfe von `kotlinx.serialization` in JSON-Strings,
 * die in plattform-nativen Key-Value-Stores (z. B. NSUserDefaults auf iOS) abgelegt
 * und vom Widget-Extension-Prozess ohne das vollständige KMP-Runtime gelesen werden.
 */
object WidgetDataSerializer {

    /** NSUserDefaults-Schlüssel für den „Up Next"-Snapshot. */
    const val KEY_UP_NEXT = "widget_up_next"

    /** NSUserDefaults-Schlüssel für den „Multi Day"-Snapshot. */
    const val KEY_MULTI_DAY = "widget_multi_day"

    private val json = Json {
        encodeDefaults = true
        // Kompakter Output für NSUserDefaults-Speicherung
        prettyPrint = false
        // Unbekannte Keys ignorieren – schützt vor künftigen Widget-/App-Versionsunterschieden
        ignoreUnknownKeys = true
        // Sealed-Class-Diskriminator heißt "type" → passend zum Swift-Codable-Modell
        classDiscriminator = "type"
    }

    // ─── Öffentliche API ──────────────────────────────────────────────────────

    fun serializeUpNextState(state: WidgetUpNextState): String =
        json.encodeToString(state.toDto())

    fun serializeMultiDayState(days: List<WidgetDayState>): String =
        json.encodeToString(days.map { it.toDto() })
}
