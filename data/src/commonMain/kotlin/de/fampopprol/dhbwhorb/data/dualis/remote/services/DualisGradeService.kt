/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.core.error.map
import de.fampopprol.dhbwhorb.data.dualis.demo.DemoDataProvider
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.GradeParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.ModuleDetailsParser
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeCacheMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeCacheMetadata
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.domain.model.ModuleResultDetails
import de.fampopprol.dhbwhorb.domain.model.Semester
import io.github.aakira.napier.Napier
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Reads semesters and grades from Dualis and keeps them in the local cache.
 *
 * Fetch, validate and re-authenticate are [DualisPageGateway]'s job; what is left here is the URL
 * for each page, the parsing, and the cache.
 */
class DualisGradeService(
    private val gateway: DualisPageGateway,
    private val sessionManager: SessionManager,
    private val gradeDao: GradeDao,
    private val gradeCacheMetadataDao: GradeCacheMetadataDao,
    private val gradeParser: GradeParser = GradeParser(),
    private val moduleDetailsParser: ModuleDetailsParser = ModuleDetailsParser(),
    private val htmlParser: HtmlParser = HtmlParser()
) {
    companion object {
        private const val TAG = "DualisGradeService"
        private const val BASE_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val MODULE_DETAILS_SOURCE = "module details"

        /** Grades change rarely; an hour old is fresh enough to skip the network. */
        private const val CACHE_VALIDITY_DURATION_MS = 60 * 60 * 1000L
    }

    /** The semesters Dualis lists in its dropdown. */
    suspend fun getSemesters(): Outcome<List<Semester>> {
        if (sessionManager.isDemoMode()) {
            Napier.d("Returning demo semesters", tag = TAG)
            return Outcome.Ok(DemoDataProvider.demoSemesters())
        }

        val html = gateway.fetchPage(
            source = "semesters",
            isValid = { htmlParser.isValidGradePage(it) },
            buildUrl = { auth -> "$BASE_URL?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N${auth.sessionId},-N000307," }
        )

        return html.map { content ->
            gradeParser.parseSemesterList(content).map { (name, id) -> Semester(id = id, name = name) }
        }
    }

    /**
     * The grades of one semester, from cache when it is fresh and [forceRefresh] is not set.
     */
    suspend fun getGradesForSemester(
        semester: Semester,
        forceRefresh: Boolean = false
    ): Outcome<List<GradeEntity>> {
        val studentId = currentStudentId()

        if (sessionManager.isDemoMode()) {
            Napier.d("Returning demo grades for semester ${semester.id}", tag = TAG)
            return Outcome.Ok(DemoDataProvider.demoGrades(semester, studentId))
        }

        if (!forceRefresh) {
            when (val cached = readValidCache(studentId, semester.id)) {
                is Outcome.Ok -> cached.value?.let { return Outcome.Ok(it) }
                // A broken cache is not a reason to refuse the screen — fall through to the
                // network, which is what the user wanted anyway.
                is Outcome.Err -> Napier.w("Ignoring unusable grade cache: ${cached.error}", tag = TAG)
            }
        }

        Napier.d("Fetching grades from network for semester ${semester.id}", tag = TAG)
        val html = gateway.fetchPage(
            source = "grades",
            isValid = { htmlParser.isValidGradePage(it) },
            buildUrl = { auth -> "$BASE_URL?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N${auth.sessionId},-N000307,-N${semester.id}" }
        )

        val grades = when (html) {
            is Outcome.Ok -> gradeParser.parseGrades(html.value, studentId, semester.id, semester.name)
            is Outcome.Err -> return html
        }

        // A failed cache write must not fail the request: the grades are already in hand.
        cacheGrades(grades, studentId, semester.id)
        return Outcome.Ok(grades)
    }

    /**
     * The exams behind one module result — Dualis' "Ergebnisdetails" pop-up.
     *
     * Not cached: it is opened for one module at a time, on demand, and a stale attempt list is
     * worse than a short wait. The menu argument is the grade page's (`-N000307`), because that
     * is the page the id was read from.
     */
    suspend fun getModuleDetails(resultId: String): Outcome<ModuleResultDetails> {
        if (sessionManager.isDemoMode()) {
            return DemoDataProvider.demoModuleDetails(resultId)
                ?.let { Outcome.Ok(it) }
                ?: Outcome.Err(AppError.Parse(MODULE_DETAILS_SOURCE, "no result recorded for $resultId"))
        }

        val html = gateway.fetchPage(
            source = MODULE_DETAILS_SOURCE,
            isValid = { htmlParser.isValidModuleDetailsPage(it) },
            buildUrl = { auth -> "$BASE_URL?APPNAME=CampusNet&PRGNAME=RESULTDETAILS&ARGUMENTS=-N${auth.sessionId},-N000307,-N$resultId" }
        )

        return when (html) {
            is Outcome.Ok -> moduleDetailsParser.parse(html.value)
                ?.let { Outcome.Ok(it) }
                ?: Outcome.Err(AppError.Parse(MODULE_DETAILS_SOURCE, "the page carried no attempt table"))
            is Outcome.Err -> html
        }
    }

    /** The stored username doubles as the student id; it is stable and never leaves the device. */
    private fun currentStudentId(): String =
        sessionManager.getStoredCredentials()?.first ?: "unknown"

    /**
     * @return `Ok(null)` when there is no fresh cache — a normal state, not a failure.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun readValidCache(studentId: String, semesterId: String): Outcome<List<GradeEntity>?> {
        return try {
            val metadata = gradeCacheMetadataDao.getMetadata(studentId, semesterId)
                ?: return Outcome.Ok(null)

            val age = Clock.System.now().toEpochMilliseconds() - metadata.lastUpdatedTimestamp
            if (age >= CACHE_VALIDITY_DURATION_MS) return Outcome.Ok(null)

            val cached = gradeDao.getGradesForSemester(studentId, semesterId)
            if (cached.isEmpty()) {
                Outcome.Ok(null)
            } else {
                Napier.d("Serving ${cached.size} cached grades for $semesterId", tag = TAG)
                Outcome.Ok(cached)
            }
        } catch (e: Exception) {
            Outcome.Err(AppError.Storage("reading the grade cache: ${e.message}"))
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun cacheGrades(grades: List<GradeEntity>, studentId: String, semesterId: String) {
        try {
            // Replace rather than merge, so a module that disappeared from Dualis disappears here.
            gradeDao.deleteGradesForSemester(studentId, semesterId)
            gradeDao.insertAll(grades)

            gradeCacheMetadataDao.insert(
                GradeCacheMetadata(
                    key = "grades_${studentId}_$semesterId",
                    lastUpdatedTimestamp = Clock.System.now().toEpochMilliseconds(),
                    studentId = studentId,
                    semesterId = semesterId
                )
            )
            Napier.d("Cached ${grades.size} grades for $semesterId", tag = TAG)
        } catch (e: Exception) {
            Napier.e("Could not cache grades for $semesterId: ${e.message}", e, tag = TAG)
        }
    }
}
