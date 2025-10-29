package de.joinside.dhbw

import androidx.compose.ui.window.ComposeUIViewController
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

fun MainViewController() = ComposeUIViewController {
    // Initialize Napier for iOS logging
    Napier.base(DebugAntilog())
    Napier.d("iOS application starting", tag = "MainViewController")

    App()
}
