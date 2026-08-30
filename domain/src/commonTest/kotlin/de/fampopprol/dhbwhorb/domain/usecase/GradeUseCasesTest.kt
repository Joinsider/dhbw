/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.domain.usecase

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.ModuleResultDetails
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.repository.GradeRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeGradeRepository(
    var semesters: Outcome<List<Semester>> = Outcome.Ok(emptyList()),
    var gradesBySemester: Map<String, Outcome<List<GradeEntry>>> = emptyMap(),
    var moduleDetails: Outcome<ModuleResultDetails> = Outcome.Err(AppError.Unexpected("no details configured")),
) : GradeRepository {

    val requests = mutableListOf<Pair<String, Boolean>>()
    val detailRequests = mutableListOf<String>()

    override suspend fun getSemesters(): Outcome<List<Semester>> = semesters

    override suspend fun getGrades(semester: Semester, forceRefresh: Boolean): Outcome<List<GradeEntry>> {
        requests += semester.id to forceRefresh
        return gradesBySemester[semester.id] ?: Outcome.Ok(emptyList())
    }

    override suspend fun getModuleDetails(resultId: String): Outcome<ModuleResultDetails> {
        detailRequests += resultId
        return moduleDetails
    }
}

private fun grade(
    semesterId: String = "s1",
    semesterName: String = "WiSe 2025/26",
    moduleNumber: String = "T3INF1001",
    grade: String? = "1,7",
    credits: Double = 5.0,
    status: String? = "bestanden",
) = GradeEntry(
    semesterId = semesterId,
    semesterName = semesterName,
    moduleNumber = moduleNumber,
    moduleName = "Mathematik I",
    grade = grade,
    credits = credits,
    status = status,
)

class GradeUseCasesTest {

    // ── GetSemesters / GetGradesForSemester / GetModuleDetails (thin wrappers) ────────────────

    @Test
    fun getSemesters_delegatesToTheRepository() = runTest {
        val semesters = listOf(Semester(id = "s1", name = "WiSe 2025/26"))
        val repository = FakeGradeRepository(semesters = Outcome.Ok(semesters))

        val result = GetSemesters(repository)()

        assertEquals(Outcome.Ok(semesters), result)
    }

    @Test
    fun getGradesForSemester_delegatesToTheRepository_withForceRefresh() = runTest {
        val semester = Semester(id = "s1", name = "WiSe 2025/26")
        val repository = FakeGradeRepository(gradesBySemester = mapOf("s1" to Outcome.Ok(listOf(grade()))))

        val result = GetGradesForSemester(repository)(semester, forceRefresh = true)

        assertEquals(1, assertIs<Outcome.Ok<List<GradeEntry>>>(result).value.size)
        assertEquals(listOf("s1" to true), repository.requests)
    }

    @Test
    fun getModuleDetails_delegatesToTheRepository() = runTest {
        val details = ModuleResultDetails(
            moduleNumber = "T3INF1001",
            moduleName = "Mathematik I",
            semesterName = "WiSe 2025/26",
            attempts = emptyList(),
            units = emptyList(),
        )
        val repository = FakeGradeRepository(moduleDetails = Outcome.Ok(details))

        val result = GetModuleDetails(repository)("demo-result-1")

        assertEquals(Outcome.Ok(details), result)
        assertEquals(listOf("demo-result-1"), repository.detailRequests)
    }

    // ── GetAllGrades ─────────────────────────────────────────────────────────────────────────

