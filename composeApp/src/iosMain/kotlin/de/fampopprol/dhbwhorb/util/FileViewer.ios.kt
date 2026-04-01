package de.fampopprol.dhbwhorb.util

import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual fun openFile(byteArray: ByteArray, fileName: String) {
    try {
        val tempDir = NSFileManager.defaultManager.temporaryDirectory
        val filePath = tempDir.URLByAppendingPathComponent(fileName)
            ?: throw Exception("Could not create file path")

        val data = byteArray.usePinned {
            NSData.dataWithBytes(it.addressOf(0), it.get().size.toULong())
        }

        if (!data.writeToFile(filePath.path!!, true)) {
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
            Napier.e("Could not find an app to open file: $fileName")
        } else {
            Napier.d("Successfully presented file preview for $fileName")
        }

    } catch (e: Exception) {
        Napier.e("Failed to open file on iOS", e)
    }
}
