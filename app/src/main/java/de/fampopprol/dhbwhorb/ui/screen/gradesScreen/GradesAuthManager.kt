/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.ui.screen.gradesScreen

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.network.DualisService
import de.fampopprol.dhbwhorb.data.security.CredentialManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles authentication logic for the grades screen
 */
class GradesAuthManager(
    private val dualisService: DualisService,
    private val credentialManager: CredentialManager?,
    private val scope: CoroutineScope
) {

    enum class AuthResult {
        SUCCESS,
        FAILED,
        NO_CREDENTIALS,
        NO_STORED_CREDENTIALS
    }

    /**
     * Ensures the user is authenticated before proceeding with data operations
     * @param onResult Callback with the authentication result
     */
    fun ensureAuthentication(onResult: (AuthResult) -> Unit) {
        // If already authenticated, return success immediately
        if (dualisService.isAuthenticated()) {
            onResult(AuthResult.SUCCESS)
            return
        }

        // Check if we have stored credentials to re-authenticate
        if (credentialManager == null) {
            onResult(AuthResult.NO_CREDENTIALS)
            return
        }

        scope.launch {
            val hasStoredCredentials = credentialManager.hasStoredCredentialsBlocking()
            if (hasStoredCredentials) {
                val username = credentialManager.getUsernameBlocking()
                val password = credentialManager.getPassword()

                if (username != null && password != null) {
                    Log.d("GradesAuthManager", "Re-authenticating with stored credentials")
                    dualisService.login(username, password) { result ->
                        if (result != null) {
                            Log.d("GradesAuthManager", "Re-authentication successful")
                            onResult(AuthResult.SUCCESS)
                        } else {
                            Log.e("GradesAuthManager", "Re-authentication failed")
                            onResult(AuthResult.FAILED)
                        }
                    }
                } else {
                    Log.e("GradesAuthManager", "No valid stored credentials found")
                    onResult(AuthResult.NO_STORED_CREDENTIALS)
                }
            } else {
                Log.e("GradesAuthManager", "No stored credentials available")
                onResult(AuthResult.NO_STORED_CREDENTIALS)
            }
        }
    }
}
