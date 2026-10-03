package com.arjunren.netsurvey.ui.theme

import android.app.Activity
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
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF0C5866),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2F0FA),
    onPrimaryContainer = Color(0xFF002F38),
    secondary = Color(0xFF537A10),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F6A6),
    tertiary = Color(0xFF7B4F00),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF7FAFA),
    surface = Color(0xFFF7FAFA),
    surfaceVariant = Color(0xFFDDE4E6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DDDEA),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF064E5B),
    secondary = Color(0xFFBBDD78),
    onSecondary = Color(0xFF283500),
    secondaryContainer = Color(0xFF3B4D00),
    tertiary = Color(0xFFF0BD69),
    background = Color(0xFF0E1416),
    surface = Color(0xFF0E1416),
    surfaceVariant = Color(0xFF3F484A),
)

@Composable
fun NetSurveyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    (context as? Activity)?.window?.let { window ->
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark
    }
    MaterialTheme(colorScheme = colors, typography = NetSurveyTypography, content = content)
}
