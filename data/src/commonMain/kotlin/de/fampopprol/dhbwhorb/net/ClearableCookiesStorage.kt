/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.net

import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ktor's in-memory cookie storage, with a way to empty it.
 *
 * The session cookie Dualis sets lives in the shared [io.ktor.client.HttpClient], not in secure
 * storage — which is why logging out and logging in as somebody else used to keep sending the
 * first account's `JSESSIONID`. [AcceptAllCookiesStorage] has no `clear`, and the plugin does not
 * expose the storage it was built with, so the storage is ours and the delegate is replaced.
 */
class ClearableCookiesStorage : CookiesStorage {

    private val mutex = Mutex()
    private var delegate = AcceptAllCookiesStorage()

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        current().addCookie(requestUrl, cookie)
    }

    override suspend fun get(requestUrl: Url): List<Cookie> = current().get(requestUrl)

    override fun close() {
        delegate.close()
    }

    /** Forget every cookie. The storage stays usable — the next login fills it again. */
    suspend fun clear() {
        val previous = mutex.withLock {
            delegate.also { delegate = AcceptAllCookiesStorage() }
        }
        previous.close()
    }

    // Under the lock, so a request in flight during clear() reads one delegate or the other and
    // never the half-swapped field.
    private suspend fun current(): AcceptAllCookiesStorage = mutex.withLock { delegate }
}
