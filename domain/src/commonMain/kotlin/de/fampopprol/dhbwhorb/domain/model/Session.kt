/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

/**
 * An established Dualis session.
 *
 * Demo mode is a session like any other rather than a flag checked at a dozen call sites — it
 * simply reports [isDemo], and the repositories serve demo data for it.
 */
data class Session(
    val userFullName: String?,
    val isDemo: Boolean = false
)
