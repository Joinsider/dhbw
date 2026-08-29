/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.model

/**
 * Semesters in the order they were studied.
 *
 * Sorting their names as strings puts "WiSe 2024/25" next to "WiSe 2025/26" and "SoSe 2025"
 * somewhere else entirely, which is what the grades screen used to show. What the reader expects
 * is the sequence the semesters happened in: WiSe 2024/25 → SoSe 2025 → WiSe 2025/26.
 */
object SemesterOrder {

    /**
     * A number that grows with time, or null for a name this cannot read.
     *
     * Dualis writes "WiSe 2025/26" and "SoSe 2025"; older exports use "WS 2025/26" and "SS 2025".
     * All four start with the letter that says which half of the year it is — W for the winter
     * term, S for the summer one — and carry the calendar year the term *starts* in. Two slots
     * per year are enough, because a year has exactly these two terms.
     */
    fun sortKey(semesterName: String): Int? {
        val year = Regex("""\d{4}""").find(semesterName)?.value?.toIntOrNull() ?: return null
        val isWinter = when (semesterName.trimStart().firstOrNull()?.uppercaseChar()) {
            'W' -> true
            'S' -> false
            else -> return null
        }
        return year * 2 + if (isWinter) 1 else 0
    }

    /**
     * Oldest first.
     *
     * Names without a readable key sort to the end rather than to the front: an unparsed name is
     * more likely to be something new than something from before 2000, and putting it last keeps
     * it out of the middle of the history.
     */
    val oldestFirst: Comparator<String> = compareBy(
        { sortKey(it) == null },
        { sortKey(it) ?: 0 },
        { it }
    )
}
