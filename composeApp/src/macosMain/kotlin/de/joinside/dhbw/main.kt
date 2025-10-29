package de.joinside.dhbw

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Initialize the macOS native framework.
 *
 * Note: For Mac App Store distribution, use the Desktop (JVM) target instead.
 * The native macOS target is experimental and primarily for framework embedding.
 *
 * To build for Mac App Store:
 * ./gradlew :composeApp:packageDmg
 * or
 * ./gradlew :composeApp:packagePkg
 *
 * Then follow Apple's notarization and signing process.
 */
fun initializeApp() {
    // Initialize Napier for macOS logging
    Napier.base(DebugAntilog())
    Napier.d("macOS native framework initialized", tag = "Main")
}

