/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.testutil.MockGradeCacheMetadataDao
import de.fampopprol.dhbwhorb.testutil.MockGradeDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The demo account has its own grades.
 *
 * Before they existed the request walked into the page gateway, found no session id, could not
 * re-authenticate an account that never logs in, and ended as "your session has expired" — advice
 * that leads nowhere for a user who has no session to expire.
 */
class DualisGradeServiceDemoTest {

    private var requests = 0

    private fun Outcome<List<GradeEntity>>.gradesOrFail(): List<GradeEntity> =
        assertIs<Outcome.Ok<List<GradeEntity>>>(this).value

    private fun createService(demo: Boolean): DualisGradeService {
        val sessionManager = SessionManager(FakeSecureStorage())
        sessionManager.setDemoMode(demo)
        sessionManager.storeCredentials(SessionManager.DEMO_EMAIL, SessionManager.DEMO_PASSWORD)

        val client = HttpClient(MockEngine {
            requests++
            respond(ByteReadChannel(""), HttpStatusCode.OK)
        })
        val apiClient = DualisApiClient(client)
        val authService = AuthenticationService(sessionManager, client)

        return DualisGradeService(
            gateway = DualisPageGateway(
                apiClient,
                sessionManager,
                ReAuthenticator(sessionManager, authService)
            ),
            sessionManager = sessionManager,
            gradeDao = MockGradeDao(),
            gradeCacheMetadataDao = MockGradeCacheMetadataDao()
        )
    }

    @Test
    fun demoMode_listsThreeSemestersWithoutTalkingToDualis() = runTest {
        val semesters = assertIs<Outcome.Ok<List<Semester>>>(createService(demo = true).getSemesters()).value

        assertEquals(3, semesters.size)
        assertTrue(semesters.all { it.name.isNotBlank() })
        assertEquals(0, requests, "demo mode must not talk to Dualis")
    }

    @Test
    fun demoMode_hasGradesForEverySemesterItLists() = runTest {
        val service = createService(demo = true)
        val semesters = assertIs<Outcome.Ok<List<Semester>>>(service.getSemesters()).value

        for (semester in semesters) {
            val grades = service.getGradesForSemester(semester).gradesOrFail()
            assertTrue(grades.isNotEmpty(), "${semester.name} has no demo grades")
        }
        assertEquals(0, requests)
    }

    @Test
    fun demoMode_carriesTheCasesTheScreenHasToSurvive() = runTest {
        val service = createService(demo = true)
        val semesters = assertIs<Outcome.Ok<List<Semester>>>(service.getSemesters()).value
        val all = semesters.flatMap { service.getGradesForSemester(it).gradesOrFail() }

        assertTrue(all.any { it.grade?.toDoubleOrNull() == null && it.grade != null }, "a numeric grade uses a comma")
        assertTrue(all.any { it.grade == "b" }, "a module that is only passed or failed")
        assertTrue(all.any { it.grade == null }, "a module that has no grade yet")
        assertTrue(all.all { it.studentId == SessionManager.DEMO_EMAIL })
    }

    @Test
    fun anUnknownSemester_hasNoDemoGrades() = runTest {
        val grades = createService(demo = true)
            .getGradesForSemester(Semester(id = "not-a-demo-semester", name = "WiSe 2020/21"))
            .gradesOrFail()

        assertTrue(grades.isEmpty())
    }
}
