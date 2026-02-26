package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.GradeParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeDao
import de.fampopprol.dhbwhorb.data.storage.database.dao.grades.GradeCacheMetadataDao
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeCacheMetadata
import de.fampopprol.dhbwhorb.data.storage.database.entities.grades.GradeEntity
import io.github.aakira.napier.Napier
import kotlin.time.ExperimentalTime

class DualisGradeService(
    private val apiClient: DualisApiClient,
    private val sessionManager: SessionManager,
    private val authenticationService: AuthenticationService,
    private val gradeParser: GradeParser,
    private val htmlParser: HtmlParser,
    private val gradeDao: GradeDao,
    private val gradeCacheMetadataDao: GradeCacheMetadataDao
) {
    companion object {
        private const val TAG = "DualisGradeService"
        private const val BASE_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val MAX_RETRY_ATTEMPTS = 2
        private const val CACHE_VALIDITY_DURATION_MS = 60 * 60 * 1000L // 1 hour in milliseconds
    }

    /**
     * Returns true if we can attempt loading: authenticated, demo mode, or credentials available for re-auth.
     */
    fun hasCredentialsOrSession(): Boolean {
        return sessionManager.isAuthenticated() || sessionManager.isDemoMode() || sessionManager.getStoredCredentials() != null
    }

    suspend fun getSemesters(): Result<Map<String, String>> {
        return fetchSemestersWithRetry(0)
    }

    /**
     * Check if cached grades for a semester are still valid (less than 1 hour old).
     */
    @OptIn(ExperimentalTime::class)
    suspend fun isCacheValid(studentId: String, semesterId: String): Boolean {
        val metadata = gradeCacheMetadataDao.getMetadata(studentId, semesterId) ?: return false
        val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val cacheAge = currentTime - metadata.lastUpdatedTimestamp
        return cacheAge < CACHE_VALIDITY_DURATION_MS
    }

    /**
     * Get cached grades for a semester from the database.
     */
    suspend fun getCachedGrades(studentId: String, semesterId: String): List<GradeEntity> {
        return gradeDao.getGradesForSemester(studentId, semesterId)
    }

    /**
     * Get grades for a semester. Uses cache if valid, otherwise fetches from network.
     * @param forceRefresh If true, always fetch from network regardless of cache validity
     */
    suspend fun getGradesForSemester(
        semesterId: String,
        semesterName: String,
        forceRefresh: Boolean = false
    ): Result<List<GradeEntity>> {
        val studentId = sessionManager.getStoredCredentials()?.first ?: "unknown"

        // Check cache validity and return cached data if valid and not forcing refresh
        if (!forceRefresh && isCacheValid(studentId, semesterId)) {
            val cachedGrades = getCachedGrades(studentId, semesterId)
            if (cachedGrades.isNotEmpty()) {
                Napier.d("Returning cached grades for semester $semesterId (${cachedGrades.size} grades)", tag = TAG)
                return Result.success(cachedGrades)
            }
        }

        // Fetch from network
        Napier.d("Fetching grades from network for semester $semesterId", tag = TAG)
        return fetchGradesWithRetry(semesterId, semesterName, 0)
    }

    private suspend fun fetchSemestersWithRetry(attemptCount: Int): Result<Map<String, String>> {
        Napier.d("Fetching semesters (attempt $attemptCount)", tag = TAG)

        if (!sessionManager.isAuthenticated() && !sessionManager.isDemoMode()) {
            val reAuthResult = reAuthenticate()
            if (reAuthResult.isFailure) {
                return Result.failure(reAuthResult.exceptionOrNull()!!)
            }
        }

        // Handle demo mode (optional, can add later)

        try {
            val authData = sessionManager.getAuthData() ?: return Result.failure(Exception("No auth data"))

            val fullUrl = "$BASE_URL?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N${authData.sessionId},-N000307,"
            Napier.d("Fetching semesters with URL: $fullUrl", tag = TAG)

            // Clean cookie (remove attributes like ; HttpOnly)
            val rawCookie = authData.cookie
            val cookie = rawCookie?.substringBefore(";")
            if (cookie != null) {
                 Napier.d("Using cookie: $cookie", tag = TAG)
            }

            when (val apiResult = apiClient.get(fullUrl, emptyMap(), cookie)) {
                is DualisApiClient.ApiResult.Success -> {
                    val htmlContent = apiResult.htmlContent

                    if (htmlParser.isErrorPage(htmlContent) || !htmlParser.isValidGradePage(htmlContent)) {
                        val title = htmlParser.extractTitle(htmlContent)
                        val snippet = htmlContent.take(500)
                        Napier.w("Invalid grade page received. Title: '$title', Snippet: $snippet", tag = TAG)
                        
                        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                            return Result.failure(Exception("Max retry attempts reached. Page title: $title"))
                        }
                        val reAuthResult = reAuthenticate()
                        if (reAuthResult.isFailure) {
                            return Result.failure(reAuthResult.exceptionOrNull()!!)
                        }
                        return fetchSemestersWithRetry(attemptCount + 1)
                    }

                    val semesters = gradeParser.parseSemesterList(htmlContent)
                    return Result.success(semesters)
                }
                is DualisApiClient.ApiResult.Failure -> {
                    return Result.failure(Exception(apiResult.message))
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }


    private suspend fun fetchGradesWithRetry(
        semesterId: String,
        semesterName: String,
        attemptCount: Int
    ): Result<List<GradeEntity>> {
        Napier.d("Fetching grades for semester $semesterId (attempt $attemptCount)", tag = TAG)

        if (!sessionManager.isAuthenticated() && !sessionManager.isDemoMode()) {
            val reAuthResult = reAuthenticate()
            if (reAuthResult.isFailure) {
                return Result.failure(reAuthResult.exceptionOrNull()!!)
            }
        }

        try {
            val authData = sessionManager.getAuthData() ?: return Result.failure(Exception("No auth data"))
            // Get student ID (username) from stored credentials as a stable ID
            val studentId = sessionManager.getStoredCredentials()?.first ?: "unknown"

            if (authData.sessionId.isEmpty()) {
                Napier.e("Session ID is empty!", tag = TAG)
                return Result.failure(Exception("Empty session ID"))
            }

            val fullUrl = "$BASE_URL?APPNAME=CampusNet&PRGNAME=COURSERESULTS&ARGUMENTS=-N${authData.sessionId},-N000307,-N$semesterId"
            Napier.d("Fetching grades with URL: $fullUrl", tag = TAG)

            // Clean cookie
            val rawCookie = authData.cookie
            val cookie = rawCookie?.substringBefore(";")

            when (val apiResult = apiClient.get(fullUrl, emptyMap(), cookie)) {
                is DualisApiClient.ApiResult.Success -> {
                    val htmlContent = apiResult.htmlContent

                    if (htmlParser.isErrorPage(htmlContent) || !htmlParser.isValidGradePage(htmlContent)) {
                         val title = htmlParser.extractTitle(htmlContent)
                         val snippet = htmlContent.take(500)
                         Napier.w("Invalid grade page for semester $semesterId. Title: '$title', Snippet: $snippet", tag = TAG)

                        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                            return Result.failure(Exception("Max retry attempts reached"))
                        }
                        val reAuthResult = reAuthenticate()
                        if (reAuthResult.isFailure) {
                            return Result.failure(reAuthResult.exceptionOrNull()!!)
                        }
                        return fetchGradesWithRetry(semesterId, semesterName, attemptCount + 1)
                    }

                    val grades = gradeParser.parseGrades(htmlContent, studentId, semesterId, semesterName)
                    
                    // Change detection and saving
                    saveGradesWithChangeDetection(grades, studentId, semesterId)

                    return Result.success(grades)
                }
                is DualisApiClient.ApiResult.Failure -> {
                    return Result.failure(Exception(apiResult.message))
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun saveGradesWithChangeDetection(
        newGrades: List<GradeEntity>,
        studentId: String,
        semesterId: String
    ) {
        try {
            // Change detection logic
            val existingGrades = gradeDao.getGradesForSemester(studentId, semesterId)
            val existingMap = existingGrades.associateBy { it.moduleNumber }

            for (newGrade in newGrades) {
                val oldGrade = existingMap[newGrade.moduleNumber]
                if (oldGrade != null) {
                    // Check if grade changed (e.g. from null to "1,3")
                    if (oldGrade.grade != newGrade.grade) {
                        Napier.i("Grade changed for ${newGrade.moduleName}: ${oldGrade.grade} -> ${newGrade.grade}", tag = TAG)
                        // TODO: Trigger notification
                    }
                }
            }

            // Save to DB (replace logic handled by DAO via DELETE then INSERT or OnConflict.REPLACE)
            // Since we want to handle deletions (if a module disappears?), we might want to delete old ones first.
            // GradeDao has deleteGradesForSemester.
            
            gradeDao.deleteGradesForSemester(studentId, semesterId)
            gradeDao.insertAll(newGrades)
            Napier.d("Saved ${newGrades.size} grades to DB", tag = TAG)

            // Save cache metadata
            val metadata = GradeCacheMetadata(
                key = "grades_${studentId}_$semesterId",
                lastUpdatedTimestamp = kotlin.time.Clock.System.now().toEpochMilliseconds(),
                studentId = studentId,
                semesterId = semesterId
            )
            gradeCacheMetadataDao.insert(metadata)
            Napier.d("Updated cache metadata for semester $semesterId", tag = TAG)

        } catch (e: Exception) {
            Napier.e("Error saving grades: ${e.message}", e, tag = TAG)
        }
    }

    private suspend fun reAuthenticate(): Result<Unit> {
        if (sessionManager.isReAuthenticating()) {
            return Result.failure(Exception("Re-authentication already in progress"))
        }

        sessionManager.setReAuthenticating(true)
        try {
            Napier.d("Attempting re-authentication", tag = TAG)
            sessionManager.clearAuthData()

            val credentials = sessionManager.getStoredCredentials()
                ?: return Result.failure(Exception("No stored credentials available"))

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
}