    @Test
    fun getAllGrades_combinesEverySemestersGrades() = runTest {
        val s1 = Semester(id = "s1", name = "WiSe 2025/26")
        val s2 = Semester(id = "s2", name = "SoSe 2025")
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(s1, s2)),
            gradesBySemester = mapOf(
                "s1" to Outcome.Ok(listOf(grade(semesterId = "s1"))),
                "s2" to Outcome.Ok(listOf(grade(semesterId = "s2"), grade(semesterId = "s2", moduleNumber = "T3INF1002"))),
            ),
        )
        val useCase = GetAllGrades(GetSemesters(repository), GetGradesForSemester(repository))

        val result = useCase()

        val grades = assertIs<Outcome.Ok<List<GradeEntry>>>(result).value
        assertEquals(3, grades.size)
    }

    @Test
    fun getAllGrades_withNoSemesters_returnsAnEmptyList() = runTest {
        val repository = FakeGradeRepository(semesters = Outcome.Ok(emptyList()))
        val useCase = GetAllGrades(GetSemesters(repository), GetGradesForSemester(repository))

        val result = useCase()

        assertEquals(Outcome.Ok(emptyList()), result)
    }

    @Test
    fun getAllGrades_whenTheSemesterListFails_propagatesTheError() = runTest {
        val repository = FakeGradeRepository(semesters = Outcome.Err(AppError.Offline))
        val useCase = GetAllGrades(GetSemesters(repository), GetGradesForSemester(repository))

        val result = useCase()

        assertEquals(Outcome.Err(AppError.Offline), result)
    }

    @Test
    fun getAllGrades_withOneSemesterFailing_stillReturnsWhatDidLoad() = runTest {
        val s1 = Semester(id = "s1", name = "WiSe 2025/26")
        val s2 = Semester(id = "s2", name = "SoSe 2025")
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(s1, s2)),
            gradesBySemester = mapOf(
                "s1" to Outcome.Err(AppError.Offline),
                "s2" to Outcome.Ok(listOf(grade(semesterId = "s2"))),
            ),
        )
        val useCase = GetAllGrades(GetSemesters(repository), GetGradesForSemester(repository))

        val result = useCase()

        val grades = assertIs<Outcome.Ok<List<GradeEntry>>>(result).value
        assertEquals(1, grades.size, "the semester that loaded must still come through")
    }

    @Test
    fun getAllGrades_whenEverySemesterFails_reportsTheLastError() = runTest {
        val s1 = Semester(id = "s1", name = "WiSe 2025/26")
        val s2 = Semester(id = "s2", name = "SoSe 2025")
        val repository = FakeGradeRepository(
            semesters = Outcome.Ok(listOf(s1, s2)),
            gradesBySemester = mapOf(
                "s1" to Outcome.Err(AppError.Offline),
                "s2" to Outcome.Err(AppError.SessionExpired),
            ),
        )
        val useCase = GetAllGrades(GetSemesters(repository), GetGradesForSemester(repository))

        val result = useCase()

        assertEquals(Outcome.Err(AppError.SessionExpired), result, "the error from the last attempted semester wins")
    }

    // ── ComputeGpa ───────────────────────────────────────────────────────────────────────────

    @Test
    fun computeGpa_isCreditWeighted() = runTest {
        val grades = listOf(
            grade(moduleNumber = "A", grade = "1,0", credits = 5.0),
            grade(moduleNumber = "B", grade = "3,0", credits = 5.0),
        )

        val gpa = ComputeGpa()(grades)

        assertEquals(2.0, gpa.average) // (1.0*5 + 3.0*5) / 10
        assertEquals(10.0, gpa.earnedCredits)
        assertEquals(2, gpa.completedModules)
    }

    @Test
    fun computeGpa_onNoGrades_hasANullAverage() {
        val gpa = ComputeGpa()(emptyList())

        assertNull(gpa.average)
        assertEquals(0.0, gpa.earnedCredits)
        assertEquals(0, gpa.completedModules)
    }

    @Test
    fun computeGpa_ignoresAFailedAttempt() {
        val grades = listOf(grade(moduleNumber = "A", grade = "5,0", status = "nicht bestanden", credits = 5.0))

        val gpa = ComputeGpa()(grades)

        assertNull(gpa.average, "a failed attempt does not count towards the degree at all")
        assertEquals(0.0, gpa.earnedCredits)
    }

    @Test
    fun computeGpa_countsAnUngradedPass_towardsCreditsButNotTheAverage() {
        // "b" (bestanden, no numeric grade) - e.g. a Praxisprojekt.
        val grades = listOf(grade(moduleNumber = "A", grade = "b", credits = 6.0))

        val gpa = ComputeGpa()(grades)

        assertNull(gpa.average, "no numeric grade to average")
        assertEquals(6.0, gpa.earnedCredits, "but the credits still count")
        assertEquals(1, gpa.completedModules)
    }

    @Test
    fun computeGpa_deduplicatesARepeatedModule_keepingOnlyTheCountingAttempt() {
        val grades = listOf(
            // Failed first attempt in an earlier semester, then passed on retake.
            grade(moduleNumber = "A", semesterId = "s1", semesterName = "SoSe 2025", grade = "5,0", status = "nicht bestanden", credits = 5.0),
            grade(moduleNumber = "A", semesterId = "s2", semesterName = "WiSe 2025/26", grade = "2,3", status = "bestanden (Wh.)", credits = 5.0),
        )

        val gpa = ComputeGpa()(grades)

        assertEquals(2.3, gpa.average)
        assertEquals(1, gpa.completedModules, "the module counts once, not twice")
        assertEquals(5.0, gpa.earnedCredits)
    }

    @Test
    fun computeGpa_ignoresAModuleWithZeroCredits() {
        val grades = listOf(grade(moduleNumber = "A", grade = "1,0", credits = 0.0))

        val gpa = ComputeGpa()(grades)

        assertNull(gpa.average, "zero credits must not contribute to a weighted average")
    }
}
