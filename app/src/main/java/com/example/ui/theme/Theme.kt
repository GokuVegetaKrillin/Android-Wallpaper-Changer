package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val WallshowDarkColorScheme = darkColorScheme(
    primary = WallshowPrimary,
    onPrimary = WallshowOnPrimary,
    primaryContainer = WallshowPrimaryContainer,
    onPrimaryContainer = Color.White,
    secondary = WallshowStartGreen,
    onSecondary = Color.Black,
    error = WallshowStopRed,
    onError = Color.White,
    background = WallshowDarkBackground,
    onBackground = WallshowTextPrimary,
    surface = WallshowDarkSurface,
    onSurface = WallshowTextPrimary,
    surfaceVariant = WallshowDarkSurfaceVariant,
    onSurfaceVariant = WallshowTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // default to sleek dark theme matching Wallshow screenshot
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WallshowDarkColorScheme,
        typography = Typography,
        content = content
    )
}
