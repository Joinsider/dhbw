package de.fampopprol.dhbwhorb.data.dualis.demo

import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * Provides demo data for the demo account.
 * This data is used when the user logs in with demo@hb.dhbw-stuttgart.de / demo123
 */
object DemoDataProvider {

    /**
     * Generate demo lecture events for a given week.
     * Creates a realistic timetable with various subjects and timings.
     */
    fun generateDemoLecturesForWeek(startDate: LocalDateTime): List<LectureEventEntity> {
        val lectures = mutableListOf<LectureEventEntity>()

        // Get Monday of the week (start of week)
        val currentDayOfWeek = startDate.dayOfWeek.isoDayNumber
        val daysToMonday = if (currentDayOfWeek == 1) 0 else -(currentDayOfWeek - 1)
        val monday = startDate.date.plus(daysToMonday, DateTimeUnit.DAY)

        // Monday
        lectures.add(
            createLecture(
                id = 1L,
                shortName = "PROG1",
                fullName = "Programmierung 1",
                date = monday,
                startHour = 8,
                startMinute = 0,
                endHour = 9,
                endMinute = 30,
                location = "Raum A1.01",
            )
        )
        lectures.add(
            createLecture(
                id = 2L,
                shortName = "PROG1",
                fullName = "Programmierung 1",
                date = monday,
                startHour = 9,
                startMinute = 45,
                endHour = 11,
                endMinute = 15,
                location = "Raum A1.01",
            )
        )
        lectures.add(
            createLecture(
                id = 3L,
                shortName = "MATH1",
                fullName = "Mathematik 1",
                date = monday,
                startHour = 11,
                startMinute = 30,
                endHour = 13,
                endMinute = 0,
                location = "Raum B2.05",
            )
        )
        lectures.add(
            createLecture(
                id = 4L,
                shortName = "DBIS",
                fullName = "Datenbanken und Informationssysteme",
                date = monday,
                startHour = 14,
                startMinute = 0,
                endHour = 15,
                endMinute = 30,
                location = "Raum C3.12",
            )
        )

        // Tuesday
        val tuesday = monday.plus(1, DateTimeUnit.DAY)
        lectures.add(
            createLecture(
                id = 5L,
                shortName = "SWENG",
                fullName = "Software Engineering",
                date = tuesday,
                startHour = 8,
                startMinute = 0,
                endHour = 9,
                endMinute = 30,
                location = "Raum A2.15",
            )
        )
        lectures.add(
            createLecture(
                id = 6L,
                shortName = "WEB",
                fullName = "Web Engineering",
                date = tuesday,
                startHour = 9,
                startMinute = 45,
                endHour = 11,
                endMinute = 15,
                location = "Raum D1.08",
            )
        )
        lectures.add(
            createLecture(
                id = 7L,
                shortName = "THEO",
                fullName = "Theoretische Informatik",
                date = tuesday,
                startHour = 13,
                startMinute = 30,
                endHour = 15,
                endMinute = 0,
                location = "Raum B1.03",
            )
        )

        // Wednesday
        val wednesday = monday.plus(2, DateTimeUnit.DAY)
        lectures.add(
            createLecture(
                id = 8L,
                shortName = "PROG1",
                fullName = "Programmierung 1",
                date = wednesday,
                startHour = 8,
                startMinute = 0,
                endHour = 9,
                endMinute = 30,
                location = "Raum A1.01",
            )
        )
        lectures.add(
            createLecture(
                id = 9L,
                shortName = "ALGO",
                fullName = "Algorithmen und Datenstrukturen",
                date = wednesday,
                startHour = 10,
                startMinute = 0,
                endHour = 11,
                endMinute = 30,
                location = "Raum C2.20",
            )
        )
        lectures.add(
            createLecture(
                id = 10L,
                shortName = "BWL",
                fullName = "Betriebswirtschaftslehre",
                date = wednesday,
                startHour = 11,
                startMinute = 45,
                endHour = 13,
                endMinute = 15,
                location = "Raum A3.05",
            )
        )

        // Thursday
        val thursday = monday.plus(3, DateTimeUnit.DAY)
        lectures.add(
            createLecture(
                id = 11L,
                shortName = "NETZ",
                fullName = "Netzwerktechnik",
                date = thursday,
                startHour = 8,
                startMinute = 0,
                endHour = 9,
                endMinute = 30,
                location = "Raum D2.11",
            )
        )
        lectures.add(
            createLecture(
                id = 12L,
                shortName = "MATH1",
                fullName = "Mathematik 1",
                date = thursday,
                startHour = 9,
                startMinute = 45,
                endHour = 11,
                endMinute = 15,
                location = "Raum B2.05",
            )
        )
        lectures.add(
            createLecture(
                id = 13L,
                shortName = "PROJ",
                fullName = "Projektmanagement",
                date = thursday,
                startHour = 13,
                startMinute = 0,
                endHour = 14,
                endMinute = 30,
                location = "Raum A1.15",
            )
        )

        // Friday
        val friday = monday.plus(4, DateTimeUnit.DAY)
        lectures.add(
            createLecture(
                id = 14L,
                shortName = "WEB",
                fullName = "Web Engineering",
                date = friday,
                startHour = 8,
                startMinute = 0,
                endHour = 9,
                endMinute = 30,
                location = "Raum D1.08",
            )
        )
        lectures.add(
            createLecture(
                id = 15L,
                shortName = "DBIS",
                fullName = "Datenbanken und Informationssysteme",
                date = friday,
                startHour = 10,
                startMinute = 0,
                endHour = 11,
                endMinute = 30,
                location = "Raum C3.12",
            )
        )
        lectures.add(
            createLecture(
                id = 16L,
                shortName = "SWENG",
                fullName = "Software Engineering",
                date = friday,
                startHour = 11,
                startMinute = 45,
                endHour = 13,
                endMinute = 15,
                location = "Raum A2.15",
                isTest = false
            )
        )

        return lectures
    }

