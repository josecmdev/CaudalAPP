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
    primary = Ink,
    onPrimary = Sheet,
    primaryContainer = Chip,
    onPrimaryContainer = Ink,
    secondary = Money,
    onSecondary = Sheet,
    secondaryContainer = Chip,
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Sheet,
    onSurface = Ink,
    surfaceVariant = Chip,
    onSurfaceVariant = Muted,
    surfaceContainer = Sheet,
    surfaceContainerHigh = Chip,
    surfaceContainerHighest = Chip,
    surfaceContainerLow = Paper,
    outline = RuleStrong,
    outlineVariant = Rule,
    error = Debt,
    tertiary = Pending,
    tertiaryContainer = PendingWash,
    onTertiaryContainer = PendingInk,
)

private val CaudalDarkColorScheme = darkColorScheme(
    primary = NightInk,
    onPrimary = NightPaper,
    primaryContainer = NightChip,
    onPrimaryContainer = NightInk,
    secondary = NightMoney,
    onSecondary = NightPaper,
    secondaryContainer = NightChip,
    onSecondaryContainer = NightInk,
    background = NightPaper,
    onBackground = NightInk,
    surface = NightSheet,
    onSurface = NightInk,
    surfaceVariant = NightChip,
    onSurfaceVariant = NightMuted,
    surfaceContainer = NightSheet,
    surfaceContainerHigh = NightChip,
    surfaceContainerHighest = NightChip,
    surfaceContainerLow = NightPaper,
    outline = NightMuted,
    outlineVariant = NightRule,
    error = NightDebt,
    tertiary = NightPending,
    tertiaryContainer = NightPendingWash,
    onTertiaryContainer = NightPending,
)

// Esquinas firmes: nada de píldoras ni tarjetas muy redondeadas.
private val CaudalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(16.dp),
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
