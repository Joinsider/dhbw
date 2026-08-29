/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.demo.DemoDataProvider
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.TimetableParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.temp_models.TempLectureModel
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.helpers.TimeHelper
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LecturerDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import io.github.aakira.napier.Napier
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Fetches the timetable from Dualis and turns it into lecture entities.
 *
 * Two levels of detail: the weekly grid alone (the *skeleton*, one request) and the grid plus one
 * request per lecture for its full name, lecturers and rooms. The skeleton exists so a week with
 * a cold cache shows something immediately.
 *
 * Which of the two to use, and when to persist, is decided one layer up in
 * [de.fampopprol.dhbwhorb.data.repository.TimetableRepositoryImpl].
 */
open class DualisLectureService(
    private val apiClient: DualisApiClient,
    private val sessionManager: SessionManager,
    private val gateway: DualisPageGateway,
    private val lectureEventDao: LectureEventDao,
    private val lecturerDao: LecturerDao,
    private val lectureLecturerCrossRefDao: LectureLecturerCrossRefDao,
    private val timetableParser: TimetableParser = TimetableParser(),
    private val htmlParser: HtmlParser = HtmlParser()
) {
    companion object {
        private const val TAG = "DualisLectureService"
        private const val BASE_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val SOURCE = "timetable"

        /**
         * How far back the cache keeps lectures. Two months covers "what did I have in the exam
         * week" and nothing beyond it — the timetable of a past week answers no question the app
         * asks, and the table used to keep every week it had ever seen.
         */
        private const val CACHE_RETENTION_DAYS = 60
    }

    /**
     * The full week containing [date], enriched from each lecture's own page.
     *
     * Nothing is written to the database here — [saveLecturesToDatabase] does that, so the caller
     * can compare old and new before replacing anything.
     */
    open suspend fun getWeeklyLecturesForDate(date: LocalDate): Outcome<List<LectureEventEntity>> {
        if (sessionManager.isDemoMode()) return demoWeek(date, persist = true)

        val html = when (val page = fetchTimetablePage(date)) {
            is Outcome.Ok -> page.value
            is Outcome.Err -> return page
        }

        // The requested week's Monday is what tells the parser which year its "Mo 05.01." headers
        // belong to; without it every week outside the current year came back misdated.
        val tempLectures = timetableParser.parseWeeklyView(html, weekStart = date)
        Napier.d("Parsed ${tempLectures.size} lectures from the weekly view", tag = TAG)

        return Outcome.Ok(enrichLecturesInMemory(tempLectures))
    }

    /** The week containing [start], as above. */
    open suspend fun getWeeklyLecturesForWeek(
        start: LocalDateTime,
        end: LocalDateTime
    ): Outcome<List<LectureEventEntity>> = getWeeklyLecturesForDate(start.date)

    /**
     * The weekly grid only — one request, no lecturers and no full course names.
     *
     * Never persisted: a skeleton lecture would overwrite the complete one already in the cache.
     */
    open suspend fun getWeeklySkeletonForWeek(
        start: LocalDateTime,
        end: LocalDateTime
    ): Outcome<List<LectureEventEntity>> = getWeeklySkeletonForDate(start.date)

    protected open suspend fun getWeeklySkeletonForDate(date: LocalDate): Outcome<List<LectureEventEntity>> {
        if (sessionManager.isDemoMode()) return demoWeek(date, persist = false)

        val html = when (val page = fetchTimetablePage(date)) {
            is Outcome.Ok -> page.value
            is Outcome.Err -> return page
        }

        val tempLectures = timetableParser.parseWeeklyView(html, weekStart = date)
        return Outcome.Ok(tempLecturesToBasicEntities(tempLectures))
    }

    private suspend fun fetchTimetablePage(date: LocalDate): Outcome<String> {
        // Dualis wants the date in German notation and answers with the whole week around it.
        val dateString = "${date.day.toString().padStart(2, '0')}." +
            "${date.month.number.toString().padStart(2, '0')}.${date.year}"

        return gateway.fetchPage(
            source = SOURCE,
            isValid = { htmlParser.isValidTimetablePage(it) },
            buildUrl = { auth ->
                "$BASE_URL?APPNAME=CampusNet&PRGNAME=SCHEDULER" +
                    "&ARGUMENTS=-N${auth.sessionId},-N000028,-A$dateString,-A,-N1,-N000000000000000"
            }
        )
    }

    /**
     * Demo data for the week containing [date].
     *
     * @param persist demo lectures are seeded into the database once so the widget and the change
     *   monitor have something to read; the skeleton path skips that.
     */
    private suspend fun demoWeek(date: LocalDate, persist: Boolean): Outcome<List<LectureEventEntity>> {
        Napier.d("Demo mode active, returning demo lectures", tag = TAG)
        val weekStart = LocalDateTime(date.year, date.month, date.day, 0, 0, 0)
        val demoLectures = DemoDataProvider.generateDemoLecturesForWeek(weekStart)
        if (persist) seedDemoData(demoLectures)
        return Outcome.Ok(demoLectures)
    }

    private suspend fun seedDemoData(demoLectures: List<LectureEventEntity>) {
        try {
            DemoDataProvider.generateDemoLecturers().forEach { lecturer ->
                if (lecturerDao.getById(lecturer.lecturerId) == null) lecturerDao.insert(lecturer)
            }

            demoLectures.forEach { lecture ->
                if (lectureEventDao.getById(lecture.lectureId) != null) return@forEach
                lectureEventDao.insert(lecture)
                DemoDataProvider.getLecturerIdsForLecture(lecture.lectureId).forEach { lecturerId ->
                    lectureLecturerCrossRefDao.insert(
                        LectureLecturerCrossRef(lectureId = lecture.lectureId, lecturerId = lecturerId)
                    )
                }
            }
        } catch (e: Exception) {
            // Demo data is already in hand; failing to cache it costs the widget, not the screen.
            Napier.w("Could not seed demo data: ${e.message}", tag = TAG)
        }
    }

    /**
     * Fetch each lecture's own page and build the entities, without touching the database.
     *
     * A lecture whose detail page cannot be read keeps what the weekly grid gave — a name and a
     * time slot are more useful than dropping the entry.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun enrichLecturesInMemory(
        tempLectures: List<TempLectureModel>
    ): List<LectureEventEntity> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        return tempLectures.map { temp ->
            var fullSubjectName = temp.fullSubjectName
            var lecturers = temp.lecturers ?: emptyList()
            var rooms = listOf(temp.location)

            temp.linkToIndividualPage?.let { link ->
                val details = fetchLectureDetails(link)
                if (details != null) {
                    fullSubjectName = details.first
                    lecturers = details.second
                    rooms = details.third
                } else {
                    Napier.w("No details for ${temp.shortSubjectName}, keeping the grid values", tag = TAG)
                }
            }

            LectureEventEntity(
                lectureId = 0, // assigned on insert
                shortSubjectName = temp.shortSubjectName ?: "Unknown",
                fullSubjectName = fullSubjectName,
                startTime = temp.startTime,
                endTime = temp.endTime,
                location = rooms.joinToString(", "),
                isTest = temp.isTest,
                fetchedAt = now
            ).apply { this.lecturers = lecturers }
        }
    }

    /**
     * Replace the stored lectures between [weekStart] and [weekEnd] with [lectures].
     *
     * Also drops everything that ended more than [CACHE_RETENTION_DAYS] ago. Here rather than in a
     * separate housekeeping task because this is the one function every write path goes through:
     * a pruner somewhere else would be a second thing to remember, and the reason the table grew
     * without bound in the first place is that nobody ever remembered.
     *
     * @return the same lectures, carrying the ids the database assigned
     */
    open suspend fun saveLecturesToDatabase(
        lectures: List<LectureEventEntity>,
        weekStart: LocalDateTime,
        weekEnd: LocalDateTime
    ): Outcome<List<LectureEventEntity>> {
        return try {
            pruneExpiredLectures()

            // Replace rather than merge: a cancelled lecture has to disappear.
            lectureEventDao.deleteInRange(weekStart, weekEnd)

            val saved = lectures.map { lecture ->
                val insertedId = lectureEventDao.insert(lecture)

                lecture.lecturers.orEmpty()
                    .filter { it.isNotBlank() }
                    .forEach { lecturerName ->
                        lectureLecturerCrossRefDao.insert(
                            LectureLecturerCrossRef(
                                lectureId = insertedId,
                                lecturerId = findOrCreateLecturer(lecturerName)
                            )
                        )
                    }

                lecture.copy(lectureId = insertedId).apply { lecturers = lecture.lecturers }
            }

            Napier.d("Saved ${saved.size} lectures for $weekStart..$weekEnd", tag = TAG)
            Outcome.Ok(saved)
        } catch (e: Exception) {
            Napier.e("Could not save lectures: ${e.message}", e, tag = TAG)
            Outcome.Err(AppError.Storage("saving the timetable: ${e.message}"))
        }
    }

    /**
     * Removes lectures that ended more than [CACHE_RETENTION_DAYS] ago.
     *
     * Failing to prune is not worth failing a save over — the cache is merely bigger than it needs
     * to be, and the next write tries again.
     */
    private suspend fun pruneExpiredLectures() {
        try {
            val cutoff = TimeHelper.now().date
                .plus(-CACHE_RETENTION_DAYS, DateTimeUnit.DAY)
                .atTime(0, 0)
            lectureEventDao.deleteEndedBefore(cutoff)
        } catch (e: Exception) {
            Napier.w("Could not prune the timetable cache: ${e.message}", e, tag = TAG)
        }
    }

    /**
     * A lecture's own page: full name, lecturers, rooms.
     *
     * Returns null instead of an [Outcome] on purpose — a missing detail page degrades one entry
     * rather than failing the week, and the caller has nothing else to decide.
     */
    private suspend fun fetchLectureDetails(url: String): Triple<String, List<String>, List<String>>? {
        val urlParameters = parseUrlParameters(url)
        val baseUrl = url.substringBefore("?")
        val cookie = sessionManager.getAuthData()?.cookie?.substringBefore(";")

        return when (val response = apiClient.get(baseUrl, urlParameters, cookie)) {
            is Outcome.Ok -> {
                if (htmlParser.isErrorPage(response.value)) {
                    // The week request that got us here already ran through the re-authenticating
                    // gateway, so a rejection now is not something another login would fix.
                    Napier.w("Detail page for $baseUrl came back as an error page", tag = TAG)
                    null
                } else {
                    timetableParser.parseIndividualPage(response.value)
                }
            }
            is Outcome.Err -> {
                Napier.w("Could not fetch lecture details: ${response.error}", tag = TAG)
                null
            }
        }
    }

    private suspend fun findOrCreateLecturer(lecturerName: String): Long {
        lecturerDao.searchByName(lecturerName).firstOrNull()?.let { return it.lecturerId }
        return lecturerDao.insert(LecturerEntity(lecturerId = 0, lecturerName = lecturerName))
    }

    /** Dualis links carry their parameters HTML-encoded, so `&amp;` has to be undone first. */
    private fun parseUrlParameters(url: String): Map<String, String> {
        val queryString = url.replace("&amp;", "&").substringAfter("?", "")
        if (queryString.isEmpty()) return emptyMap()

        return queryString.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    @OptIn(ExperimentalTime::class)
    private fun tempLecturesToBasicEntities(tempLectures: List<TempLectureModel>): List<LectureEventEntity> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return tempLectures.map { temp ->
            LectureEventEntity(
                lectureId = 0,
                shortSubjectName = temp.shortSubjectName ?: "Unknown",
                fullSubjectName = temp.fullSubjectName,
                startTime = temp.startTime,
                endTime = temp.endTime,
                location = temp.location,
                isTest = temp.isTest,
                fetchedAt = now
            )
        }
    }
}
