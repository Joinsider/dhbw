/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.services.session

import android.content.Context
import io.github.aakira.napier.Napier

private const val TAG = "CachedFileCleaner"

/**
 * Deletes the document copies `openFile` writes into the cache directory.
 *
 * Opening a document from Dualis means writing it to `cacheDir` and handing a `content://` URI to
 * whichever app can display it — so a transcript of records stays readable on the device long
 * after the account it belongs to has been logged out of.
 *
 * Only the files directly in `cacheDir`, which are the ones this app puts there. The
 * subdirectories belong to libraries with their own bookkeeping, and deleting under them is their
 * problem to be surprised by, not ours to cause.
 */
class AndroidCachedFileCleaner(private val context: Context) : CachedFileCleaner {

    override fun deleteAll() {
        val files = context.cacheDir?.listFiles()?.filter { it.isFile }.orEmpty()
        val deleted = files.count { it.delete() }
        Napier.d("Deleted $deleted of ${files.size} cached file(s)", tag = TAG)
    }
}
