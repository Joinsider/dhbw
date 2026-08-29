/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.repository

import de.fampopprol.dhbwhorb.domain.model.Session

/**
 * Read-only view of the stored session.
 *
 * Deliberately synchronous and non-failing: these are local lookups in secure storage, and a
 * caller asking "am I logged in" cannot do anything useful with an error.
 */
interface SessionRepository {

    /** The active session, or null when nobody is logged in. */
    fun currentSession(): Session?

    /** True when a session exists — including a demo session. */
    fun isLoggedIn(): Boolean

    /**
     * True when a login can be attempted without asking the user again: either a session is
     * active or credentials are stored for re-authentication.
     */
    fun canAuthenticate(): Boolean

    /** True while the demo account is in use. */
    fun isDemoMode(): Boolean
}
