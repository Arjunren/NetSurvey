package com.arjunren.netsurvey.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF136C80),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2EEF3),
    onPrimaryContainer = Color(0xFF002F38),
    secondary = Color(0xFF4D607A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE7F4),
    onSecondaryContainer = Color(0xFF162D45),
    tertiary = Color(0xFF7B4F00),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF5F7FA),
    surface = Color(0xFFF5F7FA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEEF2F6),
    surfaceContainer = Color(0xFFE8EEF3),
    surfaceContainerHigh = Color(0xFFE2EAF0),
    surfaceContainerHighest = Color(0xFFDCE5EC),
    outlineVariant = Color(0xFFCBD7DF),
    surfaceVariant = Color(0xFFDDE4E6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DDDEA),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF064E5B),
    secondary = Color(0xFFB1C7E0),
    onSecondary = Color(0xFF162D45),
    secondaryContainer = Color(0xFF2B4058),
    tertiary = Color(0xFFF0BD69),
    background = Color(0xFF0D1722),
    surface = Color(0xFF0D1722),
    surfaceContainerLowest = Color(0xFF0A111B),
    surfaceContainerLow = Color(0xFF14212E),
    surfaceContainer = Color(0xFF1A2836),
    surfaceContainerHigh = Color(0xFF21313F),
    surfaceContainerHighest = Color(0xFF293B49),
    outlineVariant = Color(0xFF394B5C),
    surfaceVariant = Color(0xFF3F484A),
)

@Composable
fun NetSurveyTheme(theme: String = "system", content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> systemDark }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = NetSurveyTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}
