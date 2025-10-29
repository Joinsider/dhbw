package de.joinside.dhbw

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

fun main() {
    // Initialize Napier for JVM logging
    Napier.base(DebugAntilog())
    Napier.d("JVM Desktop application starting", tag = "Main")

    application {
        Napier.d("Creating main window", tag = "Main")
        Window(
            onCloseRequest = {
                Napier.d("Application closing", tag = "Main")
                exitApplication()
            },
            title = "dhbw",
        ) {
            App()
        }
    }
}