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
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

private const val TAG = "FileViewer"

@OptIn(ExperimentalForeignApi::class)
actual fun openFile(byteArray: ByteArray, fileName: String) {
    try {
        val tempDir = NSTemporaryDirectory()
        val tempPath = "$tempDir$fileName"
        val filePath = NSURL.fileURLWithPath(tempPath)

        val data = byteArray.usePinned {
            NSData.dataWithBytes(it.addressOf(0), it.get().size.toULong())
        }

        if (!data.writeToFile(tempPath, true)) {
            throw Exception("Failed to write file to temp directory")
        }

        val controller = UIDocumentInteractionController.interactionControllerWithURL(filePath)
        val delegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
            override fun documentInteractionControllerViewControllerForPreview(controller: UIDocumentInteractionController): UIViewController {
                return UIApplication.sharedApplication.keyWindow!!.rootViewController!!
            }
        }
        controller.delegate = delegate

        if (!controller.presentPreviewAnimated(true)) {
            Napier.e("Could not find an app to open file: $fileName", tag = TAG)
        } else {
            Napier.d("Successfully presented file preview for $fileName", tag = TAG)
        }

    } catch (e: Exception) {
        Napier.e("Failed to open file on iOS", e, tag = TAG)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun saveFileWithDialog(byteArray: ByteArray, fileName: String) {
    try {
        val tempDir = NSTemporaryDirectory()
        val tempPath = "$tempDir$fileName"
        val filePath = NSURL.fileURLWithPath(tempPath)

        val data = byteArray.usePinned {
            NSData.dataWithBytes(it.addressOf(0), it.get().size.toULong())
        }

        if (!data.writeToFile(tempPath, true)) {
            throw Exception("Failed to write file to temp directory")
        }

        val activityViewController = UIActivityViewController(
            activityItems = listOf(filePath),
            applicationActivities = null
        )

        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (rootViewController != null) {
            rootViewController.presentViewController(activityViewController, animated = true, completion = null)
            Napier.d("Successfully presented activity controller for: $fileName", tag = TAG)
        } else {
            Napier.e("Failed to present activity controller: no root view controller available", tag = TAG)
        }
    } catch (e: Exception) {
        Napier.e("Failed to save file: $fileName", e, tag = TAG)
    }
}
