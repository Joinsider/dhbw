/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.repository

import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisGradeService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisPageGateway
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
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
 * [GradeRepositoryImpl] as a thin adapter over [DualisGradeService]: it only maps entities to
 * domain models, so exercising it through the demo account (no network, no real database) is
 * enough to prove the wiring is right.
 */
class GradeRepositoryImplTest {

    private fun createRepository(): GradeRepositoryImpl {
        val sessionManager = SessionManager(FakeSecureStorage())
        sessionManager.setDemoMode(true)
        sessionManager.storeCredentials(SessionManager.DEMO_EMAIL, SessionManager.DEMO_PASSWORD)

        val client = HttpClient(MockEngine { respond(ByteReadChannel(""), HttpStatusCode.OK) })
        val apiClient = DualisApiClient(client)
        val authService = AuthenticationService(sessionManager, client)

        val gradeService = DualisGradeService(
            gateway = DualisPageGateway(apiClient, sessionManager, ReAuthenticator(sessionManager, authService)),
            sessionManager = sessionManager,
            gradeDao = MockGradeDao(),
            gradeCacheMetadataDao = MockGradeCacheMetadataDao()
        )

        return GradeRepositoryImpl(gradeService)
    }

    @Test
    fun getSemesters_delegatesToTheService() = runTest {
        val result = createRepository().getSemesters()

        assertTrue(assertIs<Outcome.Ok<List<Semester>>>(result).value.isNotEmpty())
    }

    @Test
    fun getGrades_mapsEveryEntityToADomainGradeEntry() = runTest {
        val repository = createRepository()
        val semester = assertIs<Outcome.Ok<List<Semester>>>(repository.getSemesters()).value.first()

        val result = repository.getGrades(semester, forceRefresh = false)

        val grades = assertIs<Outcome.Ok<List<GradeEntry>>>(result).value
        assertTrue(grades.isNotEmpty())
        assertTrue(grades.all { it.semesterName == semester.name })
    }

    @Test
    fun getModuleDetails_delegatesToTheService() = runTest {
        val repository = createRepository()
        val semester = assertIs<Outcome.Ok<List<Semester>>>(repository.getSemesters()).value.first()
        val grades = assertIs<Outcome.Ok<List<GradeEntry>>>(repository.getGrades(semester, false)).value
        val resultId = grades.mapNotNull { it.resultId }.first()

        val result = repository.getModuleDetails(resultId)

        assertIs<Outcome.Ok<*>>(result)
    }
}
