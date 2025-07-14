/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

object WidgetUpdateManager {

    fun updateAllWidgets(context: Context) {
        Log.d("WidgetUpdateManager", "Updating all widgets")

        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Update main widgets
            val mainWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TimetableWidgetProvider::class.java)
            )
            Log.d("WidgetUpdateManager", "Found ${mainWidgetIds.size} main widgets to update")

            for (widgetId in mainWidgetIds) {
                TimetableWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
            }

            // Update small widgets
            val smallWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TimetableWidgetSmallProvider::class.java)
            )
            Log.d("WidgetUpdateManager", "Found ${smallWidgetIds.size} small widgets to update")

            for (widgetId in smallWidgetIds) {
                TimetableWidgetSmallProvider.updateAppWidget(context, appWidgetManager, widgetId)
            }

            Log.d("WidgetUpdateManager", "All widgets updated successfully")

        } catch (e: Exception) {
            Log.e("WidgetUpdateManager", "Error updating widgets", e)
        }
    }
}
