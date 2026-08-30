/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.demo

import de.fampopprol.dhbwhorb.domain.model.SemesterOrder
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoDataProviderTest {

    private val inWinterTerm = LocalDate(2026, 1, 15)
    private val inSummerTerm = LocalDate(2026, 5, 4)

    @Test
    fun semestersAreNamedAfterTheTermTheyBelongTo() {
        // January belongs to the winter term that started the previous autumn.
        assertEquals("WiSe 2025/26", DemoDataProvider.demoSemesters(inWinterTerm).first().name)
        assertEquals("SoSe 2026", DemoDataProvider.demoSemesters(inSummerTerm).first().name)
    }

    @Test
    fun semestersAreThreeAndInStudiedOrder() {
        val semesters = DemoDataProvider.demoSemesters(inSummerTerm)

        assertEquals(listOf("SoSe 2026", "WiSe 2025/26", "SoSe 2025"), semesters.map { it.name })
        // Newest first, so sorting them oldest-first has to reverse them exactly.
        assertEquals(
            semesters.map { it.name }.reversed(),
            semesters.map { it.name }.sortedWith(SemesterOrder.oldestFirst)
        )
    }

    @Test
    fun everyListedSemesterHasGrades_andTheCurrentOneIsStillRunning() {
        val semesters = DemoDataProvider.demoSemesters(inSummerTerm)
        val current = semesters.first()
        val finished = semesters.last()

        val currentGrades = DemoDataProvider.demoGrades(current, studentId = "demo")
        val finishedGrades = DemoDataProvider.demoGrades(finished, studentId = "demo")

        assertTrue(currentGrades.any { it.grade == null }, "the current semester is not over")
        assertTrue(currentGrades.all { it.semesterName == current.name })
        assertTrue(finishedGrades.isNotEmpty() && finishedGrades.all { it.grade != null })
        assertTrue(finishedGrades.all { it.status == "bestanden" })
    }

    @Test
    fun aSemesterTheDemoDoesNotHave_hasNoGrades() {
        val other = DemoDataProvider.demoSemesters(inSummerTerm).first().copy(id = "42")

        assertEquals(emptyList(), DemoDataProvider.demoGrades(other, studentId = "demo"))
    }

    @Test
    fun everyListedDocumentCanBeDownloaded() {
        for (document in DemoDataProvider.demoDocuments(inSummerTerm)) {
            val content = DemoDataProvider.demoDocumentContent(document.downloadUrl, inSummerTerm)

            assertNotNull(content, "${document.title} has no file behind it")
            assertTrue(content.size > 400, "${document.title} is suspiciously small")
            assertTrue(content.decodeToString().startsWith("%PDF-1.4"), "not a PDF")
        }
    }

    @Test
    fun anUnknownDocumentUrl_hasNoContent() {
        assertNull(DemoDataProvider.demoDocumentContent("/scripts/filetransfer.exe?nope"))
    }

    @Test
    fun documentDatesFollowTheDayTheyAreLookedAt() {
        val documents = DemoDataProvider.demoDocuments(LocalDate(2026, 5, 4))

        // dd.MM.yy, twelve days before the given date.
        assertEquals("22.04.26", documents.first().date)
    }

    @Test
    fun weeklyTimetable_hasSixteenLecturesAcrossMondayToFriday() {
        // A Monday, so the "start of week" resolution is a no-op.
        val monday = LocalDateTime(2026, 5, 4, 0, 0)
        val lectures = DemoDataProvider.generateDemoLecturesForWeek(monday)

        assertEquals(16, lectures.size)
        // Every lecture's id is unique and every date falls within the Monday-Friday week.
        assertEquals(lectures.map { it.lectureId }.toSet().size, lectures.size)
        val fridayDate = monday.date.plus(4, DateTimeUnit.DAY)
        assertTrue(lectures.all { it.startTime.date >= monday.date && it.startTime.date <= fridayDate })
        assertTrue(lectures.all { it.endTime > it.startTime })
    }

    @Test
    fun weeklyTimetable_resolvesToTheSameMondayRegardlessOfWhichDayIsPassedIn() {
        // A Wednesday in the same week as the Monday above.
        val wednesday = LocalDateTime(2026, 5, 6, 13, 0)
        val fromMonday = DemoDataProvider.generateDemoLecturesForWeek(LocalDateTime(2026, 5, 4, 0, 0))
        val fromWednesday = DemoDataProvider.generateDemoLecturesForWeek(wednesday)

        assertEquals(
            fromMonday.map { it.startTime },
            fromWednesday.map { it.startTime },
            "Any day of the week must resolve back to the same Monday"
        )
    }

    @Test
    fun weeklyTimetable_resolvesCorrectlyFromASunday() {
        // Sunday is ISO day 7, the "currentDayOfWeek == 1" branch's opposite extreme.
        val sunday = LocalDateTime(2026, 5, 10, 8, 0)
        val fromMonday = DemoDataProvider.generateDemoLecturesForWeek(LocalDateTime(2026, 5, 4, 0, 0))
        val fromSunday = DemoDataProvider.generateDemoLecturesForWeek(sunday)

        assertEquals(fromMonday.map { it.startTime }, fromSunday.map { it.startTime })
    }

    @Test
    fun demoLecturers_areTenNamedProfessorsWithContactDetails() {
        val lecturers = DemoDataProvider.generateDemoLecturers()

        assertEquals(10, lecturers.size)
        assertTrue(lecturers.all { it.lecturerName.startsWith("Prof. Dr.") })
        assertTrue(lecturers.all { !it.lecturerEmail.isNullOrBlank() })
        assertTrue(lecturers.all { !it.lecturerPhoneNumber.isNullOrBlank() })
        assertEquals(lecturers.map { it.lecturerId }.toSet().size, lecturers.size)
    }

    @Test
    fun lecturerIdsForLecture_mapKnownLecturesAndFallBackForUnknownOnes() {
        assertEquals(listOf(1L), DemoDataProvider.getLecturerIdsForLecture(1L))
        assertEquals(listOf(1L), DemoDataProvider.getLecturerIdsForLecture(8L))
        assertEquals(listOf(10L), DemoDataProvider.getLecturerIdsForLecture(13L))
        assertEquals(emptyList(), DemoDataProvider.getLecturerIdsForLecture(9999L))
    }

    @Test
    fun moduleDetails_forAModuleWithASingleExam_hasOneUnitAndOneAttempt() {
        val currentSemester = DemoDataProvider.demoSemesters(inSummerTerm).first()
        val grade = DemoDataProvider.demoGrades(currentSemester, studentId = "demo")
            .first { it.moduleNumber == "T3INF3001" }

        val details = DemoDataProvider.demoModuleDetails(requireNotNull(grade.resultId))

        assertNotNull(details)
        assertEquals("T3INF3001", details.moduleNumber)
        assertEquals("Software Engineering", details.moduleName)
        assertEquals(1, details.attempts.size)
        assertEquals(1, details.units.size)
        assertEquals("1,7 bestanden", details.attempts.first().result)
    }

    @Test
    fun moduleDetails_forMathematikII_splitsIntoTwoBausteine() {
        // Mathematik II (T3INF2001) is the one module the demo renders as two exam components.
        val secondSemester = DemoDataProvider.demoSemesters(inSummerTerm)
            .first { it.name == "WiSe 2025/26" }
        val grade = DemoDataProvider.demoGrades(secondSemester, studentId = "demo")
            .first { it.moduleNumber == "T3INF2001" }

        val details = DemoDataProvider.demoModuleDetails(requireNotNull(grade.resultId))

        assertNotNull(details)
        assertEquals(2, details.units.size)
        assertEquals(2, details.attempts.first().exams.size)
        assertTrue(details.units[0].name.contains("Analysis"))
        assertTrue(details.units[1].name.contains("Lineare Algebra"))
    }

    @Test
    fun moduleDetails_forAnUnknownResultId_isNull() {
        assertNull(DemoDataProvider.demoModuleDetails("demo-result-DOES-NOT-EXIST"))
    }

    @Test
    fun documentContent_forEachDocumentType_carriesItsExpectedTitle() {
        val today = inSummerTerm
        val documents = DemoDataProvider.demoDocuments(today)

        val certificate = requireNotNull(
            DemoDataProvider.demoDocumentContent(documents[0].downloadUrl, today)
        ).decodeToString()
        assertTrue(certificate.contains("Studienbescheinigung"))

        val payment = requireNotNull(
            DemoDataProvider.demoDocumentContent(documents[1].downloadUrl, today)
        ).decodeToString()
        assertTrue(payment.contains("Gesamtbetrag"))

        val grades = requireNotNull(
            DemoDataProvider.demoDocumentContent(documents[2].downloadUrl, today)
        ).decodeToString()
        assertTrue(grades.contains("Semesternotenbescheid"))
        // The grades document lists the *previous* semester's modules (index 1), e.g. Mathematik II.
        assertTrue(grades.contains("T3INF2001"))
    }
}
