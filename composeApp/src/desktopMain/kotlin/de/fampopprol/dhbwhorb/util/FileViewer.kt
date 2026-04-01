// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.util

import io.github.aakira.napier.Napier
import java.awt.Desktop
import java.io.File

private const val TAG = "FileViewer"

actual fun openFile(byteArray: ByteArray, fileName: String) {
    try {
        // Extract file name and extension
        val nameWithoutExt = fileName.substringBeforeLast(".", fileName)
        val extension = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
        
        // Create temp file
        val tempFile = File.createTempFile(nameWithoutExt, extension)
        tempFile.deleteOnExit()
        tempFile.writeBytes(byteArray)
        
        // Open with system default application
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(tempFile)
            Napier.d("Successfully opened file: $fileName", tag = TAG)
        } else {
            Napier.e("Desktop not supported on this platform", tag = TAG)
            throw UnsupportedOperationException("Desktop operations not supported")
        }
    } catch (e: Exception) {
        Napier.e("Failed to open file: $fileName", e, tag = TAG)
        throw e
    }
}
