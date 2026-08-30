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
import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import de.fampopprol.dhbwhorb.data.storage.database.dao.SyncMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.SyncMetadataEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferences
import de.fampopprol.dhbwhorb.data.storage.preferences.NotificationPreferencesInteractor
import de.fampopprol.dhbwhorb.data.storage.settings.SettingsStorage
import de.fampopprol.dhbwhorb.services.reminders.LectureReminder
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderPlanner
import de.fampopprol.dhbwhorb.services.reminders.LectureReminderScheduler
import de.fampopprol.dhbwhorb.testutil.MockAppDatabase
import de.fampopprol.dhbwhorb.testutil.MockLectureEventDao
import de.fampopprol.dhbwhorb.testutil.TestPlatformSettings
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [NotificationManager] wires four collaborators together; the interesting behaviour is which of
 * them get called for a given combination of preferences, permission and monitoring outcome - not
 * any parsing or matching logic of its own, which is what [LectureChangeMonitorTest] already covers.
 */
class NotificationManagerTest {

    // Wednesday, so the current-week check and the "is a sweep due" check both have a fixed answer.
    private val now = LocalDateTime(2026, 3, 4, 9, 0)

    private class NoOpSecureStorage : SecureStorageInterface {
        override fun setString(key: String, value: String) {}
        override fun getString(key: String, defaultValue: String): String = defaultValue
        override fun remove(key: String) {}
        override fun clear() {}
    }

    /** A [SyncMetadataDao] that never remembers a previous sweep, so a future sweep is always due. */
    private class InMemorySyncMetadataDao : SyncMetadataDao {
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

    /**
     * A [DualisLectureService] whose three network-facing methods are replaced outright - the
     * constructor dependencies below are never exercised, only type-checked.
     */
    private class FakeLectureService(
        private val currentWeekLectures: List<LectureEventEntity> = emptyList(),
        private val failWithError: AppError? = null,
    ) : DualisLectureService(
        apiClient = apiClient,
        sessionManager = sessionManager,
        gateway = DualisPageGateway(apiClient, sessionManager, ReAuthenticator(sessionManager, authService)),
        lectureEventDao = database.lectureDao(),
        lecturerDao = database.lecturerDao(),
        lectureLecturerCrossRefDao = database.lectureLecturerCrossRefDao(),
    ) {
        override suspend fun getWeeklyLecturesForWeek(
            start: LocalDateTime,
            end: LocalDateTime
        ): Outcome<List<LectureEventEntity>> {
            failWithError?.let { return Outcome.Err(it) }
            // Only the current week (the one `now` falls in) carries lectures; every future week
            // the sweep asks about comes back empty, so it is seeded without producing a change.
            return Outcome.Ok(if (start.month == LocalDateTime(2026, 3, 4, 0, 0).month && start.day <= 4) currentWeekLectures else emptyList())
        }

        override suspend fun getWeeklySkeletonForWeek(
            start: LocalDateTime,
            end: LocalDateTime
        ): Outcome<List<LectureEventEntity>> = Outcome.Ok(emptyList())

        override suspend fun saveLecturesToDatabase(
            lectures: List<LectureEventEntity>,
            weekStart: LocalDateTime,
            weekEnd: LocalDateTime
        ): Outcome<List<LectureEventEntity>> = Outcome.Ok(lectures)

        companion object {
            private val httpClient = HttpClient { }
            private val sessionManager = SessionManager(FakeSecureStorage())
            private val apiClient = DualisApiClient(httpClient)
            private val authService = AuthenticationService(sessionManager, httpClient)
            private val database = MockAppDatabase()
        }
    }

    private class RecordingNotificationDispatcher(
        private val permission: Boolean = true,
    ) : NotificationDispatcher {
        var singleNotifications = mutableListOf<Triple<String, String, String>>()
        var summaryNotifications = mutableListOf<Triple<String, String, Int>>()

        override suspend fun requestPermission(): Boolean = permission
        override suspend fun hasPermission(): Boolean = permission
        override suspend fun showNotification(title: String, message: String, notificationKey: String) {
            singleNotifications += Triple(title, message, notificationKey)
        }
        override suspend fun showSummaryNotification(title: String, message: String, changeCount: Int) {
            summaryNotifications += Triple(title, message, changeCount)
        }
    }

    private class RecordingLectureReminderScheduler : LectureReminderScheduler {
        var replaceAllCallCount = 0
        var lastReminders: List<LectureReminder> = emptyList()
        override suspend fun replaceAll(reminders: List<LectureReminder>) {
            replaceAllCallCount++
            lastReminders = reminders
        }
        override fun firesExactly(): Boolean = true
    }

    private lateinit var preferences: NotificationPreferences
    private lateinit var preferencesInteractor: NotificationPreferencesInteractor
    private lateinit var reminderScheduler: RecordingLectureReminderScheduler
    private lateinit var reminderPlanner: LectureReminderPlanner

    @BeforeTest
    fun setup() {
        val settingsStorage = SettingsStorage(TestPlatformSettings(), NoOpSecureStorage())
        preferences = NotificationPreferences(settingsStorage)
        preferencesInteractor = NotificationPreferencesInteractor(preferences)
        reminderScheduler = RecordingLectureReminderScheduler()
        reminderPlanner = LectureReminderPlanner(
            lectureEventDao = MockLectureEventDao(),
            preferences = preferencesInteractor,
            scheduler = reminderScheduler,
            clock = { now },
        )
    }

    private fun monitor(
        currentWeekLectures: List<LectureEventEntity> = emptyList(),
        failWithError: AppError? = null,
    ) = LectureChangeMonitor(
        dualisLectureServiceFactory = { FakeLectureService(currentWeekLectures, failWithError) },
        lectureEventDao = MockLectureEventDao(),
        syncMetadataDao = InMemorySyncMetadataDao(),
        clock = { now },
    )

