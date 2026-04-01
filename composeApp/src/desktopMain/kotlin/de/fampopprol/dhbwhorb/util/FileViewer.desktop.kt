package de.fampopprol.dhbwhorb.util

import io.github.aakira.napier.Napier
import java.awt.Desktop
import java.io.File
import kotlin.io.path.createTempFile

actual fun openFile(byteArray: ByteArray, fileName: String) {
    try {
        val (prefix, suffix) = fileName.split('.').let { it.first() to ".${it.last()}" }
        val tempFile = createTempFile(prefix, suffix).toFile()
        tempFile.writeBytes(byteArray)
        tempFile.deleteOnExit()


        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(tempFile)
            Napier.d("Successfully opened file: $fileName")
        } else {
            Napier.w("Desktop is not supported, cannot open file.")
        }
    } catch (e: Exception) {
        Napier.e("Failed to open file on desktop", e)
    }
}
