package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

private val NasjeDarkColorScheme = darkColorScheme(
    primary = NasjeBlue,
    onPrimary = Color.White,
    primaryContainer = NasjeSurfaceElevated,
    onPrimaryContainer = NasjeCyan,
    secondary = NasjeViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1F1638),
    onSecondaryContainer = Color(0xFFD8B4FE),
    tertiary = NasjeRunGreen,
    onTertiary = Color.White,
    background = NasjeBgDeep,
    onBackground = NasjeTextPrimary,
    surface = NasjeBgPrimary,
    onSurface = NasjeTextPrimary,
    surfaceVariant = NasjeSurface,
    onSurfaceVariant = NasjeTextSecondary,
    outline = NasjeBorderSubtle,
    outlineVariant = Color(0xFF28354F),
    error = NasjeErrorRed,
    onError = Color.White
)

private val NasjeLightColorScheme = lightColorScheme(
    primary = NasjeBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = NasjeViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = NasjeRunGreenDark,
    onTertiary = Color.White,
    background = NasjeLightBg,
    onBackground = NasjeLightTextPrimary,
    surface = NasjeLightSurface,
    onSurface = NasjeLightTextPrimary,
    surfaceVariant = NasjeLightSurfaceElevated,
    onSurfaceVariant = NasjeLightTextSecondary,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = NasjeErrorRed,
    onError = Color.White
)

val NasjeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun NasjeIdeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NasjeDarkColorScheme else NasjeLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = NasjeShapes,
        content = content
    )
}
