package de.joinside.dhbw

import android.app.Application
import de.joinside.dhbw.data.credentials.SecureStorage

class DualisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SecureStorage.initialize(this)
    }
}

