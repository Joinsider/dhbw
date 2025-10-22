package de.joinside.dhbw

import android.app.Application
import android.content.Context

class DualisApplication : Application() {
    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // SecureStorage no longer requires explicit initialization when using KSafe
    }
}
