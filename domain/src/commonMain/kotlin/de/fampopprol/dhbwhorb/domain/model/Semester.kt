/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

/**
 * A semester as Dualis identifies it.
 *
 * Replaces the `Map<String, String>` of name to id that used to travel through the layers, where
 * nothing said which side of the pair was which.
 */
data class Semester(
    val id: String,
    val name: String
) {
    companion object {
        /**
         * The synthetic "all semesters" selection. It is a real value rather than the magic
         * string `ALL_SEMESTERS_VIEW` that used to be compared against by hand.
         */
        val All = Semester(id = "ALL_SEMESTERS_VIEW", name = "")

        fun isAll(semester: Semester): Boolean = semester.id == All.id
    }
}
