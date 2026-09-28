/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.remote.session

import de.fampopprol.dhbwhorb.data.storage.credentials.SecureStorageInterface
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stops automatic logins after Dualis has rejected the stored credentials twice in a row.
 *
 * Every automatic login — the hourly background check, the widget refresh, a screen whose session
 * expired — goes through [ReAuthenticator] with the stored password. Once that password is wrong
 * (changed in Dualis, account locked), each of them used to try it again, around the clock, and
 * every rejected attempt counts towards Dualis locking the account. After [MAX_FAILURES] rejections
 * the guard closes and no automatic login reaches Dualis any more.
 *
 * It stays closed until the user logs in by hand, and only that opens it: the count lives in the
 * secure storage next to the credentials, so neither an app restart nor the iOS widget extension —
 * a second process sharing that storage — starts counting from zero again. Logging out removes it
 * along with the credentials, and so does [de.fampopprol.dhbwhorb.data.storage.credentials.CredentialsInstallGuard],
 * which clears the whole store.
 *
 * Only [de.fampopprol.dhbwhorb.core.error.AppError.InvalidCredentials] counts. Being offline or
 * Dualis answering 503 during maintenance says nothing about the password, and closing the guard
 * for that would send every user back to the login screen after a bad night's connectivity.
 */
class AutoLoginGuard(private val secureStorage: SecureStorageInterface) {

    companion object {
        private const val TAG = "AutoLoginGuard"
        private const val KEY_FAILURES = "dualis_auto_login_failures"

        /** Rejected automatic logins after which no further one is attempted. */
        const val MAX_FAILURES = 2
    }

    /** Rejected automatic logins since the last successful one. */
    val failures: Int
        get() = secureStorage.getString(KEY_FAILURES, "0").toIntOrNull() ?: 0

    /** True once automatic logins have stopped; only a manual login lifts it. */
    val isBlocked: Boolean
        get() = failures >= MAX_FAILURES

    // Lazy: reading secure storage on desktop can open a keyring dialog, which must not happen
    // merely because the Koin graph was built.
    private val _blocked by lazy { MutableStateFlow(isBlocked) }

    /**
     * [isBlocked] as it changes in this process, so a running app can leave for the login screen
     * the moment a background check closes the guard, rather than at the next cold start.
     */
    val blocked: StateFlow<Boolean> get() = _blocked.asStateFlow()

    /** Dualis rejected an automatic login with the stored credentials. */
    fun recordRejection() {
        val count = failures + 1
        secureStorage.setString(KEY_FAILURES, count.toString())
        _blocked.value = count >= MAX_FAILURES
        if (count >= MAX_FAILURES) {
            Napier.w("Stored credentials rejected $count times — automatic login stopped", tag = TAG)
        } else {
            Napier.w("Stored credentials rejected ($count of $MAX_FAILURES)", tag = TAG)
        }
    }

    /**
     * An automatic login succeeded: the rejections before it were not in a row.
     *
     * Does nothing once blocked — a closed guard lets no attempt through that could succeed, and
     * opening it is left to [reset].
     */
    fun recordSuccess() {
        if (isBlocked || failures == 0) return
        secureStorage.remove(KEY_FAILURES)
    }

    /** The user logged in by hand, or logged out: start from a clean slate. */
    fun reset() {
        if (failures == 0) return
        Napier.d("Automatic login re-enabled", tag = TAG)
        secureStorage.remove(KEY_FAILURES)
        _blocked.value = false
    }
}