    private fun manager(
        dispatcher: RecordingNotificationDispatcher = RecordingNotificationDispatcher(),
        currentWeekLectures: List<LectureEventEntity> = emptyList(),
        failWithError: AppError? = null,
        widgetRefresher: (() -> Unit)? = null,
    ) = NotificationManager(
        monitor = monitor(currentWeekLectures, failWithError),
        dispatcher = dispatcher,
        preferences = preferencesInteractor,
        reminders = reminderPlanner,
        widgetRefresher = widgetRefresher?.let { refresh -> de.fampopprol.dhbwhorb.services.widget.WidgetRefresher { refresh() } },
    )

    @Test
    fun checkAndNotify_lectureAlertsOff_skipsTheCheckEntirely() = runTest {
        preferences.setNotificationsEnabled(false)
        preferencesInteractor.refresh()
        val dispatcher = RecordingNotificationDispatcher()

        val result = manager(dispatcher).checkAndNotify()

        assertIs<Outcome.Ok<Unit>>(result)
        assertTrue(dispatcher.singleNotifications.isEmpty())
        assertTrue(dispatcher.summaryNotifications.isEmpty())
    }

    @Test
    fun checkAndNotify_alwaysReschedulesReminders_evenWhenAlertsAreOff() = runTest {
        preferences.setNotificationsEnabled(false)
        preferencesInteractor.refresh()

        manager().checkAndNotify()

        assertEquals(1, reminderScheduler.replaceAllCallCount, "reminders are a separate setting from change alerts")
    }

    @Test
    fun checkAndNotify_noPermission_skipsTheCheck() = runTest {
        preferences.setNotificationsEnabled(true)
        preferences.setLectureAlertsEnabled(true)
        preferencesInteractor.refresh()
        val dispatcher = RecordingNotificationDispatcher(permission = false)

        val result = manager(dispatcher).checkAndNotify()

        assertIs<Outcome.Ok<Unit>>(result)
        assertTrue(dispatcher.singleNotifications.isEmpty())
    }

    @Test
    fun checkAndNotify_noChanges_notifiesNothing() = runTest {
        preferences.setNotificationsEnabled(true)
        preferences.setLectureAlertsEnabled(true)
        preferencesInteractor.refresh()
        val dispatcher = RecordingNotificationDispatcher()

        val result = manager(dispatcher).checkAndNotify()

        assertIs<Outcome.Ok<Unit>>(result)
        assertTrue(dispatcher.singleNotifications.isEmpty())
        assertTrue(dispatcher.summaryNotifications.isEmpty())
    }

    @Test
    fun checkAndNotify_oneNewLecture_showsASingleNotification() = runTest {
        preferences.setNotificationsEnabled(true)
        preferences.setLectureAlertsEnabled(true)
        preferencesInteractor.refresh()
        val dispatcher = RecordingNotificationDispatcher()
        val newLecture = LectureEventEntity(
            lectureId = 1,
            shortSubjectName = "MATHE",
            fullSubjectName = "Mathematik 1",
            startTime = LocalDateTime(2026, 3, 4, 10, 0),
            endTime = LocalDateTime(2026, 3, 4, 11, 30),
            location = "HOR-100",
        )
        var widgetRefreshed = false

        val result = manager(
            dispatcher,
            currentWeekLectures = listOf(newLecture),
            widgetRefresher = { widgetRefreshed = true },
        ).checkAndNotify()

        assertIs<Outcome.Ok<Unit>>(result)
        assertEquals(1, dispatcher.singleNotifications.size)
        assertTrue(dispatcher.summaryNotifications.isEmpty())
        assertTrue(widgetRefreshed, "the cache moved, so the widget must be told to refresh")
    }

    @Test
    fun checkAndNotify_severalNewLectures_showsOneSummaryNotificationInstead() = runTest {
        preferences.setNotificationsEnabled(true)
        preferences.setLectureAlertsEnabled(true)
        preferencesInteractor.refresh()
        val dispatcher = RecordingNotificationDispatcher()
        val newLectures = listOf(
            LectureEventEntity(
                lectureId = 1,
                shortSubjectName = "MATHE",
                fullSubjectName = "Mathematik 1",
                startTime = LocalDateTime(2026, 3, 4, 10, 0),
                endTime = LocalDateTime(2026, 3, 4, 11, 30),
                location = "HOR-100",
            ),
            LectureEventEntity(
                lectureId = 2,
                shortSubjectName = "PROG",
                fullSubjectName = "Programmieren 1",
                startTime = LocalDateTime(2026, 3, 4, 13, 0),
                endTime = LocalDateTime(2026, 3, 4, 14, 30),
                location = "HOR-200",
            ),
        )

        val result = manager(dispatcher, currentWeekLectures = newLectures).checkAndNotify()

        assertIs<Outcome.Ok<Unit>>(result)
        assertTrue(dispatcher.singleNotifications.isEmpty(), "several changes must not fire one notification each")
        assertEquals(1, dispatcher.summaryNotifications.size)
        assertEquals(2, dispatcher.summaryNotifications.single().third)
    }

    @Test
    fun checkAndNotify_monitorFails_propagatesTheError() = runTest {
        preferences.setNotificationsEnabled(true)
        preferences.setLectureAlertsEnabled(true)
        preferencesInteractor.refresh()
        val dispatcher = RecordingNotificationDispatcher()

        val result = manager(dispatcher, failWithError = AppError.Offline).checkAndNotify()

        val error = assertIs<Outcome.Err>(result).error
        assertEquals(AppError.Offline, error)
        assertTrue(dispatcher.singleNotifications.isEmpty())
    }
}
