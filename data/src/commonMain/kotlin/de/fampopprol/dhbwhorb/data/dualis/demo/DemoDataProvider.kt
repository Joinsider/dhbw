package de.fampopprol.dhbwhorb.data.dualis.demo

import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import de.fampopprol.dhbwhorb.domain.model.ExamResult
import de.fampopprol.dhbwhorb.domain.model.ModuleAttempt
import de.fampopprol.dhbwhorb.domain.model.ModuleResultDetails
import de.fampopprol.dhbwhorb.domain.model.ModuleUnit
import de.fampopprol.dhbwhorb.domain.model.Semester
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
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
                fullName = SUBJECT_PROGRAMMIERUNG_1,
                date = monday,
                slot = LectureSlot(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30),
                location = ROOM_A101,
            )
        )
        lectures.add(
            createLecture(
                id = 2L,
                shortName = "PROG1",
                fullName = SUBJECT_PROGRAMMIERUNG_1,
                date = monday,
                slot = LectureSlot(startHour = 9, startMinute = 45, endHour = 11, endMinute = 15),
                location = ROOM_A101,
            )
        )
        lectures.add(
            createLecture(
                id = 3L,
                shortName = "MATH1",
                fullName = "Mathematik 1",
                date = monday,
                slot = LectureSlot(startHour = 11, startMinute = 30, endHour = 13, endMinute = 0),
                location = "Raum B2.05",
            )
        )
        lectures.add(
            createLecture(
                id = 4L,
                shortName = "DBIS",
                fullName = SUBJECT_DATENBANKEN,
                date = monday,
                slot = LectureSlot(startHour = 14, startMinute = 0, endHour = 15, endMinute = 30),
                location = "Raum C3.12",
            )
        )

        // Tuesday
        val tuesday = monday.plus(1, DateTimeUnit.DAY)
        lectures.add(
            createLecture(
                id = 5L,
                shortName = "SWENG",
                fullName = SUBJECT_SOFTWARE_ENGINEERING,
                date = tuesday,
                slot = LectureSlot(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30),
                location = "Raum A2.15",
            )
        )
        lectures.add(
            createLecture(
                id = 6L,
                shortName = "WEB",
                fullName = SUBJECT_WEB_ENGINEERING,
                date = tuesday,
                slot = LectureSlot(startHour = 9, startMinute = 45, endHour = 11, endMinute = 15),
                location = "Raum D1.08",
            )
        )
        lectures.add(
            createLecture(
                id = 7L,
                shortName = "THEO",
                fullName = "Theoretische Informatik",
                date = tuesday,
                slot = LectureSlot(startHour = 13, startMinute = 30, endHour = 15, endMinute = 0),
                location = "Raum B1.03",
            )
        )

        // Wednesday
        val wednesday = monday.plus(2, DateTimeUnit.DAY)
        lectures.add(
            createLecture(
                id = 8L,
                shortName = "PROG1",
                fullName = SUBJECT_PROGRAMMIERUNG_1,
                date = wednesday,
                slot = LectureSlot(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30),
                location = ROOM_A101,
            )
        )
        lectures.add(
            createLecture(
                id = 9L,
                shortName = "ALGO",
                fullName = "Algorithmen und Datenstrukturen",
                date = wednesday,
                slot = LectureSlot(startHour = 10, startMinute = 0, endHour = 11, endMinute = 30),
                location = "Raum C2.20",
            )
        )
        lectures.add(
            createLecture(
                id = 10L,
                shortName = "BWL",
                fullName = "Betriebswirtschaftslehre",
                date = wednesday,
                slot = LectureSlot(startHour = 11, startMinute = 45, endHour = 13, endMinute = 15),
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
                slot = LectureSlot(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30),
                location = "Raum D2.11",
            )
        )
        lectures.add(
            createLecture(
                id = 12L,
                shortName = "MATH1",
                fullName = "Mathematik 1",
                date = thursday,
                slot = LectureSlot(startHour = 9, startMinute = 45, endHour = 11, endMinute = 15),
                location = "Raum B2.05",
            )
        )
        lectures.add(
            createLecture(
                id = 13L,
                shortName = "PROJ",
                fullName = "Projektmanagement",
                date = thursday,
                slot = LectureSlot(startHour = 13, startMinute = 0, endHour = 14, endMinute = 30),
                location = "Raum A1.15",
            )
        )

        // Friday
        val friday = monday.plus(4, DateTimeUnit.DAY)
        lectures.add(
            createLecture(
                id = 14L,
                shortName = "WEB",
                fullName = SUBJECT_WEB_ENGINEERING,
                date = friday,
                slot = LectureSlot(startHour = 8, startMinute = 0, endHour = 9, endMinute = 30),
                location = "Raum D1.08",
            )
        )
        lectures.add(
            createLecture(
                id = 15L,
                shortName = "DBIS",
                fullName = SUBJECT_DATENBANKEN,
                date = friday,
                slot = LectureSlot(startHour = 10, startMinute = 0, endHour = 11, endMinute = 30),
                location = "Raum C3.12",
            )
        )
        lectures.add(
            createLecture(
                id = 16L,
                shortName = "SWENG",
                fullName = SUBJECT_SOFTWARE_ENGINEERING,
                date = friday,
                slot = LectureSlot(startHour = 11, startMinute = 45, endHour = 13, endMinute = 15),
                location = "Raum A2.15",
                isTest = false
            )
        )

        return lectures
    }

    /**
     * Generate demo lecturers.
     */
    /**
     * The semesters the demo account has studied, newest first — the order Dualis fills its
     * dropdown in.
     *
     * Derived from the current date rather than written down, for the same reason the demo
     * timetable is generated around the current week: a fixture with "WiSe 2024/25" in it looks
     * abandoned two years later, and the demo account is the first thing a new user sees.
     *
     * @param today the date the demo is being looked at, injectable for the tests
     */
    fun demoSemesters(today: LocalDate = TimeHelper.now().date): List<Semester> {
        val current = semesterOf(today)
        return listOf(current, current.previous(), current.previous().previous())
            .mapIndexed { index, term ->
                // Index 0 is the current semester, so the id counts backwards: the oldest of the
                // three is the student's first.
                Semester(id = "$DEMO_SEMESTER_ID_PREFIX${DEMO_SEMESTER_COUNT - index}", name = term.displayName)
            }
    }

    /**
     * The grades of one demo semester, or an empty list for a semester the demo does not have.
     *
     * Three semesters of a dual computer science course: two finished, the current one still
     * running. The mix is deliberate — numeric grades, a "b" for a module that is only ever
     * passed or failed, and modules with no grade yet — because those are the three cases the
     * grades screen and the average have to survive, and a fixture where every module has a
     * number tests none of them.
     *
     * The current semester holds the modules the demo timetable teaches, so the two screens
     * describe the same student.
     */
    fun demoGrades(semester: Semester, studentId: String): List<GradeEntity> {
        val modules = when (semester.id) {
            "${DEMO_SEMESTER_ID_PREFIX}1" -> firstSemesterModules
            "${DEMO_SEMESTER_ID_PREFIX}2" -> secondSemesterModules
            "${DEMO_SEMESTER_ID_PREFIX}3" -> currentSemesterModules
            else -> return emptyList()
        }

        return modules.map { module ->
            GradeEntity(
                studentId = studentId,
                semesterId = semester.id,
                semesterName = semester.name,
                moduleNumber = module.number,
                moduleName = module.name,
                grade = module.grade,
                credits = module.credits,
                // Dualis writes the German words, and the app shows the column as it comes.
                status = if (module.grade == null) "offen" else "bestanden",
                // The demo student can open the details of any module that has a grade.
                resultId = module.grade?.let { "$DEMO_RESULT_ID_PREFIX${module.number}" }
            )
        }
    }

    /**
     * The exam breakdown behind a demo module.
     *
     * Two Bausteine for Mathematik II, one for everything else — the demo has to show the case
     * the feature exists for, a module grade that is two exam grades together.
     */
    fun demoModuleDetails(resultId: String): ModuleResultDetails? {
        val moduleNumber = resultId.removePrefix(DEMO_RESULT_ID_PREFIX)
        val semester = demoSemesters().firstOrNull { semester ->
            demoGrades(semester, studentId = "demo").any { it.moduleNumber == moduleNumber }
        } ?: return null

        val module = demoGrades(semester, studentId = "demo")
            .firstOrNull { it.moduleNumber == moduleNumber } ?: return null

        val exams = if (moduleNumber == "T3INF2001") {
            listOf(
                ExamResult("$moduleNumber.1 Analysis", semester.name, "Klausur", 100.0, null, "1,7"),
                ExamResult("$moduleNumber.2 Lineare Algebra", semester.name, "Klausur", 100.0, null, "2,3")
            )
        } else {
            listOf(
                ExamResult("Modulabschlussleistungen", semester.name, "Klausur", 100.0, null, module.grade)
            )
        }

        return ModuleResultDetails(
            moduleNumber = moduleNumber,
            moduleName = module.moduleName,
            semesterName = semester.name,
            attempts = listOf(
                ModuleAttempt(
                    number = 1,
                    exams = exams,
                    result = module.grade?.let { "$it bestanden" }
                )
            ),
            units = exams.mapIndexed { index, exam ->
                ModuleUnit(
                    number = "$moduleNumber.${index + 1}",
                    name = exam.unitName.orEmpty(),
                    event = exam.unitName.orEmpty(),
                    attended = true
                )
            }
        )
    }

    /** Marks a demo id as one, so a real Dualis id can never be confused with it. */
    private const val DEMO_RESULT_ID_PREFIX = "demo-result-"

    // Subject/room names that appear both in the generated timetable and the grade fixtures below.
    private const val SUBJECT_PROGRAMMIERUNG_1 = "Programmierung 1"
    private const val SUBJECT_DATENBANKEN = "Datenbanken und Informationssysteme"
    private const val SUBJECT_SOFTWARE_ENGINEERING = "Software Engineering"
    private const val SUBJECT_WEB_ENGINEERING = "Web Engineering"
    private const val ROOM_A101 = "Raum A1.01"

    /** One row of the grade table, before it knows which semester or student it belongs to. */
    private data class DemoModule(
        val number: String,
        val name: String,
        val grade: String?,
        val credits: Double
    )

    private val firstSemesterModules = listOf(
        DemoModule("T3INF1001", "Mathematik I", "1,7", 6.0),
        DemoModule("T3INF1002", SUBJECT_PROGRAMMIERUNG_1, "1,3", 8.0),
        DemoModule("T3INF1003", "Theoretische Informatik I", "2,3", 5.0),
        DemoModule("T3INF1004", "Grundlagen der Informatik", "2,0", 5.0),
        // Passed or not passed, never a number — the case that makes numericGrade null on a
        // module that is nonetheless finished and whose credits count.
        DemoModule("T3INF1900", "Praxisprojekt I", "b", 6.0)
    )

    private val secondSemesterModules = listOf(
        DemoModule("T3INF2001", "Mathematik II", "2,0", 6.0),
        DemoModule("T3INF2002", "Algorithmen und Datenstrukturen", "1,7", 6.0),
        DemoModule("T3INF2003", "Programmierung 2", "1,3", 6.0),
        DemoModule("T3INF2004", "Technische Informatik", "2,7", 5.0),
        DemoModule("T3INF2005", "Betriebswirtschaftslehre", "2,3", 4.0),
        DemoModule("T3INF2900", "Praxisprojekt II", "b", 8.0)
    )

    private val currentSemesterModules = listOf(
        DemoModule("T3INF3001", SUBJECT_SOFTWARE_ENGINEERING, "1,7", 5.0),
        DemoModule("T3INF3002", "Projektmanagement", "2,0", 4.0),
        DemoModule("T3INF3003", SUBJECT_WEB_ENGINEERING, null, 5.0),
        DemoModule("T3INF3004", SUBJECT_DATENBANKEN, null, 6.0),
        DemoModule("T3INF3005", "Theoretische Informatik II", null, 5.0),
        DemoModule("T3INF3006", "Netzwerktechnik", null, 5.0)
    )

    private const val DEMO_SEMESTER_ID_PREFIX = "demo-semester-"
    private const val DEMO_SEMESTER_COUNT = 3

    /** A term as Dualis names it: "WiSe 2025/26" or "SoSe 2026". */
    private data class Term(val startYear: Int, val isWinter: Boolean) {
        val displayName: String
            get() = if (isWinter) {
                // Two digits, so 2099/2100 reads "2099/00" the way Dualis writes it.
                val endYear = ((startYear + 1) % 100).toString().padStart(2, '0')
                "WiSe $startYear/$endYear"
            } else {
                "SoSe $startYear"
            }

        /** The term before this one. Winter starts the academic year, so it follows that summer. */
        fun previous(): Term =
            if (isWinter) Term(startYear, isWinter = false) else Term(startYear - 1, isWinter = true)
    }

    /**
     * The term [date] falls into: the summer one from March to August, the winter one otherwise —
     * and a winter term is named after the year it *starts* in, so January belongs to the term
     * that began the previous autumn.
     */
    private fun semesterOf(date: LocalDate): Term {
        val month = date.month.number
        return when {
            month in 3..8 -> Term(date.year, isWinter = false)
            month >= 9 -> Term(date.year, isWinter = true)
            else -> Term(date.year - 1, isWinter = true)
        }
    }

    /**
     * The documents the demo account shows.
     *
     * Each of them downloads: [demoDocumentContent] renders the PDF the viewer opens, so the
     * demo goes all the way through the list-open-save flow rather than stopping at a message.
     */
    fun demoDocuments(today: LocalDate = TimeHelper.now().date): List<DualisDocument> = listOf(
        DualisDocument(
            title = "Studienbescheinigung",
            // Dated relative to today for the same reason the timetable is: a demo whose newest
            // document is from two years ago looks like a broken account, not like a preview.
            date = today.minusDays(12).asDualisDate(),
            time = "09:40",
            downloadUrl = DEMO_DOCUMENT_CERTIFICATE
        ),
        DualisDocument(
            title = "Zahlungsinformation Semesterbeiträge",
            date = today.minusDays(34).asDualisDate(),
            time = "14:47",
            downloadUrl = DEMO_DOCUMENT_PAYMENT
        ),
        DualisDocument(
            title = "Semesternotenbescheid - Download",
            date = today.minusDays(61).asDualisDate(),
            time = "15:52",
            downloadUrl = DEMO_DOCUMENT_GRADES
        )
    )

    /**
     * The file behind a demo document, or null for a URL that is not one of them.
     *
     * A real PDF rather than a placeholder: the download path ends in the platform's own viewer
     * and save dialog, and handing those an empty array or a text file is how the demo would
     * "work" everywhere except on a device.
     *
     * Keyed by [downloadUrl] because that is all Dualis gives a caller to ask for a file with,
     * and the demo behaves the same way.
     */
    fun demoDocumentContent(
        downloadUrl: String,
        today: LocalDate = TimeHelper.now().date
    ): ByteArray? {
        val document = demoDocuments(today).find { it.downloadUrl == downloadUrl } ?: return null
        val issued = "${document.date}, ${document.time} Uhr"
        val student = "Max Mustermann, Matrikelnummer 1234567"

        return when (document.downloadUrl) {
            DEMO_DOCUMENT_CERTIFICATE -> DemoPdf.render(
                title = "Studienbescheinigung",
                lines = listOf(
                    "Duale Hochschule Baden-Württemberg",
                    "Studiengang Informatik (T3INF)",
                    "",
                    student,
                    "Semester: ${demoSemesters(today).first().name}",
                    "Ausgestellt am $issued",
                    "",
                    "Hiermit wird bescheinigt, dass die oben genannte Person im",
                    "laufenden Semester ordentlich immatrikuliert ist.",
                    "",
                    DEMO_FOOTER
                )
            )

            DEMO_DOCUMENT_PAYMENT -> DemoPdf.render(
                title = "Zahlungsinformation Semesterbeiträge",
                lines = listOf(
                    student,
                    "Semester: ${demoSemesters(today).first().name}",
                    "Ausgestellt am $issued",
                    "",
                    "Verwaltungskostenbeitrag         70,00 EUR",
                    "Studierendenwerksbeitrag         89,00 EUR",
                    "Studierendenschaftsbeitrag        9,00 EUR",
                    "-----------------------------------------",
                    "Gesamtbetrag                    168,00 EUR",
                    "",
                    "Der Betrag wurde vollständig verbucht.",
                    "",
                    DEMO_FOOTER
                )
            )

            DEMO_DOCUMENT_GRADES -> {
                val semester = demoSemesters(today)[1]
                DemoPdf.render(
                    title = "Semesternotenbescheid",
                    lines = listOf(
                        student,
                        "Semester: ${semester.name}",
                        "Ausgestellt am $issued",
                        ""
                    ) + demoGrades(semester, studentId = student).map { grade ->
                        // Padded into columns; the page is set in Courier so they line up.
                        "${grade.moduleNumber}  ${grade.moduleName.padEnd(36).take(36)}" +
                            "${(grade.grade ?: "-").padStart(4)}   " +
                            "${grade.credits.toString().replace('.', ',')} ECTS"
                    } + listOf("", DEMO_FOOTER),
                    monospacedBody = true
                )
            }

            else -> null
        }
    }

    private const val DEMO_DOCUMENT_CERTIFICATE = "/scripts/filetransfer.exe?demo_cert"
    private const val DEMO_DOCUMENT_PAYMENT = "/scripts/filetransfer.exe?demo_payment"
    private const val DEMO_DOCUMENT_GRADES = "/scripts/filetransfer.exe?demo_grades"

    private const val DEMO_FOOTER =
        "Beispieldokument des Demo-Kontos - keine gültige Bescheinigung."

    /** Dualis writes dates as `dd.MM.yy`. */
    private fun LocalDate.asDualisDate(): String =
        "${day.toString().padStart(2, '0')}.${month.number.toString().padStart(2, '0')}." +
            "${(year % 100).toString().padStart(2, '0')}"

    private fun LocalDate.minusDays(days: Int): LocalDate = plus(-days, DateTimeUnit.DAY)

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

    /** The start and end clock time of a lecture slot, bundled so createLecture stays under the
     * parameter-count limit. */
    private data class LectureSlot(val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int)

    /**
     * Helper function to create a lecture event.
     */
    @OptIn(ExperimentalTime::class)
    private fun createLecture(
        id: Long,
        shortName: String,
        fullName: String,
        date: kotlinx.datetime.LocalDate,
        slot: LectureSlot,
        location: String,
        isTest: Boolean = false
    ): LectureEventEntity {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        return LectureEventEntity(
            lectureId = id,
            shortSubjectName = shortName,
            fullSubjectName = fullName,
            startTime = LocalDateTime(date.year, date.month, date.day, slot.startHour, slot.startMinute, 0),
            endTime = LocalDateTime(date.year, date.month, date.day, slot.endHour, slot.endMinute, 0),
            location = location,
            isTest = isTest,
            fetchedAt = now
        )
    }
}

