/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.session

/**
 * Something outside the repositories that still holds data belonging to the logged-in account.
 *
 * The database, the credentials and the session are [de.fampopprol.dhbwhorb.domain.repository.AuthRepository]'s
 * to clear. What is left is everything the app handed to the *system*: scheduled reminders for
 * lectures nobody is having any more, a home screen widget still drawing last week's timetable,
 * document files written to a cache directory so a viewer could open them. None of it lives in a
 * table, and all of it is the previous user's.
 *
 * An interface in `:domain` because [de.fampopprol.dhbwhorb.domain.usecase.Logout] has to call it
 * while the implementations sit in `:services`, which depends on `:domain` and not the other way
 * round.
 */
fun interface SessionDataCleaner {

    /**
     * Drop everything derived from the session that just ended.
     *
     * Runs after the session, the credentials and the cached tables are gone, so an implementation
     * that reads the database sees it empty — which is what a widget refresh needs.
     */
    suspend fun clearSessionData()
}
