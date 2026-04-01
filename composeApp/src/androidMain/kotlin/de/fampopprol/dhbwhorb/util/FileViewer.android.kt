// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.util

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import io.github.aakira.napier.Napier
import java.io.File

private const val TAG = "FileViewer"

actual fun openFile(byteArray: ByteArray, fileName: String) {
    val context = NotificationDispatcher.getContext()
        ?: throw IllegalStateException("NotificationDispatcher not initialized with Android context")
    
    try {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)
        file.writeBytes(byteArray)

        val authority = "de.fampopprol.dhbwhorb.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Napier.d("Successfully started activity to open file: $fileName", tag = TAG)
    } catch (e: Exception) {
        Napier.e("Failed to open file: $fileName", e, tag = TAG)
        throw e
    }
}
