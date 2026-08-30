/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.core.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests for the [Outcome] helpers.
 *
 * These are exercised indirectly by every service that returns an [Outcome], but that leaves the
 * helper functions themselves — [map], [flatMap], [fold], [onOk]/[onErr], [recover] — with no
 * direct test naming which branch of which function is under test.
 */
class OutcomeTest {

    private val ok: Outcome<Int> = Outcome.Ok(42)
    private val err: Outcome<Int> = Outcome.Err(AppError.Offline)

    // ── asOk / asErr ─────────────────────────────────────────────────────────

    @Test
    fun asOk_wrapsTheValue() {
        assertEquals(Outcome.Ok(5), 5.asOk())
    }

    @Test
    fun asErr_wrapsTheError() {
        assertEquals(Outcome.Err(AppError.Offline), AppError.Offline.asErr())
    }

    // ── isOk ─────────────────────────────────────────────────────────────────

    @Test
    fun isOk_isTrueForOkAndFalseForErr() {
        assertTrue(ok.isOk)
        assertFalse(err.isOk)
    }

    // ── getOrNull / errorOrNull ──────────────────────────────────────────────

    @Test
    fun getOrNull_returnsTheValueForOk_andNullForErr() {
        assertEquals(42, ok.getOrNull())
        assertEquals(null, err.getOrNull())
    }

    @Test
    fun errorOrNull_returnsTheErrorForErr_andNullForOk() {
        assertEquals(AppError.Offline, err.errorOrNull())
        assertEquals(null, ok.errorOrNull())
    }

    // ── getOrElse ────────────────────────────────────────────────────────────

    @Test
    fun getOrElse_returnsTheValueForOk_withoutCallingTheFallback() {
        var fallbackCalled = false
        val result = ok.getOrElse { fallbackCalled = true; -1 }

        assertEquals(42, result)
        assertFalse(fallbackCalled)
    }

    @Test
    fun getOrElse_computesTheFallbackFromTheErrorForErr() {
        val result = err.getOrElse { error -> if (error == AppError.Offline) -1 else -2 }
        assertEquals(-1, result)
    }

    // ── map ──────────────────────────────────────────────────────────────────

    @Test
    fun map_transformsTheValueOfAnOk() {
        assertEquals(Outcome.Ok("42"), ok.map { it.toString() })
    }

    @Test
    fun map_passesAnErrThrough_untouched() {
        val result: Outcome<String> = err.map { it.toString() }
        assertEquals(AppError.Offline, result.errorOrNull())
    }

    // ── flatMap ──────────────────────────────────────────────────────────────

    @Test
    fun flatMap_chainsIntoTheNextOutcomeForAnOk() {
        val result = ok.flatMap { Outcome.Ok(it * 2) }
        assertEquals(Outcome.Ok(84), result)
    }

    @Test
    fun flatMap_chainsIntoAFailureForAnOk() {
        val result: Outcome<String> = ok.flatMap { Outcome.Err(AppError.InvalidCredentials) }
        assertEquals(Outcome.Err(AppError.InvalidCredentials), result)
    }

    @Test
    fun flatMap_passesAnErrThrough_withoutCallingTheTransform() {
        var called = false
        val result = err.flatMap { called = true; Outcome.Ok(it * 2) }

        assertEquals(err, result)
        assertFalse(called)
    }

    // ── fold ─────────────────────────────────────────────────────────────────

    @Test
    fun fold_callsOnOkForAnOk() {
        val result = ok.fold(onOk = { "value:$it" }, onErr = { "error" })
        assertEquals("value:42", result)
    }

    @Test
    fun fold_callsOnErrForAnErr() {
        val result = err.fold(onOk = { "value:$it" }, onErr = { "error:$it" })
        assertEquals("error:${AppError.Offline}", result)
    }

    // ── onOk / onErr ─────────────────────────────────────────────────────────

    @Test
    fun onOk_runsTheActionForAnOk_andReturnsTheSameOutcome() {
        var seen: Int? = null
        val result = ok.onOk { seen = it }

        assertEquals(42, seen)
        assertEquals(ok, result)
    }

    @Test
    fun onOk_doesNothingForAnErr() {
        var called = false
        val result = err.onOk { called = true }

        assertFalse(called)
        assertEquals(err, result)
    }

    @Test
    fun onErr_runsTheActionForAnErr_andReturnsTheSameOutcome() {
        var seen: AppError? = null
        val result = err.onErr { seen = it }

        assertEquals(AppError.Offline, seen)
        assertEquals(err, result)
    }

    @Test
    fun onErr_doesNothingForAnOk() {
        var called = false
        val result = ok.onErr { called = true }

        assertFalse(called)
        assertEquals(ok, result)
    }

    // ── recover ──────────────────────────────────────────────────────────────

    @Test
    fun recover_passesAnOkThrough_withoutCallingTheTransform() {
        var called = false
        val result = ok.recover { called = true; Outcome.Ok(-1) }

        assertEquals(ok, result)
        assertFalse(called)
    }

    @Test
    fun recover_substitutesTheTransformsResultForAnErr() {
        val result = err.recover { Outcome.Ok(-1) }
        assertEquals(Outcome.Ok(-1), result)
    }
}
