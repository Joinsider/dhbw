// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

package de.fampopprol.dhbwhorb.widget.theme

import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders

/** Seed colour that matches the app's default Material You seed. */
private val WidgetSeedLight = lightColorScheme()
private val WidgetSeedDark  = darkColorScheme()

/**
 * Wraps [GlanceTheme] using Material 3 dynamic colours on Android 12+ (API 31+),
 * falling back to the static baseline scheme on older versions.
 */
@Composable
fun TimetableWidgetTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ColorProviders(
            light = dynamicLightColorScheme(context),
            dark  = dynamicDarkColorScheme(context),
        )
    } else {
        ColorProviders(
            light = WidgetSeedLight,
            dark  = WidgetSeedDark,
        )
    }
    GlanceTheme(colors = colors, content = content)
}
