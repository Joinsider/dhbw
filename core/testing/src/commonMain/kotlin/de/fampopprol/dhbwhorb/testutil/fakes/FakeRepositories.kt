/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.testutil.fakes

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.storage.preferences.ThemeMode
import de.fampopprol.dhbwhorb.domain.model.GradeEntry
import de.fampopprol.dhbwhorb.domain.model.Lecture
import de.fampopprol.dhbwhorb.domain.model.ModuleResultDetails
import de.fampopprol.dhbwhorb.domain.model.Semester
import de.fampopprol.dhbwhorb.domain.model.Session
import de.fampopprol.dhbwhorb.domain.model.TimetableWeek
import de.fampopprol.dhbwhorb.domain.repository.AuthRepository
import de.fampopprol.dhbwhorb.domain.repository.DocumentRepository
import de.fampopprol.dhbwhorb.domain.repository.GradeRepository
import de.fampopprol.dhbwhorb.domain.repository.PreferencesRepository
import de.fampopprol.dhbwhorb.domain.repository.SessionRepository
import de.fampopprol.dhbwhorb.domain.repository.TimetableRepository
import de.fampopprol.dhbwhorb.presentation.settings.SettingsState
import kotlinx.datetime.LocalDateTime

/**
 * Hand-written fakes for the six repository interfaces.
 *
 * They exist because the interfaces landed in P3: a store's effect handler can be tested against
 * four methods that return a value someone set, with no mocking framework, no network and no
 * database. Each one records what it was asked for, so a test can assert that a refresh really
 * bypassed the cache.
 *
 * The plan puts these in a `:core:testing` module eventually. They stay here while every test
 * still lives in `:composeApp`; moving them is part of the same job as moving the tests.
 */

class FakeTimetableRepository(
    var week: Outcome<TimetableWeek> = Outcome.Ok(emptyWeek(0)),
    var fullWeek: Outcome<TimetableWeek>? = null,
    var refreshed: Outcome<TimetableWeek>? = null,
    var cached: Outcome<List<Lecture>> = Outcome.Ok(emptyList())
) : TimetableRepository {

    val requestedWeeks = mutableListOf<Int>()
    val awaitedWeeks = mutableListOf<Int>()
    val refreshedWeeks = mutableListOf<Int>()

    override suspend fun getWeek(weekOffset: Int): Outcome<TimetableWeek> {
        requestedWeeks += weekOffset
        return week
    }

    override suspend fun awaitFullWeek(weekOffset: Int): Outcome<TimetableWeek> {
        awaitedWeeks += weekOffset
        return fullWeek ?: week
    }

    override suspend fun refreshWeek(weekOffset: Int): Outcome<TimetableWeek> {
        refreshedWeeks += weekOffset
        return refreshed ?: week
    }

    override suspend fun getCachedLectures(
        start: LocalDateTime,
        end: LocalDateTime
    ): Outcome<List<Lecture>> = cached

    companion object {
        fun emptyWeek(offset: Int) = TimetableWeek(
            weekOffset = offset,
            start = LocalDateTime(2026, 3, 2, 0, 0),
            end = LocalDateTime(2026, 3, 8, 23, 59),
            lectures = emptyList()
        )
    }
}

class FakeGradeRepository(
    var semesters: Outcome<List<Semester>> = Outcome.Ok(emptyList()),
    var grades: Outcome<List<GradeEntry>> = Outcome.Ok(emptyList()),
    /** Per-semester answers, for tests about ordering; [grades] answers anything not listed. */
    var gradesBySemester: Map<String, List<GradeEntry>> = emptyMap(),
    var moduleDetails: Outcome<ModuleResultDetails> = Outcome.Err(AppError.Unexpected("no details configured"))
) : GradeRepository {

    /** Every result id the details were asked for, in order. */
    val detailRequests = mutableListOf<String>()

    /** Every (semester id, forceRefresh) pair this was asked for, in order. */
    val requests = mutableListOf<Pair<String, Boolean>>()

    override suspend fun getSemesters(): Outcome<List<Semester>> = semesters

    override suspend fun getGrades(
        semester: Semester,
        forceRefresh: Boolean
    ): Outcome<List<GradeEntry>> {
        requests += semester.id to forceRefresh
        return gradesBySemester[semester.id]?.let { Outcome.Ok(it) } ?: grades
    }

    override suspend fun getModuleDetails(resultId: String): Outcome<ModuleResultDetails> {
        detailRequests += resultId
        return moduleDetails
    }
}

open class FakeDocumentRepository(
    var documents: Outcome<List<DualisDocument>> = Outcome.Ok(emptyList()),
    var download: Outcome<ByteArray> = Outcome.Ok(ByteArray(0))
) : DocumentRepository {

    val downloaded = mutableListOf<String>()

    open override suspend fun listDocuments(): Outcome<List<DualisDocument>> = documents

    override suspend fun downloadDocument(document: DualisDocument): Outcome<ByteArray> {
        downloaded += document.title
        return download
    }
}

class FakeSessionRepository(
    var session: Session? = null,
    var canAuthenticate: Boolean = true
) : SessionRepository {
    override fun currentSession(): Session? = session
    override fun isLoggedIn(): Boolean = session != null
    override fun canAuthenticate(): Boolean = canAuthenticate
    override fun isDemoMode(): Boolean = session?.isDemo == true
}

class FakeAuthRepository(
    var loginResult: Outcome<Session> = Outcome.Ok(Session(userFullName = "Test User")),
    var logoutResult: Outcome<Unit> = Outcome.Ok(Unit)
) : AuthRepository {

    var loginCount = 0
        private set
    var logoutCount = 0
        private set

    override suspend fun login(username: String, password: String): Outcome<Session> {
        loginCount++
        return loginResult
    }

    override suspend fun reAuthenticate(): Outcome<Session> = loginResult

    override suspend fun logout(): Outcome<Unit> {
        logoutCount++
        return logoutResult
    }
}

class FakePreferencesRepository(
    private var themeMode: ThemeMode = ThemeMode.SYSTEM,
    private var materialYou: Boolean = true,
    private var seedColor: Long = SettingsState.DEFAULT_SEED_COLOR,
    private var notifications: Boolean = false,
    private var lectureAlerts: Boolean = false,
    private var reminderLeadMinutes: Int = 0
) : PreferencesRepository {
    override fun getThemeMode() = themeMode
    override fun setThemeMode(mode: ThemeMode) { themeMode = mode }
    override fun isMaterialYouEnabled() = materialYou
    override fun setMaterialYouEnabled(enabled: Boolean) { materialYou = enabled }
    override fun getCustomColor() = seedColor
    override fun setCustomColor(color: Long) { seedColor = color }
    override fun areNotificationsEnabled() = notifications
    override fun setNotificationsEnabled(enabled: Boolean) { notifications = enabled }
    override fun areLectureAlertsEnabled() = lectureAlerts
    override fun setLectureAlertsEnabled(enabled: Boolean) { lectureAlerts = enabled }
    override fun getReminderLeadMinutes() = reminderLeadMinutes
    override fun setReminderLeadMinutes(minutes: Int) { reminderLeadMinutes = minutes }
}

/** A repository whose every call fails the same way, for the error paths. */
fun offline(): AppError = AppError.Offline
