/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

/**
 * One completed background lecture check: when it finished and how it ended.
 *
 * [epochMillis] rather than an `Instant`, as everywhere else the app stores a time: it crosses into
 * Swift as a plain `Int64`, and it is what the preferences hold.
 */
data class LectureCheck(
    val epochMillis: Long,
    val outcome: LectureCheckOutcome,
)

enum class LectureCheckOutcome {
    /** The check ran. Also covers runs with nothing to do, such as alerts being switched off. */
    SUCCESS,

    /** The check could not reach Dualis. Retried on the next run like any failure. */
    OFFLINE,

    /** The check failed for any other reason, for example a changed page or an expired login. */
    FAILED,
}
