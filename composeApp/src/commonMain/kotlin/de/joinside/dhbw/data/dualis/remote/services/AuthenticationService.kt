package de.joinside.dhbw.data.dualis.remote.services

import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess

/**
 * * Service zur Authentifizierung bei Dualis.
 * *
 */
class AuthenticationService(
    private val client: HttpClient = HttpClient {
        expectSuccess = false // Manuelle Response-Validierung
    }
) {

    /**
     * Login bei Dualis mit Benutzername und Passwort.
     */
    suspend fun login(username: String, password: String): Boolean {
        Napier.d("=== STARTING LOGIN PROCESS ===", tag = "DualisAuthenticationService")
        Napier.d("Username: $username", tag = "DualisAuthenticationService")
        Napier.d("Password length: ${password.length}", tag = "DualisAuthenticationService")

        if(username == "demo@dhbw.de" && password == "demopassword") {
            Napier.d("Demo user detected, enabling demo mode", tag = "DualisAuthenticationService")
            return true
        }

        return try {
            val response: HttpResponse = client.submitForm(
                url = "https://dualis.dhbw.de/scripts/mgrqispi.dll",
                formParameters = Parameters.build {
                    append("usrname", username)
                    append("pass", password)
                    append("APPNAME", "CampusNet")
                    append("PRGNAME", "LOGINCHECK")
                    append("ARGUMENTS", "clino,usrname,pass,menuno,menu_type,browser,platform")
                    append("clino", "000000000000001")
                    append("menuno", "000324")
                    append("menu_type", "classic")
                    append("browser", "")
                    append("platform", "")
                }
            )

            val responseBody = response.bodyAsText()
            val isSuccess = response.status.isSuccess()

            Napier.d("Response status: ${response.status}", tag = "DualisAuthenticationService")
            Napier.d("Login successful: $isSuccess", tag = "DualisAuthenticationService")

            // Prüfe auf erfolgreichen Login (Status 2xx und keine Error-Hinweise in der Response)
            isSuccess && !responseBody.contains("LOGINCHECK", ignoreCase = true)

        } catch (e: Exception) {
            Napier.e("Login failed with exception: ${e.message}", tag = "DualisAuthenticationService")
            false
        }
    }

    fun close() {
        client.close()
    }

    companion object
}
