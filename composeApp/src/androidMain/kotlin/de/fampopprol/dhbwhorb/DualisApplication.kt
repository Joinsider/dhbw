package de.fampopprol.dhbwhorb

import android.app.Application
import android.content.Context
import de.fampopprol.dhbwhorb.util.AndroidAppContext
import de.fampopprol.dhbwhorb.data.storage.database.createRoomDatabase
import de.fampopprol.dhbwhorb.data.storage.database.getDatabaseBuilder
import de.fampopprol.dhbwhorb.services.widget.DatabaseWidgetRepository
import de.fampopprol.dhbwhorb.services.widget.WidgetRefreshTrigger
import de.fampopprol.dhbwhorb.services.widget.WidgetServiceLocator
import de.fampopprol.dhbwhorb.widget.sync.WidgetSyncWorker
import de.fampopprol.dhbwhorb.services.widget.WidgetTimetableUseCase
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class DualisApplication : Application() {
    companion object {
        /** Kept for source compatibility; the Context itself lives in [AndroidAppContext]. */
        val appContext: Context get() = AndroidAppContext.requireContext()
    }

    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.initialize(applicationContext)

        // Initialize Napier for logging
        Napier.base(DebugAntilog())
        Napier.d("DualisApplication initialized", tag = "DualisApplication")

        // The scheduler in :services asks for widget refreshes through this hook, because the
        // Glance implementation lives here.
        WidgetRefreshTrigger.register { context -> WidgetSyncWorker.enqueueImmediate(context) }

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
