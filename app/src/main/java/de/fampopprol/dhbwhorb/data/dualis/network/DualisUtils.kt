/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

/**
 * Utility class for common Dualis operations
 */
object DualisUtils {

    /**
     * Validates if authentication token is present and valid
     */
    fun isValidAuthToken(authToken: String?): Boolean {
        return !authToken.isNullOrEmpty() && authToken.matches(Regex("[0-9]{15}"))
    }
}
