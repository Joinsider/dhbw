package de.joinside.dhbw.data.dualis.remote.services

import de.joinside.dhbw.data.dualis.remote.DualisApiClient
import de.joinside.dhbw.data.dualis.remote.parser.GradeParser
import de.joinside.dhbw.data.dualis.remote.parser.HtmlParser
import de.joinside.dhbw.data.dualis.remote.session.SessionManager
import de.joinside.dhbw.data.storage.database.dao.grades.GradeDao
import de.joinside.dhbw.data.storage.database.entities.grades.GradeEntity
import io.github.aakira.napier.Napier

class DualisGradeService(
    private val apiClient: DualisApiClient,
    private val sessionManager: SessionManager,
    private val authenticationService: AuthenticationService,
    private val gradeParser: GradeParser,
    private val htmlParser: HtmlParser,
    private val gradeDao: GradeDao
) {
    companion object {
        private const val TAG = "DualisGradeService"
        private const val BASE_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val MAX_RETRY_ATTEMPTS = 2
    }

    suspend fun getSemesters(): Result<Map<String, String>> {
        return fetchSemestersWithRetry(0)
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

    suspend fun getGradesForSemester(semesterId: String, semesterName: String): Result<List<GradeEntity>> {
        return fetchGradesWithRetry(semesterId, semesterName, 0)
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
