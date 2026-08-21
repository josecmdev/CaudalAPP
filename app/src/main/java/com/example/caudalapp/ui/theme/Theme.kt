package com.example.caudalapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.caudalapp.AppTheme

private val CaudalColorScheme = lightColorScheme(
    primary = CaudalBlue,
    onPrimary = CaudalSurface,
    primaryContainer = CaudalLightBlue,
    onPrimaryContainer = CaudalDarkBlue,
    background = CaudalBackground,
    onBackground = CaudalInk,
    surface = CaudalSurface,
    onSurface = CaudalInk,
    onSurfaceVariant = CaudalMuted,
    error = CaudalError,
)

private val CaudalDarkColorScheme = darkColorScheme(
    primary = CaudalLightBlue,
    onPrimary = CaudalDarkBlue,
    primaryContainer = CaudalDarkBlue,
    background = CaudalInk,
    surface = androidx.compose.ui.graphics.Color(0xFF202A3C),
    onSurface = CaudalSurface,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC0C8D8),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
)

@Composable
fun CaudalAPPTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) CaudalDarkColorScheme else CaudalColorScheme,
        typography = Typography,
        content = content,
    )
}
