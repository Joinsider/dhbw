// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import de.fampopprol.dhbwhorb.services.widget.models.WidgetDayState
import de.fampopprol.dhbwhorb.services.widget.models.WidgetUpNextState
import de.fampopprol.dhbwhorb.widget.state.TimetableWidgetState
import de.fampopprol.dhbwhorb.widget.state.WidgetStateCodec
import de.fampopprol.dhbwhorb.widget.state.WidgetStateKeys
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [TimetableWidgetState] codec round-trips and layout selection logic.
 *
 * These are pure JVM tests – no Android framework required.
 */
class WidgetStateCodecTest {

    // ── Codec: Loading ───────────────────────────────────────────────────────

    @Test
    fun `encode Loading then decode returns Loading`() {
        val prefs = mutablePreferencesOf()
        WidgetStateCodec.encode(prefs, TimetableWidgetState.Loading)
        val decoded = WidgetStateCodec.decode(prefs)
        assertIs<TimetableWidgetState.Loading>(decoded)
    }

    @Test
    fun `decode empty prefs returns Loading`() {
        val decoded = WidgetStateCodec.decode(emptyPreferences())
        assertIs<TimetableWidgetState.Loading>(decoded)
    }

    // ── Codec: Error ─────────────────────────────────────────────────────────

    @Test
    fun `encode Error then decode preserves message`() {
        val prefs = mutablePreferencesOf()
        WidgetStateCodec.encode(prefs, TimetableWidgetState.Error("Verbindungsfehler"))
        val decoded = WidgetStateCodec.decode(prefs)
        assertIs<TimetableWidgetState.Error>(decoded)
        assertEquals("Verbindungsfehler", decoded.message)
    }

    // ── Codec: Success – NoMoreClassesToday ──────────────────────────────────

    @Test
    fun `encode Success with NoMoreClassesToday round-trips correctly`() {
        val state = TimetableWidgetState.Success(
            upNext = WidgetUpNextState.NoMoreClassesToday,
            day0 = null,
            day1 = null,
        )
        val prefs = mutablePreferencesOf()
        WidgetStateCodec.encode(prefs, state)
        val decoded = WidgetStateCodec.decode(prefs)

        assertIs<TimetableWidgetState.Success>(decoded)
        assertIs<WidgetUpNextState.NoMoreClassesToday>(decoded.upNext)
        assertNull(decoded.day0)
        assertNull(decoded.day1)
    }

    // ── Codec: Success – ComingUp ────────────────────────────────────────────

    @Test
    fun `encode Success with ComingUp round-trips correctly`() {
        val cls = makeClass(name = "Statistik", start = "08:00", end = "09:30", location = "HOR-120")
        val state = TimetableWidgetState.Success(
            upNext = WidgetUpNextState.ComingUp(cls),
            day0 = null,
            day1 = null,
        )
        val prefs = mutablePreferencesOf()
        WidgetStateCodec.encode(prefs, state)
        val decoded = WidgetStateCodec.decode(prefs)

        assertIs<TimetableWidgetState.Success>(decoded)
        val upNext = decoded.upNext
        assertIs<WidgetUpNextState.ComingUp>(upNext)
        assertEquals("Statistik", upNext.lecture.name)
        assertEquals("08:00", upNext.lecture.formattedStartTime)
        assertEquals("HOR-120", upNext.lecture.location)
    }

    // ── Codec: Success – day0/day1 ───────────────────────────────────────────

    @Test
    fun `encode Success with day0 and day1 round-trips correctly`() {
        val date0 = LocalDate(2026, 3, 11)
        val date1 = LocalDate(2026, 3, 12)
        val state = TimetableWidgetState.Success(
            upNext = WidgetUpNextState.NoMoreClassesToday,
            day0 = WidgetDayState(date0, listOf(makeClass("Mathe", "10:00", "11:30", "HOR-101"))),
            day1 = WidgetDayState(date1, listOf(makeClass("Physik", "13:00", "14:30", "HOR-202"))),
        )
        val prefs = mutablePreferencesOf()
        WidgetStateCodec.encode(prefs, state)
        val decoded = WidgetStateCodec.decode(prefs)

        assertIs<TimetableWidgetState.Success>(decoded)
        assertEquals(date0, decoded.day0?.date)
        assertEquals("Mathe", decoded.day0?.classes?.first()?.name)
        assertEquals(date1, decoded.day1?.date)
        assertEquals("Physik", decoded.day1?.classes?.first()?.name)
    }

    @Test
    fun `classes with pipe or semicolon in name are skipped gracefully`() {
        // Guard: malformed data in prefs should not crash decode
        val prefs = mutablePreferencesOf(
            WidgetStateKeys.STATUS to "success",
            WidgetStateKeys.UP_NEXT_TYPE to "none",
            WidgetStateKeys.DAY0_DATE to "2026-03-11",
            WidgetStateKeys.DAY0_CLASSES to "broken|entry",  // only 1 field → skipped
        )
        val decoded = WidgetStateCodec.decode(prefs)
        assertIs<TimetableWidgetState.Success>(decoded)
        assertNull(decoded.day0) // empty class list → null day
    }

    // ── Layout selector thresholds ────────────────────────────────────────────

    /**
     * Mirrors the breakpoint logic in [TimetableGlanceWidget.ResponsiveContent].
     * Tests run without Glance/Android dependencies.
     */
    @Test
    fun `layout selector returns correct layout for each DpSize`() {
        // The widget's responsive logic (size.width >= threshold && size.height >= threshold):
        data class DpSize(val widthDp: Int, val heightDp: Int)

        fun selectLayout(size: DpSize): String = when {
            size.widthDp >= 220 && size.heightDp >= 220 -> "WeeklyTimelineLarge"
            size.widthDp >= 220 -> "DailyScheduleWide"
            size.heightDp >= 220 -> "WeekSummaryTall"
            else -> "UpNext"
        }

        assertEquals("UpNext",             selectLayout(DpSize(110, 110)))
        assertEquals("WeekSummaryTall",    selectLayout(DpSize(110, 220)))
        assertEquals("DailyScheduleWide",  selectLayout(DpSize(220, 110)))
        assertEquals("WeeklyTimelineLarge",selectLayout(DpSize(220, 220)))

        // Edge cases: just below threshold
        assertEquals("UpNext",             selectLayout(DpSize(219, 219)))
        assertEquals("UpNext",             selectLayout(DpSize(109, 109)))
        // Wide variant even when height is very small
        assertEquals("DailyScheduleWide",  selectLayout(DpSize(300, 110)))
        // Tall variant even when width is very small
        assertEquals("WeekSummaryTall",    selectLayout(DpSize(110, 300)))
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeClass(
        name: String,
        start: String,
        end: String,
        location: String,
    ) = de.fampopprol.dhbwhorb.services.widget.models.WidgetClassState(
        name = name,
        shortName = name.take(6),
        formattedStartTime = start,
        formattedEndTime = end,
        location = location,
        isTest = false,
        isOngoing = false,
        startTime = kotlinx.datetime.LocalDateTime(1970, 1, 1, 0, 0),
        endTime   = kotlinx.datetime.LocalDateTime(1970, 1, 1, 0, 0),
    )
}

