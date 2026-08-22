/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.util

/**
 * The language the user reads, as a two-letter code — `"de"`, `"en"`, whatever the system says.
 *
 * Almost nothing needs this: both UIs get their text from their own resource system, which knows
 * the language without asking. The exception is text produced where there is no UI at all — a
 * notification written by a background worker. See `LectureChangeMessages`.
 */
expect fun currentLanguage(): String
