/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.util.currentLanguage
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number

/**
 * The text of every notification this app sends, in the user's language.
 *
 * **Why the strings live here and not in a resource file.** Everything else the user reads comes
 * from one of the two UI resource systems — Compose resources on Android and Desktop, a string
 * catalogue on iOS — and that is still the rule for anything with a screen behind it. A
 * notification has no screen behind it: it is written by a background worker in `:services`, which
 * can reach neither system. Copying the formatting into all four platform dispatchers instead
 * would put one behaviour in four places, which is the thing this rebuild keeps removing.
 *
 * So: one table, two languages, chosen by [currentLanguage]. Adding a language means adding a
 * [Strings] implementation and nothing else.
 */
object LectureNotificationTexts {

    private val strings: Strings
        get() = if (currentLanguage() == "de") German else English

    /** Title and body for a single change. */
    fun single(change: LectureChange): Pair<String, String> = strings.single(change)

    /** Title and body for a run that found more than one. */
    fun summary(changes: List<LectureChange>): Pair<String, String> = strings.summary(changes)

    /** Title and body for the reminder that fires [leadMinutes] before a lecture starts. */
    fun reminder(
        courseName: String,
        location: String,
        startsAt: LocalDateTime,
        leadMinutes: Int,
    ): Pair<String, String> = strings.reminder(courseName, location, startsAt, leadMinutes)

    // ── The two tables ──────────────────────────────────────────────────────────────────────

    private interface Strings {
        fun single(change: LectureChange): Pair<String, String>
        fun summary(changes: List<LectureChange>): Pair<String, String>
        fun reminder(
            courseName: String,
            location: String,
            startsAt: LocalDateTime,
            leadMinutes: Int,
        ): Pair<String, String>
    }

    private object German : Strings {
        override fun single(change: LectureChange): Pair<String, String> = when (change) {
            is LectureChange.TimeChange ->
                "Verschoben: ${change.courseName}" to
                    "Von ${change.oldStartTime.dayAndTime()} auf ${change.newStartTime.dayAndTime()}"

            is LectureChange.LocationChange ->
                "Raumwechsel: ${change.courseName}" to
                    "${change.occursAt.dayAndTime()}: ${change.oldLocation.orDash()} → ${change.newLocation.orDash()}"

            is LectureChange.LecturerChange ->
                "Dozentenwechsel: ${change.courseName}" to
                    "${change.occursAt.dayAndTime()}: statt ${change.oldLecturers.list("niemandem")} " +
                    "jetzt ${change.newLecturers.list("niemand")}"

            is LectureChange.TypeChange ->
                if (change.newIsTest) {
                    "Jetzt eine Prüfung: ${change.courseName}" to
                        "Am ${change.occursAt.dayAndTime()} statt der Vorlesung"
                } else {
                    "Doch keine Prüfung: ${change.courseName}" to
                        "Am ${change.occursAt.dayAndTime()} wieder eine normale Vorlesung"
                }

            is LectureChange.Cancellation ->
                "Fällt aus: ${change.courseName}" to "Am ${change.occursAt.dayAndTime()}"

            is LectureChange.NewLecture ->
                "Neu im Plan: ${change.courseName}" to "Am ${change.occursAt.dayAndTime()}"
        }

        override fun summary(changes: List<LectureChange>) =
            "Stundenplan geändert" to changes.count(
                CategoryWords(
                    move = "Verschiebung" to "Verschiebungen",
                    room = "Raumwechsel" to "Raumwechsel",
                    lecturer = "Dozentenwechsel" to "Dozentenwechsel",
                    type = "Prüfungsänderung" to "Prüfungsänderungen",
                    cancelled = "Absage" to "Absagen",
                    added = "neue Vorlesung" to "neue Vorlesungen",
                ),
                separator = ", ",
                lastSeparator = " und ",
            )

        override fun reminder(
            courseName: String,
            location: String,
            startsAt: LocalDateTime,
            leadMinutes: Int,
        ): Pair<String, String> {
            val lead = if (leadMinutes % 60 == 0) {
                val hours = leadMinutes / 60
                if (hours == 1) "In einer Stunde" else "In $hours Stunden"
            } else {
                "In $leadMinutes Minuten"
            }
            val room = location.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
            return "$lead: $courseName" to "${startsAt.hour.pad()}:${startsAt.minute.pad()} Uhr$room"
        }

