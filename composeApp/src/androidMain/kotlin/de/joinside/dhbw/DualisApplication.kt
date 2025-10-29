package de.joinside.dhbw

import android.app.Application
import android.content.Context
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class DualisApplication : Application() {
    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Initialize Napier for logging
        Napier.base(DebugAntilog())
        Napier.d("DualisApplication initialized", tag = "DualisApplication")
    }
}
