// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.util

import io.github.aakira.napier.Napier
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController

private const val TAG = "FileViewer"

actual fun openFile(byteArray: ByteArray, fileName: String) {
    try {
        // Convert ByteArray to NSData
        val nsData = byteArray.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = byteArray.size.toULong())
        }
        
        // Create temporary file path
        val tempDir = NSTemporaryDirectory()
        val tempPath = "$tempDir$fileName"
        val fileURL = NSURL.fileURLWithPath(tempPath)
        
        // Write data to file
        nsData?.writeToURL(fileURL, atomically = true)
        
        // Open file with UIDocumentInteractionController
        val documentController = UIDocumentInteractionController.interactionControllerWithURL(fileURL)
        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        
        if (rootViewController != null) {
            documentController.presentPreviewAnimated(true)
            Napier.d("Successfully opened file: $fileName", tag = TAG)
        } else {
            Napier.e("Failed to open file: no root view controller available", tag = TAG)
        }
    } catch (e: Exception) {
        Napier.e("Failed to open file: $fileName", e, tag = TAG)
        throw e
    }
}
