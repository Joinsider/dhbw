// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.state

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetDayState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

// ─── Preference keys ─────────────────────────────────────────────────────────

object WidgetStateKeys {
    val STATUS = stringPreferencesKey("widget_status")
    val ERROR_MSG = stringPreferencesKey("widget_error_msg")
    val UP_NEXT_TYPE = stringPreferencesKey("up_next_type")
    val UP_NEXT_NAME = stringPreferencesKey("up_next_name")
    val UP_NEXT_SHORT = stringPreferencesKey("up_next_short")
    val UP_NEXT_START = stringPreferencesKey("up_next_start")
    val UP_NEXT_END = stringPreferencesKey("up_next_end")
    val UP_NEXT_LOCATION = stringPreferencesKey("up_next_location")
    val UP_NEXT_IS_TEST = stringPreferencesKey("up_next_is_test")
    val DAY0_DATE = stringPreferencesKey("day0_date")
    val DAY0_CLASSES = stringPreferencesKey("day0_classes")
    val DAY1_DATE = stringPreferencesKey("day1_date")
    val DAY1_CLASSES = stringPreferencesKey("day1_classes")
}

// ─── Domain state ─────────────────────────────────────────────────────────────

sealed class TimetableWidgetState {
    data object Loading : TimetableWidgetState()
    data class Error(val message: String) : TimetableWidgetState()
    data class Success(
        val upNext: WidgetUpNextState,
        val day0: WidgetDayState?,
        val day1: WidgetDayState?,
    ) : TimetableWidgetState()
}

// ─── Codec ────────────────────────────────────────────────────────────────────

object WidgetStateCodec {

    private const val CLASS_SEP = ";"
    private const val FIELD_SEP = "|"

    fun encode(prefs: MutablePreferences, state: TimetableWidgetState) {
        when (state) {
            TimetableWidgetState.Loading -> prefs[WidgetStateKeys.STATUS] = "loading"
            is TimetableWidgetState.Error -> {
                prefs[WidgetStateKeys.STATUS] = "error"
                prefs[WidgetStateKeys.ERROR_MSG] = state.message
            }
            is TimetableWidgetState.Success -> {
                prefs[WidgetStateKeys.STATUS] = "success"
                encodeUpNext(prefs, state.upNext)
                state.day0?.let { encodeDay(prefs, it, 0) }
                state.day1?.let { encodeDay(prefs, it, 1) }
            }
        }
    }

    fun decode(prefs: Preferences): TimetableWidgetState = when (prefs[WidgetStateKeys.STATUS]) {
        "error" -> TimetableWidgetState.Error(prefs[WidgetStateKeys.ERROR_MSG] ?: "Unknown error")
        "success" -> TimetableWidgetState.Success(
            upNext = decodeUpNext(prefs),
            day0 = decodeDay(prefs, 0),
            day1 = decodeDay(prefs, 1),
        )
        else -> TimetableWidgetState.Loading
    }

    private fun encodeUpNext(prefs: MutablePreferences, state: WidgetUpNextState) {
        when (state) {
            WidgetUpNextState.NoMoreClassesToday -> prefs[WidgetStateKeys.UP_NEXT_TYPE] = "none"
            is WidgetUpNextState.CurrentlyRunning -> {
                prefs[WidgetStateKeys.UP_NEXT_TYPE] = "running"
                encodeClass(prefs, state.lecture)
            }
            is WidgetUpNextState.ComingUp -> {
                prefs[WidgetStateKeys.UP_NEXT_TYPE] = "coming"
                encodeClass(prefs, state.lecture)
            }
        }
    }

    private fun encodeClass(prefs: MutablePreferences, c: WidgetClassState) {
        prefs[WidgetStateKeys.UP_NEXT_NAME] = c.name
        prefs[WidgetStateKeys.UP_NEXT_SHORT] = c.shortName
        prefs[WidgetStateKeys.UP_NEXT_START] = c.formattedStartTime
        prefs[WidgetStateKeys.UP_NEXT_END] = c.formattedEndTime
        prefs[WidgetStateKeys.UP_NEXT_LOCATION] = c.location
        prefs[WidgetStateKeys.UP_NEXT_IS_TEST] = c.isTest.toString()
    }

    private fun decodeUpNext(prefs: Preferences): WidgetUpNextState {
        val type = prefs[WidgetStateKeys.UP_NEXT_TYPE] ?: return WidgetUpNextState.NoMoreClassesToday
        if (type == "none") return WidgetUpNextState.NoMoreClassesToday
        val cls = WidgetClassState(
            name = prefs[WidgetStateKeys.UP_NEXT_NAME] ?: "",
            shortName = prefs[WidgetStateKeys.UP_NEXT_SHORT] ?: "",
            formattedStartTime = prefs[WidgetStateKeys.UP_NEXT_START] ?: "",
            formattedEndTime = prefs[WidgetStateKeys.UP_NEXT_END] ?: "",
            location = prefs[WidgetStateKeys.UP_NEXT_LOCATION] ?: "",
            isTest = prefs[WidgetStateKeys.UP_NEXT_IS_TEST] == "true",
            isOngoing = type == "running",
            startTime = LocalDateTime(1970, 1, 1, 0, 0),
            endTime = LocalDateTime(1970, 1, 1, 0, 0),
        )
        return if (type == "running") WidgetUpNextState.CurrentlyRunning(cls)
        else WidgetUpNextState.ComingUp(cls)
    }

    private fun encodeDay(prefs: MutablePreferences, day: WidgetDayState, idx: Int) {
        val dateKey = if (idx == 0) WidgetStateKeys.DAY0_DATE else WidgetStateKeys.DAY1_DATE
        val clsKey  = if (idx == 0) WidgetStateKeys.DAY0_CLASSES else WidgetStateKeys.DAY1_CLASSES
        prefs[dateKey] = day.date.toString()
        prefs[clsKey]  = day.classes.joinToString(CLASS_SEP) { c ->
            listOf(c.name, c.shortName, c.formattedStartTime, c.formattedEndTime,
                   c.location, c.isTest.toString(), c.isOngoing.toString()).joinToString(FIELD_SEP)
        }
    }

    private fun decodeDay(prefs: Preferences, idx: Int): WidgetDayState? {
        val dateKey = if (idx == 0) WidgetStateKeys.DAY0_DATE else WidgetStateKeys.DAY1_DATE
        val clsKey  = if (idx == 0) WidgetStateKeys.DAY0_CLASSES else WidgetStateKeys.DAY1_CLASSES
        val dateStr = prefs[dateKey] ?: return null
        val clsStr  = prefs[clsKey]  ?: return null
        val date    = LocalDate.parse(dateStr)
        val classes = clsStr.split(CLASS_SEP).mapNotNull { entry ->
            val p = entry.split(FIELD_SEP)
            if (p.size < 7) null
            else WidgetClassState(
                name = p[0], shortName = p[1],
                formattedStartTime = p[2], formattedEndTime = p[3],
                location = p[4],
                isTest = p[5] == "true", isOngoing = p[6] == "true",
                startTime = LocalDateTime(date.year, date.month, date.day, 0, 0),
                endTime   = LocalDateTime(date.year, date.month, date.day, 0, 0),
            )
        }
        return if (classes.isEmpty()) null else WidgetDayState(date, classes)
    }
}
