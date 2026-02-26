package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.data.dualis.demo.DemoDataProvider
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.TimetableParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.temp_models.TempLectureModel
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureLecturerCrossRefDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LectureEventDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.timetable.LecturerDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureLecturerCrossRef
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LectureEventEntity
import de.fampopprol.dhbwhorb.data.storage.database.entities.timetable.LecturerEntity
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Service for fetching and processing lecture/timetable data from Dualis.
 *
 * Flow: Service -> API Client -> Service -> Parser -> Service -> DB
 *
 * This service:
 * 1. Checks session/authentication state
 * 2. Fetches HTML using DualisApiClient
 * 3. Validates HTML (checks for errors, redirects)
 * 4. Passes HTML to TimetableParser
 * 5. Enriches parsed data with additional fetches if needed
 * 6. Saves to database
 */
class DualisLectureService(
    private val apiClient: DualisApiClient,
    private val sessionManager: SessionManager,
    private val authenticationService: AuthenticationService,
    private val timetableParser: TimetableParser,
    private val htmlParser: HtmlParser,
    private val lectureEventDao: LectureEventDao,
    private val lecturerDao: LecturerDao,
    private val lectureLecturerCrossRefDao: LectureLecturerCrossRefDao
) {
    companion object {
        private const val TAG = "DualisLectureService"
        private const val BASE_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val MAX_RETRY_ATTEMPTS = 2
    }

    /**
     * Fetch weekly lectures for the current week.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getWeeklyLecturesForCurrentWeek(): Result<List<LectureEventEntity>> {
        Napier.d("Fetching weekly lectures for current week", tag = TAG)

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val currentDate = now.date

        return getWeeklyLecturesForDate(currentDate)
    }

    /**
     * Fetch weekly lectures for a specific date.
     * The Dualis API will return the week containing this date.
     */
    suspend fun getWeeklyLecturesForDate(date: LocalDate): Result<List<LectureEventEntity>> {
        return fetchWeeklyLecturesWithRetry(date, 0)
    }

    /**
     * Internal method to fetch weekly lectures with retry logic for authentication.
     */
    private suspend fun fetchWeeklyLecturesWithRetry(
        date: LocalDate,
        attemptCount: Int
    ): Result<List<LectureEventEntity>> {
        Napier.d("Fetching weekly lectures for date: $date (attempt $attemptCount)", tag = TAG)

        // Check authentication
        if (!sessionManager.isAuthenticated() && !sessionManager.isDemoMode()) {
            Napier.w("No active session, need to authenticate first", tag = TAG)
            val reAuthResult = reAuthenticate()
            if (reAuthResult.isFailure) {
                return Result.failure(reAuthResult.exceptionOrNull()!!)
            }
        }

        // Handle demo mode
        if (sessionManager.isDemoMode()) {
            Napier.d("Demo mode active, returning demo lectures", tag = TAG)
            val demoStartDate = LocalDateTime(date.year, date.month, date.day, 0, 0, 0)
            val demoLectures = DemoDataProvider.generateDemoLecturesForWeek(demoStartDate)

            // Save demo lecturers to database if not already present
            val demoLecturers = DemoDataProvider.generateDemoLecturers()
            demoLecturers.forEach { lecturer ->
                try {
                    val existingLecturer = lecturerDao.getById(lecturer.lecturerId)
                    if (existingLecturer == null) {
                        lecturerDao.insert(lecturer)
                    }
                } catch (e: Exception) {
                    Napier.w("Could not check/insert demo lecturer: ${e.message}", tag = TAG)
                }
            }

            // Save demo lectures to database
            demoLectures.forEach { lecture ->
                try {
                    val existingLecture = lectureEventDao.getById(lecture.lectureId)
                    if (existingLecture == null) {
                        lectureEventDao.insert(lecture)

                        // Create lecturer associations
                        val lecturerIds = DemoDataProvider.getLecturerIdsForLecture(lecture.lectureId)
                        lecturerIds.forEach { lecturerId ->
                            val crossRef = LectureLecturerCrossRef(
                                lectureId = lecture.lectureId,
                                lecturerId = lecturerId
                            )
                            lectureLecturerCrossRefDao.insert(crossRef)
                        }
                    }
                } catch (e: Exception) {
                    Napier.w("Could not insert demo lecture: ${e.message}", tag = TAG)
                }
            }

            return Result.success(demoLectures)
        }

        try {
            // Get auth data for user ID
            val authData = sessionManager.getAuthData()
            if (authData == null) {
                return Result.failure(Exception("No auth data available"))
            }

            // Format date as DD.MM.YYYY (German format used by Dualis)
            val dateString = "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}.${date.year}"

            // Build URL parameters
            // Note: User ID and course ID would normally be extracted from session
            // For now using placeholder logic - these should come from auth data or user profile
            val urlParameters = mapOf(
                "APPNAME" to "CampusNet",
                "PRGNAME" to "SCHEDULER",
                "ARGUMENTS" to "-N${authData.sessionId},-N000028,-A$dateString,-A,-N1,-N000000000000000"
            )

            // Step 1: Fetch HTML via API client
            Napier.d("Fetching weekly timetable HTML", tag = TAG)
            
            // Clean cookie
            val rawCookie = authData.cookie
            val cookie = rawCookie?.substringBefore(";")
            
            when (val apiResult = apiClient.get(BASE_URL, urlParameters, cookie)) {
                is DualisApiClient.ApiResult.Success -> {
                    val htmlContent = apiResult.htmlContent

                    // Step 2: Validate HTML - check for explicit errors
                    if (htmlParser.isErrorPage(htmlContent)) {
                        Napier.w("Received error page, attempting re-authentication", tag = TAG)

                        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                            return Result.failure(Exception("Max retry attempts reached"))
                        }

                        val reAuthResult = reAuthenticate()
                        if (reAuthResult.isFailure) {
                            return Result.failure(reAuthResult.exceptionOrNull()!!)
                        }

                        // Retry the request
                        return fetchWeeklyLecturesWithRetry(date, attemptCount + 1)
                    }

                    // Step 2b: Validate that it's actually a timetable page (not session expired)
                    if (!htmlParser.isValidTimetablePage(htmlContent)) {
                        Napier.w("Received invalid timetable page (likely session expired), attempting re-authentication", tag = TAG)

                        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                            return Result.failure(Exception("Max retry attempts reached - invalid timetable page"))
                        }

                        val reAuthResult = reAuthenticate()
                        if (reAuthResult.isFailure) {
                            return Result.failure(reAuthResult.exceptionOrNull()!!)
                        }

                        // Retry the request
                        return fetchWeeklyLecturesWithRetry(date, attemptCount + 1)
                    }

                    // Step 3: Parse HTML
                    Napier.d("Parsing weekly timetable HTML", tag = TAG)
                    val tempLectures = timetableParser.parseWeeklyView(htmlContent)
                    Napier.d("Parsed ${tempLectures.size} lectures from weekly view", tag = TAG)

                    // Step 4: Enrich with individual page data (but DON'T save to DB yet)
                    val lectureEntities = enrichLecturesInMemory(tempLectures)

                    Napier.d("Successfully enriched ${lectureEntities.size} lecture entities in memory (not saved to DB yet)", tag = TAG)
                    return Result.success(lectureEntities)
                }
                is DualisApiClient.ApiResult.Failure -> {
                    Napier.e("Failed to fetch weekly lectures: ${apiResult.message}", tag = TAG)
                    return Result.failure(Exception(apiResult.message))
                }
            }
        } catch (e: Exception) {
            Napier.e("Error fetching weekly lectures: ${e.message}", e, tag = TAG)
            return Result.failure(e)
        }
    }

    /**
     * Enrich temp lectures with data from individual pages (in memory only, not saved to DB).
     * Returns fully enriched lecture entities with lecturers attached.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun enrichLecturesInMemory(
        tempLectures: List<TempLectureModel>
    ): List<LectureEventEntity> {
        Napier.d("Enriching ${tempLectures.size} lectures with detailed information (in memory)", tag = TAG)

        val lectureEntities = mutableListOf<LectureEventEntity>()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        for (tempLecture in tempLectures) {
            try {
                var fullSubjectName: String? = tempLecture.fullSubjectName
                var lecturers: List<String> = tempLecture.lecturers ?: emptyList()
                var rooms: List<String> = listOf(tempLecture.location)

                Napier.d("Processing lecture: ${tempLecture.shortSubjectName}, initial lecturers: $lecturers, has link: ${tempLecture.linkToIndividualPage != null}", tag = TAG)

                // If we have a link to the individual page, fetch additional details
                if (tempLecture.linkToIndividualPage != null) {
                    val detailsResult = fetchLectureDetails(tempLecture.linkToIndividualPage)
                    if (detailsResult != null) {
                        fullSubjectName = detailsResult.first
                        lecturers = detailsResult.second
                        rooms = detailsResult.third
                        Napier.d("Fetched details - fullName: $fullSubjectName, lecturers: $lecturers", tag = TAG)
                    } else {
                        Napier.w("Failed to fetch details for lecture: ${tempLecture.shortSubjectName}", tag = TAG)
                    }
                }

                // Create lecture event entity (with temporary ID 0)
                val lectureEntity = LectureEventEntity(
                    lectureId = 0, // Temporary - will be assigned when saved
                    shortSubjectName = tempLecture.shortSubjectName ?: "Unknown",
                    fullSubjectName = fullSubjectName,
                    startTime = tempLecture.startTime,
                    endTime = tempLecture.endTime,
                    location = rooms.joinToString(", "),
                    isTest = tempLecture.isTest,
                    fetchedAt = now
                )

                // Set lecturers (transient field)
                lectureEntity.lecturers = lecturers

                lectureEntities.add(lectureEntity)
                Napier.d("Enriched lecture: ${lectureEntity.shortSubjectName} with ${lecturers.size} lecturer(s)", tag = TAG)
            } catch (e: Exception) {
                Napier.e("Error enriching lecture ${tempLecture.shortSubjectName}: ${e.message}", e, tag = TAG)
            }
        }

        return lectureEntities
    }

    /**
     * Save enriched lectures to database.
     * This is called AFTER change detection determines that updates are needed.
     */
    suspend fun saveLecturesToDatabase(
        lectures: List<LectureEventEntity>,
        weekStart: LocalDateTime,
        weekEnd: LocalDateTime
    ): List<LectureEventEntity> {
        Napier.d("💾 Saving ${lectures.size} lectures to database", tag = TAG)

        // Delete old lectures for this week
        try {
            Napier.d("🗑️  Deleting existing lectures in range: $weekStart to $weekEnd", tag = TAG)
            lectureEventDao.deleteInRange(weekStart.toString(), weekEnd.toString())
            Napier.d("✅ Deleted old lectures for the week", tag = TAG)
        } catch (e: Exception) {
            Napier.e("❌ Failed to delete old lectures: ${e.message}", tag = TAG, throwable = e)
        }

        val savedLectures = mutableListOf<LectureEventEntity>()

        for (lecture in lectures) {
            try {
                // Save lecture to database
                val insertedId = lectureEventDao.insert(lecture)
                val savedEntity = lecture.copy(lectureId = insertedId)
                savedLectures.add(savedEntity)

                // Create and save lecturer associations
                val lecturers = lecture.lecturers ?: emptyList()
                if (lecturers.isNotEmpty()) {
                    for (lecturerName in lecturers) {
                        if (lecturerName.isNotBlank()) {
                            val lecturerId = findOrCreateLecturer(lecturerName)
                            val crossRef = LectureLecturerCrossRef(
                                lectureId = insertedId,
                                lecturerId = lecturerId
                            )
                            lectureLecturerCrossRefDao.insert(crossRef)
                            Napier.d("Created association: lecture $insertedId -> lecturer $lecturerId ($lecturerName)", tag = TAG)
                        }
                    }
                    Napier.d("Saved lecture: ${savedEntity.shortSubjectName} with ${lecturers.size} lecturer(s)", tag = TAG)
                } else {
                    Napier.w("No lecturers found for lecture: ${savedEntity.shortSubjectName}", tag = TAG)
                }
            } catch (e: Exception) {
                Napier.e("Error saving lecture ${lecture.shortSubjectName}: ${e.message}", e, tag = TAG)
            }
        }

        Napier.d("✅ Successfully saved ${savedLectures.size} lectures to database", tag = TAG)
        return savedLectures
    }

    /**
     * OLD METHOD - Kept for backward compatibility but deprecated.
     * Enrich temp lectures with data from individual pages and save to database.
     */
    @Deprecated("Use enrichLecturesInMemory + saveLecturesToDatabase instead")
    @OptIn(ExperimentalTime::class)
    private suspend fun enrichAndSaveLectures(
        tempLectures: List<TempLectureModel>
    ): List<LectureEventEntity> {
        Napier.d("Enriching ${tempLectures.size} lectures with detailed information", tag = TAG)

        // Before inserting new lectures, delete all existing lectures for this week
        // to avoid duplicates
        if (tempLectures.isNotEmpty()) {
            val weekStart = tempLectures.minOf { it.startTime }
            val weekEnd = tempLectures.maxOf { it.endTime }
            Napier.d("Deleting existing lectures in range: $weekStart to $weekEnd", tag = TAG)
            try {
                lectureEventDao.deleteInRange(weekStart.toString(), weekEnd.toString())
                Napier.d("Deleted old lectures for the week", tag = TAG)
            } catch (e: Exception) {
                Napier.w("Failed to delete old lectures: ${e.message}", tag = TAG)
            }
        }

        val lectureEntities = mutableListOf<LectureEventEntity>()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        for (tempLecture in tempLectures) {
            try {
                var fullSubjectName: String? = tempLecture.fullSubjectName
                var lecturers: List<String> = tempLecture.lecturers ?: emptyList()
                var rooms: List<String> = listOf(tempLecture.location)

                Napier.d("Processing lecture: ${tempLecture.shortSubjectName}, initial lecturers: $lecturers, has link: ${tempLecture.linkToIndividualPage != null}", tag = TAG)

                // If we have a link to the individual page, fetch additional details
                if (tempLecture.linkToIndividualPage != null) {
                    val detailsResult = fetchLectureDetails(tempLecture.linkToIndividualPage)
                    if (detailsResult != null) {
                        fullSubjectName = detailsResult.first
                        lecturers = detailsResult.second
                        rooms = detailsResult.third
                        Napier.d("Fetched details - fullName: $fullSubjectName, lecturers: $lecturers", tag = TAG)
                    } else {
                        Napier.w("Failed to fetch details for lecture: ${tempLecture.shortSubjectName}", tag = TAG)
                    }
                }

                // Create lecture event entity
                val lectureEntity = LectureEventEntity(
                    lectureId = 0, // Auto-generated
                    shortSubjectName = tempLecture.shortSubjectName ?: "Unknown",
                    fullSubjectName = fullSubjectName,
                    startTime = tempLecture.startTime,
                    endTime = tempLecture.endTime,
                    location = rooms.joinToString(", "),
                    isTest = tempLecture.isTest,
                    fetchedAt = now
                )

                // Save to database
                val insertedId = lectureEventDao.insert(lectureEntity)
                val savedEntity = lectureEntity.copy(lectureId = insertedId)
                lectureEntities.add(savedEntity)

                // Create and save lecturer associations
                if (lecturers.isNotEmpty()) {
                    for (lecturerName in lecturers) {
                        if (lecturerName.isNotBlank()) {
                            val lecturerId = findOrCreateLecturer(lecturerName)
                            val crossRef = LectureLecturerCrossRef(
                                lectureId = insertedId,
                                lecturerId = lecturerId
                            )
                            lectureLecturerCrossRefDao.insert(crossRef)
                            Napier.d("Created association: lecture ${insertedId} -> lecturer ${lecturerId} (${lecturerName})", tag = TAG)
                        }
                    }
                    Napier.d("Saved lecture: ${savedEntity.shortSubjectName} with ${lecturers.size} lecturer(s)", tag = TAG)
                } else {
                    Napier.w("No lecturers found for lecture: ${savedEntity.shortSubjectName}", tag = TAG)
                }
            } catch (e: Exception) {
                Napier.e("Error enriching lecture ${tempLecture.shortSubjectName}: ${e.message}", e, tag = TAG)
            }
        }

        return lectureEntities
    }

    /**
     * Fetch and parse individual lecture page details.
     * Follows the same pattern: Service -> API Client -> Service -> Parser
     */
    private suspend fun fetchLectureDetails(url: String, attemptCount: Int = 0): Triple<String, List<String>, List<String>>? {
        Napier.d("Fetching lecture details from: $url (attempt $attemptCount)", tag = TAG)

        try {
            // Parse URL to extract query parameters
            val urlParameters = parseUrlParameters(url)
            val baseUrl = url.substringBefore("?")

            Napier.d("Parsed URL - base: $baseUrl, params: $urlParameters", tag = TAG)

            // Get cookie for request
            val authData = sessionManager.getAuthData()
            val rawCookie = authData?.cookie
            val cookie = rawCookie?.substringBefore(";")

            // Fetch via API client
            when (val apiResult = apiClient.get(baseUrl, urlParameters, cookie)) {
                is DualisApiClient.ApiResult.Success -> {
                    val htmlContent = apiResult.htmlContent
                    Napier.d("Received HTML content (${htmlContent.length} chars)", tag = TAG)

                    // Check for errors (likely session expired)
                    if (htmlParser.isErrorPage(htmlContent)) {
                        Napier.w("Individual page returned error, attempting re-authentication", tag = TAG)
                        
                        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                            Napier.e("Max retry attempts reached for lecture details", tag = TAG)
                            return null
                        }

                        val reAuthResult = reAuthenticate()
                        if (reAuthResult.isFailure) {
                            Napier.e("Re-authentication failed for lecture details", tag = TAG)
                            return null
                        }

                        // Retry the request
                        return fetchLectureDetails(url, attemptCount + 1)
                    }

                    // Parse via parser
                    val result = timetableParser.parseIndividualPage(htmlContent)
                    if (result != null) {
                        Napier.d("Successfully parsed individual page: ${result.first} with ${result.second.size} lecturer(s): ${result.second} and rooms: ${result.third}", tag = TAG)
                    } else {
                        Napier.w("Parser returned null for individual page", tag = TAG)
                    }
                    return result
                }
                is DualisApiClient.ApiResult.Failure -> {
                    Napier.w("Failed to fetch lecture details: ${apiResult.message}", tag = TAG)
                    return null
                }
            }
        } catch (e: Exception) {
            Napier.e("Error fetching lecture details: ${e.message}", e, tag = TAG)
            return null
        }
    }

    /**
     * Find an existing lecturer by name or create a new one.
     */
    private suspend fun findOrCreateLecturer(lecturerName: String): Long {
        // Search for existing lecturer
        val existingLecturers = lecturerDao.searchByName(lecturerName)

        if (existingLecturers.isNotEmpty()) {
            Napier.d("Found existing lecturer: $lecturerName", tag = TAG)
            return existingLecturers.first().lecturerId
        }

        // Create new lecturer
        val newLecturer = LecturerEntity(
            lecturerId = 0, // Auto-generated
            lecturerName = lecturerName
        )

        val insertedId = lecturerDao.insert(newLecturer)
        Napier.d("Created new lecturer: $lecturerName with ID: $insertedId", tag = TAG)

        return insertedId
    }

    /**
     * Attempt to re-authenticate using stored credentials.
     */
    private suspend fun reAuthenticate(): Result<Unit> {
        if (sessionManager.isReAuthenticating()) {
            return Result.failure(Exception("Re-authentication already in progress"))
        }

        sessionManager.setReAuthenticating(true)
        try {
            Napier.d("Attempting re-authentication", tag = TAG)

            // Clear cached auth data to force fresh login
            sessionManager.clearAuthData()

            val credentials = sessionManager.getStoredCredentials()

            if (credentials == null) {
                return Result.failure(Exception("No stored credentials available"))
            }

            val (username, password) = credentials
            val loginResult = authenticationService.login(username, password)

            return when (loginResult) {
                is LoginResult.Success -> {
                    Napier.d("Re-authentication successful", tag = TAG)
                    Result.success(Unit)
                }
                is LoginResult.Failure -> {
                    Napier.e("Re-authentication failed: ${loginResult.message}", tag = TAG)
                    Result.failure(Exception(loginResult.message))
                }
            }
        } finally {
            sessionManager.setReAuthenticating(false)
        }
    }

    /**
     * Parse URL parameters from a full URL string.
     * Handles HTML-encoded URLs (replaces &amp; with &).
     */
    private fun parseUrlParameters(url: String): Map<String, String> {
        val parameters = mutableMapOf<String, String>()

        // Decode HTML entities first (e.g., &amp; -> &)
        val decodedUrl = url.replace("&amp;", "&")

        val queryString = decodedUrl.substringAfter("?", "")
        if (queryString.isEmpty()) {
            return parameters
        }

        queryString.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                parameters[parts[0]] = parts[1]
            }
        }

        return parameters
    }

    /**
     * Fetch lectures for a specific week range.
     */
    suspend fun getWeeklyLecturesForWeek(start: LocalDateTime, end: LocalDateTime): Result<List<LectureEventEntity>> {
        Napier.d("Fetching weekly lectures for week: $start to $end", tag = TAG)

        // Use the start date to fetch the week
        return getWeeklyLecturesForDate(start.date)
    }

    /**
     * Fetch only the weekly skeleton (overview) without hitting individual lecture pages.
     * Returns basic LectureEventEntity list (no lecturers, rooms from weekly grid, maybe short/full as available).
     * Nothing is saved to the database here.
     */
    suspend fun getWeeklySkeletonForWeek(start: LocalDateTime, end: LocalDateTime): Result<List<LectureEventEntity>> {
        return getWeeklySkeletonForDate(start.date)
    }

    private suspend fun getWeeklySkeletonForDate(date: LocalDate): Result<List<LectureEventEntity>> {
        Napier.d("Fetching weekly skeleton for date: $date", tag = TAG)

        // Check authentication
        if (!sessionManager.isAuthenticated() && !sessionManager.isDemoMode()) {
            Napier.w("No active session, need to authenticate first", tag = TAG)
            val reAuthResult = reAuthenticate()
            if (reAuthResult.isFailure) {
                return Result.failure(reAuthResult.exceptionOrNull()!!)
            }
        }

        // Handle demo mode similar to full flow: just return demo lectures as already basic
        if (sessionManager.isDemoMode()) {
            Napier.d("Demo mode active, returning demo lectures as skeleton", tag = TAG)
            val demoStartDate = LocalDateTime(date.year, date.month, date.day, 0, 0, 0)
            val demoLectures = DemoDataProvider.generateDemoLecturesForWeek(demoStartDate)
            return Result.success(demoLectures)
        }

        try {
            val authData = sessionManager.getAuthData() ?: return Result.failure(Exception("No auth data available"))
            val dateString = "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}.${date.year}"
            val urlParameters = mapOf(
                "APPNAME" to "CampusNet",
                "PRGNAME" to "SCHEDULER",
                "ARGUMENTS" to "-N${authData.sessionId},-N000028,-A$dateString,-A,-N1,-N000000000000000"
            )

            // Clean cookie
            val rawCookie = authData.cookie
            val cookie = rawCookie?.substringBefore(";")

            when (val apiResult = apiClient.get(BASE_URL, urlParameters, cookie)) {
                is DualisApiClient.ApiResult.Success -> {
                    val htmlContent = apiResult.htmlContent

                    if (htmlParser.isErrorPage(htmlContent) || !htmlParser.isValidTimetablePage(htmlContent)) {
                        Napier.w("Invalid/expired timetable page when fetching skeleton, try re-auth", tag = TAG)
                        val reAuthResult = reAuthenticate()
                        if (reAuthResult.isFailure) {
                            return Result.failure(reAuthResult.exceptionOrNull()!!)
                        }
                        // Retry once after re-auth
                        return getWeeklySkeletonForDate(date)
                    }

                    // Parse only weekly view
                    val tempLectures = timetableParser.parseWeeklyView(htmlContent)
                    val basicEntities = tempLecturesToBasicEntities(tempLectures)
                    Napier.d("Skeleton contains ${basicEntities.size} items", tag = TAG)
                    return Result.success(basicEntities)
                }
                is DualisApiClient.ApiResult.Failure -> {
                    return Result.failure(Exception(apiResult.message))
                }
            }
        } catch (e: Exception) {
            Napier.e("Error fetching weekly skeleton: ${e.message}", e, tag = TAG)
            return Result.failure(e)
        }
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
