/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.joinside.dhbw.testutil

import de.joinside.dhbw.data.dualis.remote.models.AuthData

/**
 * Mock session manager for testing.
 * Returns controlled auth data without accessing secure storage.
 */
class MockSessionManager(
    private var mockAuthData: AuthData? = null,
    private var mockIsDemo: Boolean = false
) {

    fun storeCredentials(username: String, password: String) {
        // Mock implementation - no-op
    }

    fun getStoredCredentials(): Pair<String, String>? {
        return null
    }

    fun hasStoredCredentials(): Boolean {
        return false
    }

    fun storeAuthData(authData: AuthData) {
        mockAuthData = authData
    }

    fun getAuthData(): AuthData? {
        return mockAuthData
    }

    fun isAuthenticated(): Boolean {
        return mockAuthData != null && (mockAuthData!!.sessionId.isNotEmpty() || mockAuthData!!.authToken.isNotEmpty())
    }

    fun isDemoUser(username: String, password: String): Boolean {
        return false
    }

    fun setDemoMode(enabled: Boolean) {
        mockIsDemo = enabled
    }

    fun isDemoMode(): Boolean {
        return mockIsDemo
    }

    fun isReAuthenticating(): Boolean {
        return false
    }

    fun setReAuthenticating(value: Boolean) {
        // Mock implementation - no-op
    }

    fun logout() {
        mockAuthData = null
        mockIsDemo = false
    }

    fun clearAuthData() {
        mockAuthData = null
    }

    fun setMockAuthData(authData: AuthData?) {
        mockAuthData = authData
    }
}

