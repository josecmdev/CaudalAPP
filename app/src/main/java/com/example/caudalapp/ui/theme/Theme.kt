package com.example.caudalapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.caudalapp.AppTheme

private val CaudalColorScheme = lightColorScheme(
    primary = CaudalBlue,
    onPrimary = CaudalSurface,
    primaryContainer = CaudalLightBlue,
    onPrimaryContainer = CaudalDarkBlue,
    secondary = CaudalMoney,
    onSecondary = CaudalSurface,
    background = CaudalBackground,
    onBackground = CaudalInk,
    surface = CaudalSurface,
    onSurface = CaudalInk,
    surfaceVariant = CaudalSurfaceHigh,
    onSurfaceVariant = CaudalMuted,
    surfaceContainer = CaudalSurface,
    surfaceContainerHigh = CaudalSurfaceHigh,
    surfaceContainerHighest = CaudalSurfaceHigh,
    surfaceContainerLow = CaudalBackground,
    outline = CaudalOutline,
    outlineVariant = CaudalSurfaceHigh,
    error = CaudalError,
    tertiary = CaudalWarning,
    tertiaryContainer = CaudalWarningContainer,
    onTertiaryContainer = NightWarningContainer,
)

private val CaudalDarkColorScheme = darkColorScheme(
    primary = NightBlue,
    onPrimary = NightBackground,
    primaryContainer = NightBlueContainer,
    onPrimaryContainer = NightInk,
    secondary = NightMoney,
    onSecondary = NightBackground,
    background = NightBackground,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = NightMuted,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = NightSurfaceHigh,
    surfaceContainerLow = NightBackground,
    outline = NightOutline,
    outlineVariant = NightSurfaceHigh,
    error = NightError,
    tertiary = NightWarning,
    tertiaryContainer = NightWarningContainer,
    onTertiaryContainer = NightWarning,
)

// Esquinas moderadas: tarjetas hasta 16 dp; los botones siguen en píldora.
private val CaudalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
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
        shapes = CaudalShapes,
        content = content,
    )
}
