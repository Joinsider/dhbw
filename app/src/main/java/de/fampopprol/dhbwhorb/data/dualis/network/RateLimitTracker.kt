/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package de.fampopprol.dhbwhorb.data.dualis.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RateLimitState(
    val isRateLimited: Boolean = false,
    val attempt: Int = 0,
    val maxAttempts: Int = 3,
    val finalFailure: Boolean = false
) {
    companion object {
        val Idle = RateLimitState()
    }
}

object RateLimitTracker {
    private val _state = MutableStateFlow(RateLimitState.Idle)
    val state: StateFlow<RateLimitState> = _state

    fun updateRateLimit(attempt: Int, maxAttempts: Int) {
        _state.value = RateLimitState(isRateLimited = true, attempt = attempt, maxAttempts = maxAttempts, finalFailure = false)
    }

    fun finalFailure(maxAttempts: Int) {
        _state.value = RateLimitState(isRateLimited = true, attempt = maxAttempts, maxAttempts = maxAttempts, finalFailure = true)
    }

    fun clear() {
        _state.value = RateLimitState.Idle
    }
}

