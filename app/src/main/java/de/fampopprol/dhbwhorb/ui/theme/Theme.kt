package de.fampopprol.dhbwhorb.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Enhanced Dark Color Scheme with better Material You compatibility
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Neutral10,
    primaryContainer = Purple40,
    onPrimaryContainer = Purple80,
    secondary = PurpleGrey80,
    onSecondary = Neutral10,
    secondaryContainer = PurpleGrey40,
    onSecondaryContainer = PurpleGrey80,
    tertiary = Pink80,
    onTertiary = Neutral10,
    tertiaryContainer = Pink40,
    onTertiaryContainer = Pink80,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant60,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant30,
    surfaceContainer = Neutral20,
    surfaceContainerHigh = Neutral20,
    surfaceContainerHighest = Neutral20
)

// Enhanced Light Color Scheme with better Material You compatibility
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Neutral99,
    primaryContainer = Purple80,
    onPrimaryContainer = Purple40,
    secondary = PurpleGrey40,
    onSecondary = Neutral99,
    secondaryContainer = PurpleGrey80,
    onSecondaryContainer = PurpleGrey40,
    tertiary = Pink40,
    onTertiary = Neutral99,
    tertiaryContainer = Pink80,
    onTertiaryContainer = Pink40,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant90,
    surfaceContainer = Neutral95,
    surfaceContainerHigh = Neutral95,
    surfaceContainerHighest = Neutral90
)

@Composable
fun DHBWHorbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ (API 31+)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use the newer WindowInsetsController API instead of deprecated statusBarColor
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                // Set status bar color through window flags instead of direct assignment
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                } else {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = colorScheme.primary.toArgb()
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}