        private fun LocalDateTime?.dayAndTime(): String =
            if (this == null) "unbekannt" else "${day.pad()}.${month.number.pad()}., ${hour.pad()}:${minute.pad()} Uhr"
    }

    private object English : Strings {
        override fun single(change: LectureChange): Pair<String, String> = when (change) {
            is LectureChange.TimeChange ->
                "Moved: ${change.courseName}" to
                    "From ${change.oldStartTime.dayAndTime()} to ${change.newStartTime.dayAndTime()}"

            is LectureChange.LocationChange ->
                "Room change: ${change.courseName}" to
                    "${change.occursAt.dayAndTime()}: ${change.oldLocation.orDash()} → ${change.newLocation.orDash()}"

            is LectureChange.LecturerChange ->
                "Lecturer change: ${change.courseName}" to
                    "${change.occursAt.dayAndTime()}: ${change.oldLecturers.list("nobody")} " +
                    "replaced by ${change.newLecturers.list("nobody")}"

            is LectureChange.TypeChange ->
                if (change.newIsTest) {
                    "Now an exam: ${change.courseName}" to
                        "On ${change.occursAt.dayAndTime()}, instead of the lecture"
                } else {
                    "No longer an exam: ${change.courseName}" to
                        "On ${change.occursAt.dayAndTime()}, an ordinary lecture again"
                }

            is LectureChange.Cancellation ->
                "Cancelled: ${change.courseName}" to "On ${change.occursAt.dayAndTime()}"

            is LectureChange.NewLecture ->
                "Added: ${change.courseName}" to "On ${change.occursAt.dayAndTime()}"
        }

        override fun summary(changes: List<LectureChange>) =
            "Timetable changed" to changes.count(
                CategoryWords(
                    move = "move" to "moves",
                    room = "room change" to "room changes",
                    lecturer = "lecturer change" to "lecturer changes",
                    type = "exam change" to "exam changes",
                    cancelled = "cancellation" to "cancellations",
                    added = "new lecture" to "new lectures",
                ),
                separator = ", ",
                lastSeparator = " and ",
            )

        override fun reminder(
            courseName: String,
            location: String,
            startsAt: LocalDateTime,
            leadMinutes: Int,
        ): Pair<String, String> {
            val lead = if (leadMinutes % 60 == 0) {
                val hours = leadMinutes / 60
                if (hours == 1) "In an hour" else "In $hours hours"
            } else {
                "In $leadMinutes minutes"
            }
            val room = location.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()
            return "$lead: $courseName" to "${startsAt.hour.pad()}:${startsAt.minute.pad()}$room"
        }

        private fun LocalDateTime?.dayAndTime(): String =
            if (this == null) "unknown" else "${day.pad()}/${month.number.pad()}, ${hour.pad()}:${minute.pad()}"
    }

    // ── Shared shaping ──────────────────────────────────────────────────────────────────────

    /** The singular/plural word pair for each [LectureChange] category, e.g. "move" to "moves". */
    private data class CategoryWords(
        val move: Pair<String, String>,
        val room: Pair<String, String>,
        val lecturer: Pair<String, String>,
        val type: Pair<String, String>,
        val cancelled: Pair<String, String>,
        val added: Pair<String, String>,
    )

    /**
     * "2 Verschiebungen, 1 Raumwechsel und 1 Absage" — in the order the cases are declared, which
     * is roughly the order of how much they disrupt a day.
     */
    private fun List<LectureChange>.count(
        words: CategoryWords,
        separator: String,
        lastSeparator: String,
    ): String {
        val parts = listOf(
            count { it is LectureChange.Cancellation } to words.cancelled,
            count { it is LectureChange.TimeChange } to words.move,
            count { it is LectureChange.NewLecture } to words.added,
            count { it is LectureChange.LocationChange } to words.room,
            count { it is LectureChange.TypeChange } to words.type,
            count { it is LectureChange.LecturerChange } to words.lecturer,
        ).filter { (n, _) -> n > 0 }
            .map { (n, w) -> "$n ${if (n == 1) w.first else w.second}" }

        return when (parts.size) {
            0 -> ""
            1 -> parts.first()
            else -> parts.dropLast(1).joinToString(separator) + lastSeparator + parts.last()
        }
    }

    private fun Int.pad(): String = toString().padStart(2, '0')

    private fun String.orDash(): String = ifBlank { "—" }

    private fun List<String>.list(ifEmpty: String): String =
        if (isEmpty()) ifEmpty else joinToString(", ")
}
