/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.notifications

import de.fampopprol.dhbwhorb.core.error.AppError
import de.fampopprol.dhbwhorb.core.error.Outcome
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.services.AuthenticationService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisLectureService
import de.fampopprol.dhbwhorb.data.dualis.remote.services.DualisPageGateway
import de.fampopprol.dhbwhorb.data.dualis.remote.session.ReAuthenticator
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.credentials.FakeSecureStorage
import de.fampopprol.dhbwhorb.data.storage.database.dao.SyncMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.SyncMetadataEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureWithLecturers
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import de.fampopprol.dhbwhorb.testutil.MockAppDatabase
import de.fampopprol.dhbwhorb.testutil.MockLectureEventDao
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The matching is the part of this that a user notices.
 *
 * Before the rewrite, cached and fetched lectures were paired by `subject_start_end`, so a lecture
 * that moved could never be recognised as having moved — the pairing key had changed with it. The
 * first two tests are the two halves of getting that right: a move has to read as a move, and a
 * cancellation must not be dressed up as one.
 */
class LectureChangeMonitorTest {

    // Wednesday 09:00 of a fixed week, so nothing here depends on when the suite runs.
    private val now = LocalDateTime(2026, 3, 4, 9, 0)
    private val weekMonday = LocalDateTime(2026, 3, 2, 0, 0)

    // ── Matching ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a moved lecture is reported as a move`() = runTest {
        val cached = lecture("MATHE", day = 5, from = 10, to = 12)
        val fetched = lecture("MATHE", day = 5, from = 14, to = 16)

        val changes = changesFrom(cached = listOf(cached), fetched = listOf(fetched))

