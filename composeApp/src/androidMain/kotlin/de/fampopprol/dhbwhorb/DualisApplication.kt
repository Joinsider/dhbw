package de.fampopprol.dhbwhorb

import android.app.Application
import android.content.Context
import de.fampopprol.dhbwhorb.data.storage.database.createRoomDatabase
import de.fampopprol.dhbwhorb.data.storage.database.getDatabaseBuilder
import de.fampopprol.dhbwhorb.services.widget.DatabaseWidgetRepository
import de.fampopprol.dhbwhorb.services.widget.WidgetServiceLocator
import de.fampopprol.dhbwhorb.services.widget.WidgetTimetableUseCase
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

        // Initialize widget service locator with a DB-only (no network) use case.
        // This ensures background WorkManager jobs can update the widget even without
        // an active Activity (and without re-creating the full Ktor/auth stack).
        initializeWidgetServiceLocator()
    }

    private fun initializeWidgetServiceLocator() {
        try {
            val db = createRoomDatabase(getDatabaseBuilder(applicationContext))
            val repository = DatabaseWidgetRepository(db.lectureDao())
            val useCase = WidgetTimetableUseCase(repository = repository)
            WidgetServiceLocator.initialize(useCase)
            Napier.d("WidgetServiceLocator initialized in DualisApplication", tag = "DualisApplication")
        } catch (e: Exception) {
            Napier.e("Failed to initialize WidgetServiceLocator: ${e.message}", e, tag = "DualisApplication")
        }
    }
}