    /**
     * Generate demo lecturers.
     */
    fun generateDemoLecturers(): List<LecturerEntity> {
        return listOf(
            LecturerEntity(
                lecturerId = 1L,
                lecturerName = "Prof. Dr. Schmidt",
                lecturerEmail = "schmidt@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-101"
            ),
            LecturerEntity(
                lecturerId = 2L,
                lecturerName = "Prof. Dr. Müller",
                lecturerEmail = "mueller@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-102"
            ),
            LecturerEntity(
                lecturerId = 3L,
                lecturerName = "Prof. Dr. Weber",
                lecturerEmail = "weber@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-103"
            ),
            LecturerEntity(
                lecturerId = 4L,
                lecturerName = "Prof. Dr. Fischer",
                lecturerEmail = "fischer@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-104"
            ),
            LecturerEntity(
                lecturerId = 5L,
                lecturerName = "Prof. Dr. Meyer",
                lecturerEmail = "meyer@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-105"
            ),
            LecturerEntity(
                lecturerId = 6L,
                lecturerName = "Prof. Dr. Wagner",
                lecturerEmail = "wagner@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-106"
            ),
            LecturerEntity(
                lecturerId = 7L,
                lecturerName = "Prof. Dr. Becker",
                lecturerEmail = "becker@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-107"
            ),
            LecturerEntity(
                lecturerId = 8L,
                lecturerName = "Prof. Dr. Schulz",
                lecturerEmail = "schulz@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-108"
            ),
            LecturerEntity(
                lecturerId = 9L,
                lecturerName = "Prof. Dr. Hoffmann",
                lecturerEmail = "hoffmann@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-109"
            ),
            LecturerEntity(
                lecturerId = 10L,
                lecturerName = "Prof. Dr. Koch",
                lecturerEmail = "koch@dhbw.de",
                lecturerPhoneNumber = "+49 711 1234-110"
            )
        )
    }

    /**
     * Get lecturer IDs for a specific lecture ID.
     * Maps demo lectures to their lecturers.
     */
    fun getLecturerIdsForLecture(lectureId: Long): List<Long> {
        return when (lectureId) {
            1L, 2L, 8L -> listOf(1L) // PROG1 - Prof. Dr. Schmidt
            3L, 12L -> listOf(2L) // MATH1 - Prof. Dr. Müller
            4L, 15L -> listOf(3L) // DBIS - Prof. Dr. Weber
            5L, 16L -> listOf(4L) // SWENG - Prof. Dr. Fischer
            6L, 14L -> listOf(5L) // WEB - Prof. Dr. Meyer
            7L -> listOf(6L) // THEO - Prof. Dr. Wagner
            9L -> listOf(7L) // ALGO - Prof. Dr. Becker
            10L -> listOf(8L) // BWL - Prof. Dr. Schulz
            11L -> listOf(9L) // NETZ - Prof. Dr. Hoffmann
            13L -> listOf(10L) // PROJ - Prof. Dr. Koch
            else -> emptyList()
        }
    }

    /**
     * Helper function to create a lecture event.
     */
    @OptIn(ExperimentalTime::class)
    private fun createLecture(
        id: Long,
        shortName: String,
        fullName: String,
        date: kotlinx.datetime.LocalDate,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        location: String,
        isTest: Boolean = false
    ): LectureEventEntity {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        return LectureEventEntity(
            lectureId = id,
            shortSubjectName = shortName,
            fullSubjectName = fullName,
            startTime = LocalDateTime(date.year, date.month, date.day, startHour, startMinute, 0),
            endTime = LocalDateTime(date.year, date.month, date.day, endHour, endMinute, 0),
            location = location,
            isTest = isTest,
            fetchedAt = now
        )
    }
}

