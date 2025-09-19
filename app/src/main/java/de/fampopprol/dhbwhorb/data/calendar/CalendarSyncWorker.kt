package de.fampopprol.dhbwhorb.data.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log

class CalendarSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val service = CalendarExportService(applicationContext)
            val report = service.syncWithLocalTimetable()
            Log.d("CalendarSyncWorker", "Sync done: inserted=${report.inserted}, updated=${report.updated}, reinserted=${report.reinsertedMissing}, deleted=${report.deletedOrphans}, errors=${report.errors}")
            Result.success()
        } catch (e: Exception) {
            Log.e("CalendarSyncWorker", "Error during calendar sync", e)
            Result.retry()
        }
    }
}

