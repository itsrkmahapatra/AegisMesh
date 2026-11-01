package com.itsrkmahapatra.aegismesh.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = RadarCyan,
    onPrimary = AegisDarkBg,
    primaryContainer = AegisSurfaceVariant,
    onPrimaryContainer = RadarCyan,
    secondary = MeshGreen,
    onSecondary = AegisDarkBg,
    background = AegisDarkBg,
    onBackground = TextPrimary,
    surface = AegisSurface,
    onSurface = TextPrimary,
    surfaceVariant = AegisSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = AlertExtremeRed,
    onError = TextPrimary
)

@Composable
fun AegisMeshTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AegisDarkBg.toArgb()
            window.navigationBarColor = AegisDarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