        assertEquals(1, changes.size, "one event, one notification: $changes")
        val move = changes.single()
        assertTrue(move is LectureChange.TimeChange, "expected a move but got $move")
        assertEquals(LocalDateTime(2026, 3, 5, 10, 0), move.oldStartTime)
        assertEquals(LocalDateTime(2026, 3, 5, 14, 0), move.newStartTime)
    }

    @Test
    fun `a lecture moved to another day is still a move`() = runTest {
        val changes = changesFrom(
            cached = listOf(lecture("PROG", day = 5, from = 10, to = 12)),
            fetched = listOf(lecture("PROG", day = 6, from = 10, to = 12)),
        )

        assertEquals(1, changes.size, "$changes")
        assertTrue(changes.single() is LectureChange.TimeChange)
    }

    @Test
    fun `cancelling one of two weekly slots is a cancellation and not a move`() = runTest {
        // The trap for a naive nearest-match: MATHE runs Thursday and Friday, Thursday is dropped.
        // Pairing the survivors by distance would call that "Thursday moved to Friday".
        val thursday = lecture("MATHE", day = 5, from = 10, to = 12)
        val friday = lecture("MATHE", day = 6, from = 10, to = 12)

        val changes = changesFrom(cached = listOf(thursday, friday), fetched = listOf(friday))

        assertEquals(1, changes.size, "$changes")
        val change = changes.single()
        assertTrue(change is LectureChange.Cancellation, "expected a cancellation but got $change")
        assertEquals(LocalDateTime(2026, 3, 5, 10, 0), change.occursAt)
    }

    @Test
    fun `a room change is reported on its own`() = runTest {
        val changes = changesFrom(
            cached = listOf(lecture("PROG", day = 5, from = 10, to = 12, location = "HOR-231")),
            fetched = listOf(lecture("PROG", day = 5, from = 10, to = 12, location = "HOR-120")),
        )

        val change = changes.single()
        assertTrue(change is LectureChange.LocationChange, "$change")
        assertEquals("HOR-231", change.oldLocation)
        assertEquals("HOR-120", change.newLocation)
    }

    @Test
    fun `an exam flag change is reported on its own`() = runTest {
        val changes = changesFrom(
            cached = listOf(lecture("PROG", day = 5, from = 10, to = 12, isTest = false)),
            fetched = listOf(lecture("PROG", day = 5, from = 10, to = 12, isTest = true)),
        )

        val change = changes.single()
        assertTrue(change is LectureChange.TypeChange, "$change")
        assertTrue(!change.oldIsTest)
        assertTrue(change.newIsTest)
    }

    @Test
    fun `a lecturer change is reported on its own`() = runTest {
        val changes = changesFrom(
            cached = listOf(lecture("PROG", day = 5, from = 10, to = 12, lecturers = listOf("Prof. A"))),
            fetched = listOf(lecture("PROG", day = 5, from = 10, to = 12, lecturers = listOf("Prof. B"))),
        )

        val change = changes.single()
        assertTrue(change is LectureChange.LecturerChange, "$change")
        assertEquals(listOf("Prof. A"), change.oldLecturers)
        assertEquals(listOf("Prof. B"), change.newLecturers)
    }

    @Test
    fun `the same lecturers in a different order is not a change`() = runTest {
        val changes = changesFrom(
            cached = listOf(lecture("PROG", day = 5, from = 10, to = 12, lecturers = listOf("Prof. A", "Prof. B"))),
            fetched = listOf(lecture("PROG", day = 5, from = 10, to = 12, lecturers = listOf("Prof. B", "Prof. A"))),
        )

        assertTrue(changes.isEmpty(), "sorted comparison must ignore ordering: $changes")
    }

    @Test
    fun `an unchanged week reports nothing`() = runTest {
        val same = lecture("PROG", day = 5, from = 10, to = 12)
        val monitor = monitor(
            service = FakeLectureService(fullByWeek = mapOf(0 to listOf(same)), gridByWeek = emptyMap()),
            cached = listOf(same),
        )

        val result = monitor.checkForChanges()

        assertTrue(result is MonitorResult.NoChanges, "$result")
    }

    @Test
    fun `a new lecture and a cancellation keep separate notification keys`() = runTest {
        val changes = changesFrom(
            cached = listOf(lecture("ALT", day = 5, from = 10, to = 12)),
            fetched = listOf(
                lecture("NEU1", day = 5, from = 14, to = 16),
                lecture("NEU2", day = 6, from = 14, to = 16),
            ),
        )

        // The old code gave every new lecture the id `lecture_0`, so they replaced each other.
        val keys = changes.map { it.notificationKey }
        assertEquals(keys.size, keys.toSet().size, "notification keys must be unique: $keys")
    }

    // ── Scope ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `lectures that have already ended are not compared`() = runTest {
        // Monday is behind us on the Wednesday this test pretends to run.
        val changes = changesFrom(
            cached = listOf(lecture("PAST", day = 2, from = 10, to = 12, location = "OLD")),
            fetched = listOf(lecture("PAST", day = 2, from = 10, to = 12, location = "NEW")),
        )

        assertTrue(changes.isEmpty(), "nobody needs a notification about Monday: $changes")
    }

    // ── The grid sweep over future weeks ────────────────────────────────────────────────────

    /** One lecture in each of the weeks the sweep covers, so nothing has to be seeded. */
    private fun futureWeeks(shiftWeek1By: Int = 0) =
        (1..LectureChangeMonitor.FUTURE_WEEKS).associateWith { offset ->
            val from = if (offset == 1) 10 + shiftWeek1By else 10
            listOf(lecture("PROG", day = 2 + offset * 7, from = from, to = from + 2))
        }

    @Test
    fun `a future week whose grid is unchanged is never fetched in full`() = runTest {
        val cached = futureWeeks()
        val service = FakeLectureService(
            fullByWeek = cached + (0 to emptyList()),
            gridByWeek = cached,
        )

        monitor(service = service, cached = cached.values.flatten()).checkForChanges()

        assertEquals(4, service.gridFetches, "the grid is what a sweep is supposed to cost")
        assertEquals(1, service.fullFetches, "only the current week may be fetched in full")
    }

    @Test
    fun `a future week whose grid moved is fetched in full`() = runTest {
        val cached = futureWeeks()
        val moved = futureWeeks(shiftWeek1By = 4)
        val service = FakeLectureService(
            fullByWeek = moved + (0 to emptyList()),
            gridByWeek = moved,
        )

        val result = monitor(service = service, cached = cached.values.flatten()).checkForChanges()

        assertEquals(2, service.fullFetches, "current week plus the one that moved")
        assertTrue(result is MonitorResult.Changes, "$result")
        assertTrue(result.changes.single() is LectureChange.TimeChange)
    }

    @Test
    fun `a future week that was never loaded is fetched in full and stays quiet`() = runTest {
        val weeks = futureWeeks()
        val service = FakeLectureService(fullByWeek = weeks + (0 to emptyList()), gridByWeek = weeks)

        val result = monitor(service = service, cached = emptyList()).checkForChanges()

        // Announcing a whole week the app has never shown is not news, it is noise — but the week
        // still has to end up in the cache, or it stays invisible until someone pages to it.
        assertTrue(result is MonitorResult.NoChanges, "seeding must not notify: $result")
        assertEquals(
            LectureChangeMonitor.FUTURE_WEEKS,
            service.savedWeeks.keys.count { it > 0 },
            "every uncached week gets seeded, not just the first: ${service.savedWeeks.keys}",
        )
        assertEquals(0, service.gridFetches, "there is nothing to compare a grid against yet")
    }

    @Test
    fun `a seeded week is compared by its grid on the next run`() = runTest {
        val weeks = futureWeeks()
        val service = FakeLectureService(fullByWeek = weeks + (0 to emptyList()), gridByWeek = weeks)
        val sync = RememberingSyncDao()

        monitor(service = service, cached = emptyList(), sync = sync).checkForChanges()
        val fullAfterSeeding = service.fullFetches

        // Four hours later, with everything the first run stored now in the cache.
        sync.insert(SyncMetadataEntity("lecture_monitor_future_sweep", now.minusHours(5)))
        monitor(service = service, cached = weeks.values.flatten(), sync = sync).checkForChanges()

        assertEquals(4, service.gridFetches, "the second run compares instead of seeding again")
        assertEquals(
            fullAfterSeeding + 1,
            service.fullFetches,
            "only the current week may still be fetched in full",
        )
    }

    @Test
    fun `the grid sweep is skipped when it ran recently`() = runTest {
        val weeks = futureWeeks()
        val service = FakeLectureService(fullByWeek = weeks + (0 to emptyList()), gridByWeek = weeks)
        val sync = RememberingSyncDao()
        // One hour ago — inside the four the sweep waits for.
        sync.insert(SyncMetadataEntity("lecture_monitor_future_sweep", now.minusHours(1)))

        monitor(service = service, cached = weeks.values.flatten(), sync = sync).checkForChanges()

        assertEquals(0, service.gridFetches, "the hourly run must not sweep the future every time")
        assertEquals(1, service.fullFetches, "nor seed anything outside a sweep")
    }

    // ── Failure handling ────────────────────────────────────────────────────────────────────

    @Test
    fun `a failure fetching the current week in full is reported as an error`() = runTest {
        val service = FakeLectureService(
            fullByWeek = emptyMap(),
            gridByWeek = emptyMap(),
            failFullFetchForWeeks = setOf(0),
        )

        val result = monitor(service = service, cached = emptyList()).checkForChanges()

        assertTrue(result is MonitorResult.Error, "$result")
        assertEquals(AppError.Offline, result.error)
    }

    @Test
    fun `a failure saving the current week after a change is reported as an error`() = runTest {
        val cached = listOf(lecture("PROG", day = 5, from = 10, to = 12))
        val fetched = listOf(lecture("PROG", day = 5, from = 14, to = 16)) // a move, so a save is attempted
        val service = FakeLectureService(
            fullByWeek = mapOf(0 to fetched),
            gridByWeek = emptyMap(),
            failSaveForWeeks = setOf(0),
        )

        val result = monitor(service = service, cached = cached).checkForChanges()

        assertTrue(result is MonitorResult.Error, "$result")
        assertTrue(result.error is AppError.Storage)
    }

    @Test
    fun `a future week whose grid cannot be fetched is skipped without discarding current-week changes`() = runTest {
        val cached = futureWeeks()
        // Week +1's grid fails; the current week has an independent change to report.
        val service = FakeLectureService(
            fullByWeek = mapOf(0 to listOf(lecture("PROG", day = 5, from = 14, to = 16))),
            gridByWeek = cached,
            failGridFetchForWeeks = setOf(1),
        )
        val currentWeekCached = listOf(lecture("PROG", day = 5, from = 10, to = 12))

        val result = monitor(service = service, cached = currentWeekCached + cached.values.flatten()).checkForChanges()

        assertTrue(result is MonitorResult.Changes, "one broken future week must not swallow real changes: $result")
    }

    @Test
    fun `a failure fetching a never-seen future week in full is not fatal to the run`() = runTest {
        val weeks = futureWeeks()
        // Week +1 has never been cached, and its seeding fetch fails.
        val service = FakeLectureService(
            fullByWeek = weeks + (0 to emptyList()),
            gridByWeek = weeks,
            failFullFetchForWeeks = setOf(1),
        )

        val result = monitor(service = service, cached = emptyList()).checkForChanges()

        // The failure is logged and skipped (handleUnexpectedPage-style tolerance), current week is fine.
        assertTrue(result is MonitorResult.NoChanges, "$result")
    }

    @Test
    fun `an unexpected exception is reported as an Unexpected error rather than propagating`() = runTest {
        val service = FakeLectureService(fullByWeek = emptyMap(), gridByWeek = emptyMap(), throwOnFullFetch = true)

        val result = monitor(service = service, cached = emptyList()).checkForChanges()

        assertTrue(result is MonitorResult.Error, "$result")
        assertTrue(result.error is AppError.Unexpected, "expected Unexpected but got ${result.error}")
    }

    @Test
    fun `an exception with no message falls back to a generic one`() = runTest {
        val service = FakeLectureService(
            fullByWeek = emptyMap(),
            gridByWeek = emptyMap(),
            throwOnFullFetch = true,
            throwWithNullMessage = true,
        )

        val result = monitor(service = service, cached = emptyList()).checkForChanges()

        val error = assertIs<MonitorResult.Error>(result).error
        assertEquals(AppError.Unexpected("lecture monitoring"), error)
    }

    // ── Scaffolding ─────────────────────────────────────────────────────────────────────────

    private suspend fun changesFrom(
        cached: List<LectureEventEntity>,
        fetched: List<LectureEventEntity>,
    ): List<LectureChange> {
        val result = monitor(
            service = FakeLectureService(fullByWeek = mapOf(0 to fetched), gridByWeek = emptyMap()),
            cached = cached,
        ).checkForChanges()
        return when (result) {
            is MonitorResult.Changes -> result.changes
            is MonitorResult.NoChanges -> emptyList()
            is MonitorResult.Error -> error("unexpected error: ${result.error}")
        }
    }

    private fun monitor(
        service: DualisLectureService = FakeLectureService(emptyMap(), emptyMap()),
        cached: List<LectureEventEntity>,
        sync: RememberingSyncDao = RememberingSyncDao(),
    ) = LectureChangeMonitor(
        dualisLectureServiceFactory = { service },
        lectureEventDao = CachedLectureDao(cached),
        syncMetadataDao = sync,
        clock = { now },
    )

    /** March 2026: day 2 is the Monday of the current week, day 12 is in the week after. */
    private fun lecture(
        subject: String,
        day: Int,
        from: Int,
        to: Int,
        location: String = "HOR-100",
        lecturers: List<String> = emptyList(),
        isTest: Boolean = false,
    ) = LectureEventEntity(
        lectureId = 0,
        shortSubjectName = subject,
        fullSubjectName = subject,
        startTime = LocalDateTime(2026, 3, day, from, 0),
        endTime = LocalDateTime(2026, 3, day, to, 0),
        location = location,
        isTest = isTest,
    ).apply { this.lecturers = lecturers }

    private fun LocalDateTime.minusHours(hours: Int) =
        LocalDateTime(year, month, day, hour - hours, minute)

    /** The shared mock forgets everything it is told; the sweep interval needs one that does not. */
    private class RememberingSyncDao : SyncMetadataDao {
        private val entries = mutableMapOf<String, SyncMetadataEntity>()
        override suspend fun insert(syncMetadataEntity: SyncMetadataEntity) {
            entries[syncMetadataEntity.key] = syncMetadataEntity
        }
        override suspend fun insertAll(syncMetadataEntities: List<SyncMetadataEntity>) =
            syncMetadataEntities.forEach { insert(it) }
        override suspend fun update(syncMetadataEntity: SyncMetadataEntity) = insert(syncMetadataEntity)
        override suspend fun delete(syncMetadataEntity: SyncMetadataEntity) {
            entries.remove(syncMetadataEntity.key)
        }
        override suspend fun getSyncMetadata(key: String): SyncMetadataEntity? = entries[key]
        override suspend fun clearAllSyncMetadata() = entries.clear()
        override suspend fun getAllSyncMetadata(): List<SyncMetadataEntity> = entries.values.toList()
        override suspend fun deleteByKey(key: String) {
            entries.remove(key)
        }
    }

    private class CachedLectureDao(private val cached: List<LectureEventEntity>) : MockLectureEventDao() {
        override suspend fun getAllWithLecturers(): List<LectureWithLecturers> = cached.map {
            LectureWithLecturers(
                lecture = it,
                lecturers = it.lecturers.orEmpty().map { name -> LecturerEntity(0, name) },
            )
        }
    }

    /** Counts what a run actually asks Dualis for, which is the point of the two-speed design. */
    private class FakeLectureService(
        private val fullByWeek: Map<Int, List<LectureEventEntity>>,
        private val gridByWeek: Map<Int, List<LectureEventEntity>>,
        /** Week offsets whose full fetch must fail instead of returning [fullByWeek]. */
        private val failFullFetchForWeeks: Set<Int> = emptySet(),
        /** Week offsets whose grid fetch must fail instead of returning [gridByWeek]. */
        private val failGridFetchForWeeks: Set<Int> = emptySet(),
        /** Week offsets whose save must fail instead of succeeding. */
        private val failSaveForWeeks: Set<Int> = emptySet(),
        /** When set, the full fetch throws instead of returning an [Outcome] at all. */
        private val throwOnFullFetch: Boolean = false,
        /** When set together with [throwOnFullFetch], the thrown exception carries no message. */
        private val throwWithNullMessage: Boolean = false,
    ) : DualisLectureService(
        apiClient = apiClient,
        sessionManager = sessionManager,
        gateway = DualisPageGateway(apiClient, sessionManager, ReAuthenticator(sessionManager, authService)),
        lectureEventDao = database.lectureDao(),
        lecturerDao = database.lecturerDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao(),
    ) {
        var fullFetches = 0
        var gridFetches = 0

        override suspend fun getWeeklyLecturesForWeek(
            start: LocalDateTime,
            end: LocalDateTime
        ): Outcome<List<LectureEventEntity>> {
            fullFetches++
            if (throwOnFullFetch) throw IllegalStateException(if (throwWithNullMessage) null else "boom")
            if (start.weekOffset() in failFullFetchForWeeks) {
                return Outcome.Err(AppError.Offline)
            }
            return Outcome.Ok(fullByWeek[start.weekOffset()].orEmpty())
        }

        override suspend fun getWeeklySkeletonForWeek(
            start: LocalDateTime,
            end: LocalDateTime
        ): Outcome<List<LectureEventEntity>> {
            gridFetches++
            if (start.weekOffset() in failGridFetchForWeeks) {
                return Outcome.Err(AppError.Offline)
            }
            return Outcome.Ok(gridByWeek[start.weekOffset()].orEmpty())
        }

        val savedWeeks = mutableMapOf<Int, List<LectureEventEntity>>()

        override suspend fun saveLecturesToDatabase(
            lectures: List<LectureEventEntity>,
            weekStart: LocalDateTime,
            weekEnd: LocalDateTime
        ): Outcome<List<LectureEventEntity>> {
            if (weekStart.weekOffset() in failSaveForWeeks) {
                return Outcome.Err(AppError.Storage("could not save"))
            }
            savedWeeks[weekStart.weekOffset()] = lectures
            return Outcome.Ok(lectures)
        }

        /** 2 March 2026 is week 0 here, so every Monday maps back to an offset. */
        private fun LocalDateTime.weekOffset(): Int = (day - 2) / 7

        companion object {
            private val httpClient = HttpClient { }
            private val sessionManager = SessionManager(FakeSecureStorage())
            private val apiClient = DualisApiClient(httpClient)
            private val authService = AuthenticationService(sessionManager, httpClient)
            private val database = MockAppDatabase()
        }
    }
}
