/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.cache

/**
 * Interface defining common cache management operations
 */
interface CacheManager {
    /**
     * Default cache expiration time in hours
     */
    companion object {
        const val DEFAULT_CACHE_EXPIRY_HOURS = 24 // Cache expires after 24 hours
    }

    /**
     * Clear all cached data
     */
    fun clearCache()

    /**
     * Check if the cache is expired based on its timestamp
     *
     * @param timestamp The time when the cache was last updated
     * @param expiryHours Number of hours after which the cache is considered expired
     * @return True if the cache is still valid, false if expired
     */
    fun isCacheValid(timestamp: Long, expiryHours: Int = DEFAULT_CACHE_EXPIRY_HOURS): Boolean
}
