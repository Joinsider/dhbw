// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.util

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.AppKit.NSWorkspace
import platform.AppKit.NSSavePanel

private const val TAG = "FileViewer"

@OptIn(ExperimentalForeignApi::class)
actual fun openFile(byteArray: ByteArray, fileName: String) {
    try {
        // Create temporary file path
        val tempDir = NSTemporaryDirectory()
        val tempPath = "$tempDir$fileName"
        val fileURL = NSURL.fileURLWithPath(tempPath)
        
        // Create NSData from ByteArray
        val data = byteArray.usePinned {
            NSData.dataWithBytes(it.addressOf(0), it.get().size.toULong())
        }

        // Write data to file
        if (!data.writeToFile(tempPath, true)) {
            throw Exception("Failed to write file to temp directory")
        }

        // Open file with NSWorkspace on macOS
        if (fileURL != null) {
            NSWorkspace.sharedWorkspace.openURL(fileURL)
            Napier.d("Successfully opened file: $fileName", tag = TAG)
        } else {
            Napier.e("Failed to create file URL for: $fileName", tag = TAG)
        }
    } catch (e: Exception) {
        Napier.e("Failed to open file on macOS", e, tag = TAG)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun saveFileWithDialog(byteArray: ByteArray, fileName: String) {
    try {
        val savePanel = NSSavePanel()
        savePanel.nameFieldStringValue = fileName
        
        val response = savePanel.runModal()
        if (response.toInt() == 1) { // NSModalResponseOK
            val selectedURL = savePanel.URL
            if (selectedURL != null && selectedURL.path != null) {
                val data = byteArray.usePinned {
                    NSData.dataWithBytes(it.addressOf(0), it.get().size.toULong())
                }
                
                if (data.writeToFile(selectedURL.path!!, true)) {
                    Napier.d("Successfully presented document picker: $fileName", tag = TAG)
                } else {
                    Napier.e("Failed to write data to selected file", tag = TAG)
                }
            }
        } else {
            Napier.d("File save cancelled by user", tag = TAG)
        }
    } catch (e: Exception) {
        Napier.e("Failed to save file: $fileName", e, tag = TAG)
    }
}
