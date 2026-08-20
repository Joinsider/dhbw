// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.util

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import de.fampopprol.dhbwhorb.services.notifications.NotificationDispatcher
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream

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

actual fun saveFileWithDialog(byteArray: ByteArray, fileName: String) {
    val context = NotificationDispatcher.getContext()
        ?: throw IllegalStateException("NotificationDispatcher not initialized with Android context")

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(byteArray)
                }
                Napier.d("Successfully saved file to Downloads via MediaStore: $fileName", tag = TAG)
            } else {
                throw Exception("Failed to create MediaStore entry")
            }
        } else {
            // Fallback for older Android versions
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            file.writeBytes(byteArray)
            Napier.d("Successfully saved file to Downloads via File API: $fileName", tag = TAG)
        }
    } catch (e: Exception) {
        Napier.e("Failed to save file: $fileName", e, tag = TAG)
        // If everything fails, try to at least show the create document intent as per plan, 
        // even if we can't write to it easily without a result listener
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Napier.d("Triggered ACTION_CREATE_DOCUMENT as fallback for: $fileName", tag = TAG)
        } catch (e2: Exception) {
            Napier.e("Final fallback failed: ${e2.message}", tag = TAG)
            throw e
        }
    }
}
