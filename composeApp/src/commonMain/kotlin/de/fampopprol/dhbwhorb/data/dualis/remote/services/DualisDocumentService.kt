package de.fampopprol.dhbwhorb.data.dualis.remote.services

import de.fampopprol.dhbwhorb.data.dualis.models.DualisDocument
import de.fampopprol.dhbwhorb.data.dualis.remote.DualisApiClient
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.DocumentParser
import de.fampopprol.dhbwhorb.data.dualis.remote.parser.HtmlParser
import de.fampopprol.dhbwhorb.data.dualis.remote.session.SessionManager
import io.github.aakira.napier.Napier

class DualisDocumentService(
    private val apiClient: DualisApiClient,
    private val sessionManager: SessionManager,
    private val authenticationService: AuthenticationService,
    private val documentParser: DocumentParser,
    private val htmlParser: HtmlParser
) {
    companion object {
        private const val TAG = "DualisDocumentService"
        private const val BASE_URL = "https://dualis.dhbw.de/scripts/mgrqispi.dll"
        private const val MAX_RETRY_ATTEMPTS = 2
    }

    /**
     * Returns true if we can attempt loading: authenticated, demo mode, or credentials available for re-auth.
     */
    fun hasCredentialsOrSession(): Boolean {
        return sessionManager.isAuthenticated() || sessionManager.isDemoMode() || sessionManager.getStoredCredentials() != null
    }

    /**
     * Fetches documents from the Dualis portal.
     * Handles session expiration and re-authentication automatically.
     */
    suspend fun fetchDocuments(): Result<List<DualisDocument>> {
        return fetchDocumentsWithRetry(0)
    }

    private suspend fun fetchDocumentsWithRetry(attemptCount: Int): Result<List<DualisDocument>> {
        Napier.d("Fetching documents (attempt $attemptCount)", tag = TAG)

        if (!sessionManager.isAuthenticated() && !sessionManager.isDemoMode()) {
            val reAuthResult = reAuthenticate()
            if (reAuthResult.isFailure) {
                return Result.failure(reAuthResult.exceptionOrNull()!!)
            }
        }

        // Handle demo mode
        if (sessionManager.isDemoMode()) {
            Napier.d("Returning demo documents", tag = TAG)
            return Result.success(
                listOf(
                    DualisDocument(
                        title = "Studienbescheinigung",
                        date = "25.03.26",
                        time = "09:40",
                        downloadUrl = "/scripts/filetransfer.exe?demo_cert"
                    ),
                    DualisDocument(
                        title = "Zahlungsinformation Semesterbeiträge",
                        date = "19.02.26",
                        time = "14:47",
                        downloadUrl = "/scripts/filetransfer.exe?demo_payment"
                    ),
                    DualisDocument(
                        title = "Semesternotenbescheid - Download",
                        date = "11.02.26",
                        time = "15:52",
                        downloadUrl = "/scripts/filetransfer.exe?demo_grades"
                    )
                )
            )
        }

        try {
            val authData = sessionManager.getAuthData() ?: return Result.failure(Exception("No auth data"))

            if (authData.sessionId.isEmpty()) {
                Napier.e("Session ID is empty!", tag = TAG)
                return Result.failure(Exception("Empty session ID"))
            }

            // URL structure from RESEARCH.md: ?APPNAME=CampusNet&PRGNAME=CREATEDOCUMENT&ARGUMENTS=-N{sessionId},-N000339
            val fullUrl = "$BASE_URL?APPNAME=CampusNet&PRGNAME=CREATEDOCUMENT&ARGUMENTS=-N${authData.sessionId},-N000339"
            Napier.d("Fetching documents with URL: $fullUrl", tag = TAG)

            // Clean cookie
            val rawCookie = authData.cookie
            val cookie = rawCookie?.substringBefore(";")

            when (val apiResult = apiClient.get(fullUrl, emptyMap(), cookie)) {
                is DualisApiClient.ApiResult.Success -> {
                    val htmlContent = apiResult.htmlContent

                    if (htmlParser.isErrorPage(htmlContent) || !isValidDocumentPage(htmlContent)) {
                        val title = htmlParser.extractTitle(htmlContent)
                        Napier.w("Invalid document page received. Title: '$title'", tag = TAG)
                        
                        if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                            return Result.failure(Exception("Max retry attempts reached. Page title: $title"))
                        }
                        
                        val reAuthResult = reAuthenticate()
                        if (reAuthResult.isFailure) {
                            return Result.failure(reAuthResult.exceptionOrNull()!!)
                        }
                        return fetchDocumentsWithRetry(attemptCount + 1)
                    }

                    val documents = documentParser.parseDocuments(htmlContent)
                    return Result.success(documents)
                }
                is DualisApiClient.ApiResult.Failure -> {
                    return Result.failure(Exception(apiResult.message))
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Downloads a document from the Dualis portal using the given URL.
     * Handles session expiration and re-authentication automatically.
     */
    suspend fun downloadDocument(url: String): Result<ByteArray> {
        return downloadDocumentWithRetry(url, 0)
    }

    private suspend fun downloadDocumentWithRetry(url: String, attemptCount: Int): Result<ByteArray> {
        // Convert relative URLs to absolute (Dualis returns relative paths like /scripts/filetransfer.exe?...)
        val absoluteUrl = if (url.startsWith("/")) "https://dualis.dhbw.de$url" else url

        Napier.d("Downloading document from: $absoluteUrl (attempt $attemptCount)", tag = TAG)

        if (!sessionManager.isAuthenticated() && !sessionManager.isDemoMode()) {
            val reAuthResult = reAuthenticate()
            if (reAuthResult.isFailure) {
                return Result.failure(reAuthResult.exceptionOrNull()!!)
            }
        }

        if (sessionManager.isDemoMode()) {
            return Result.failure(Exception("Document download not available in demo mode"))
        }

        try {
            val authData = sessionManager.getAuthData() ?: return Result.failure(Exception("No auth data"))

            if (authData.sessionId.isEmpty()) {
                Napier.e("Session ID is empty!", tag = TAG)
                return Result.failure(Exception("Empty session ID"))
            }

            // Clean cookie
            val rawCookie = authData.cookie
            val cookie = rawCookie?.substringBefore(";")

            val result = apiClient.getRawBytes(absoluteUrl, cookie)

            return result.fold(
                onSuccess = { bytes ->
                    Result.success(bytes)
                },
                onFailure = { exception ->
                    if (exception.message?.contains("401") == true && attemptCount < MAX_RETRY_ATTEMPTS) {
                        Napier.w("Download failed due to possible session expiration, attempting re-authentication.", tag = TAG)
                        val reAuthResult = reAuthenticate()
                        if (reAuthResult.isFailure) {
                            return Result.failure(reAuthResult.exceptionOrNull()!!)
                        }
                        return downloadDocumentWithRetry(absoluteUrl, attemptCount + 1)
                    }
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun isValidDocumentPage(htmlContent: String): Boolean {
        // A valid document page should have the documents table (class "tb")
        // and ideally some header like "Dokumente" or similar.
        // Based on RESEARCH.md, it has table class="tb".
        return htmlContent.contains("class=\"tb\"", ignoreCase = true) && !htmlParser.isRedirectPage(htmlContent)
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
