package de.joinside.dhbw.data.dualis.remote.services

import de.joinside.dhbw.data.dualis.remote.DualisApiClient
import de.joinside.dhbw.data.dualis.remote.parser.HtmlParser
import de.joinside.dhbw.data.dualis.remote.parser.TimetableParser
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.storage.database.dao.timetable.LectureEventDao
import de.joinside.dhbw.data.storage.database.dao.timetable.LecturerDao
import de.joinside.dhbw.data.storage.database.entities.timetable.LectureEventEntity
import de.joinside.dhbw.data.storage.database.entities.timetable.LecturerEntity
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
    private val lecturerDao: LecturerDao
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
            Napier.d("Demo mode active, returning empty list", tag = TAG)
            return Result.success(emptyList())
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
            val apiResult = apiClient.get(BASE_URL, urlParameters)

            when (apiResult) {
                is DualisApiClient.ApiResult.Success -> {
                    val htmlContent = apiResult.htmlContent

                    // Step 2: Validate HTML
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

                    // Step 3: Parse HTML
                    Napier.d("Parsing weekly timetable HTML", tag = TAG)
                    val tempLectures = timetableParser.parseWeeklyView(htmlContent)
                    Napier.d("Parsed ${tempLectures.size} lectures from weekly view", tag = TAG)

                    // Step 4: Enrich with individual page data and save
                    val lectureEntities = enrichAndSaveLectures(tempLectures)

                    Napier.d("Successfully processed ${lectureEntities.size} lecture entities", tag = TAG)
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
     * Enrich temp lectures with data from individual pages and save to database.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun enrichAndSaveLectures(
        tempLectures: List<de.joinside.dhbw.data.dualis.remote.parser.temp_models.TempLectureModel>
    ): List<LectureEventEntity> {
        Napier.d("Enriching ${tempLectures.size} lectures with detailed information", tag = TAG)

        val lectureEntities = mutableListOf<LectureEventEntity>()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        for (tempLecture in tempLectures) {
            try {
                var fullSubjectName: String? = tempLecture.fullSubjectName
                var lecturers: List<String> = tempLecture.lecturers ?: emptyList()

                // If we have a link to the individual page, fetch additional details
                if (tempLecture.linkToIndividualPage != null) {
                    val detailsResult = fetchLectureDetails(tempLecture.linkToIndividualPage)
                    if (detailsResult != null) {
                        fullSubjectName = detailsResult.first
                        lecturers = detailsResult.second
                    }
                }

                // Find or create lecturer entity
                val lecturerId = if (lecturers.isNotEmpty()) {
                    findOrCreateLecturer(lecturers.first())
                } else {
                    null
                }

                // Create lecture event entity
                val lectureEntity = LectureEventEntity(
                    lectureId = 0, // Auto-generated
                    shortSubjectName = tempLecture.shortSubjectName ?: "Unknown",
                    fullSubjectName = fullSubjectName,
                    startTime = tempLecture.startTime,
                    endTime = tempLecture.endTime,
                    location = tempLecture.location,
                    isTest = tempLecture.isTest,
                    lecturerId = lecturerId,
                    fetchedAt = now
                )

                // Save to database
                val insertedId = lectureEventDao.insert(lectureEntity)
                val savedEntity = lectureEntity.copy(lectureId = insertedId)
                lectureEntities.add(savedEntity)

                Napier.d("Saved lecture: ${savedEntity.shortSubjectName}", tag = TAG)
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
    private suspend fun fetchLectureDetails(url: String, attemptCount: Int = 0): Pair<String, List<String>>? {
        Napier.d("Fetching lecture details from: $url (attempt $attemptCount)", tag = TAG)

        try {
            // Parse URL to extract query parameters
            val urlParameters = parseUrlParameters(url)
            val baseUrl = url.substringBefore("?")

            // Fetch via API client
            val apiResult = apiClient.get(baseUrl, urlParameters)

            when (apiResult) {
                is DualisApiClient.ApiResult.Success -> {
                    val htmlContent = apiResult.htmlContent

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
                    return timetableParser.parseIndividualPage(htmlContent)
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
     */
    private fun parseUrlParameters(url: String): Map<String, String> {
        val parameters = mutableMapOf<String, String>()

        val queryString = url.substringAfter("?", "")
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
}

