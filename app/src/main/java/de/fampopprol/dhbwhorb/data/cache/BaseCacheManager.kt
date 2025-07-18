/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.cache

import android.content.Context
import android.util.Log
import com.google.gson.Gson

/**
 * Base abstract class implementing common cache management functionality
 */
abstract class BaseCacheManager(protected val context: Context) : CacheManager {
    protected val gson = Gson()
    protected abstract val tag: String

    /**
     * Check if the cache is valid based on its timestamp
     *
     * @param timestamp The time when the cache was last updated
     * @param expiryHours Number of hours after which the cache is considered expired
     * @return True if the cache is still valid, false if expired
     */
    override fun isCacheValid(timestamp: Long, expiryHours: Int): Boolean {
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - timestamp
        val cacheExpiryMillis = expiryHours * 60 * 60 * 1000L

        val isValid = cacheAge <= cacheExpiryMillis
        if (!isValid) {
            Log.d(tag, "Cache expired (age: ${cacheAge / 1000 / 60} minutes)")
        }
        return isValid
    }
}
