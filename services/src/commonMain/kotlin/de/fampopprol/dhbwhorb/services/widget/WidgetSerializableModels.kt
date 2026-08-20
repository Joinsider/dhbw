// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.services.widget

import de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetDayState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Serialisierbare DTOs ─────────────────────────────────────────────────────
// Lightweight, kotlinx.serialization-annotated mirror of the domain models.
// All fields use only primitive/String types so no additional type serializers
// are required (LocalDateTime is avoided here by pre-formatting + epoch seconds).

/**
 * JSON-serialisierbare Darstellung einer einzelnen Vorlesungsstunde für das Widget.
 *
 * @property startEpoch  Unix-Epoch-Sekunden des Startzeitpunkts – wird in Swift für
 *                       `Date(timeIntervalSince1970:)` und Refresh-Planung genutzt.
 * @property endEpoch    Unix-Epoch-Sekunden des Endzeitpunkts.
 */
@Serializable
data class WidgetClassDto(
    val name: String,
    val shortName: String,
    @SerialName("startTime") val formattedStartTime: String,
    @SerialName("endTime")   val formattedEndTime: String,
    val location: String,
    val isTest: Boolean,
    val isOngoing: Boolean,
    val startEpoch: Long,
    val endEpoch: Long,
)

/** JSON-serialisierbare Darstellung eines Tages mit seinen Vorlesungen. */
@Serializable
data class WidgetDayDto(
    /** ISO-8601 Datums-String `"YYYY-MM-DD"`. */
    val date: String,
    val classes: List<WidgetClassDto>,
)

/**
 * JSON-serialisierbare sealed-Variante des "Up Next"-Zustands.
 *
 * Das `type`-Diskriminatorfeld (Standard von kotlinx.serialization) entspricht
 * den `@SerialName`-Werten und wird vom Swift-`Codable`-Decoder auf der
 * Widget-Seite direkt ausgewertet.
 */
@Serializable
sealed class WidgetUpNextDto {
    @Serializable
    @SerialName("currently_running")
    data class CurrentlyRunning(val lecture: WidgetClassDto) : WidgetUpNextDto()

    @Serializable
    @SerialName("coming_up")
    data class ComingUp(val lecture: WidgetClassDto) : WidgetUpNextDto()

    @Serializable
    @SerialName("no_more_today")
    data object NoMoreClassesToday : WidgetUpNextDto()
}

// ─── Mapping-Erweiterungen ────────────────────────────────────────────────────

internal fun WidgetClassState.toDto(): WidgetClassDto {
    val tz = TimeZone.currentSystemDefault()
    return WidgetClassDto(
        name = name,
        shortName = shortName,
        formattedStartTime = formattedStartTime,
        formattedEndTime = formattedEndTime,
        location = location,
        isTest = isTest,
        isOngoing = isOngoing,
        startEpoch = startTime.toInstant(tz).epochSeconds,
        endEpoch = endTime.toInstant(tz).epochSeconds,
    )
}

internal fun WidgetDayState.toDto(): WidgetDayDto = WidgetDayDto(
    date = date.toString(),          // LocalDate.toString() → "YYYY-MM-DD"
    classes = classes.map { it.toDto() },
)

internal fun WidgetUpNextState.toDto(): WidgetUpNextDto = when (this) {
    is WidgetUpNextState.CurrentlyRunning -> WidgetUpNextDto.CurrentlyRunning(lecture.toDto())
    is WidgetUpNextState.ComingUp         -> WidgetUpNextDto.ComingUp(lecture.toDto())
    WidgetUpNextState.NoMoreClassesToday  -> WidgetUpNextDto.NoMoreClassesToday
}

