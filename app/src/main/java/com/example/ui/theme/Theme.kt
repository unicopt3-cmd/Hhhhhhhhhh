package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AutoPilotDarkColorScheme =
  darkColorScheme(
    primary = CyanElectric,
    onPrimary = ObsidianBg,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanElectric,
    secondary = EmeraldSafe,
    onSecondary = ObsidianBg,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldSafe,
    tertiary = NeonPurple,
    onTertiary = TextPrimary,
    tertiaryContainer = NeonPurpleContainer,
    onTertiaryContainer = NeonPurple,
    error = CoralAlert,
    onError = ObsidianBg,
    errorContainer = CoralContainer,
    onErrorContainer = CoralAlert,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    outlineVariant = SlateBorder,
  )

@Composable
fun PTAutoPilotTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AutoPilotDarkColorScheme,
    typography = AppTypography,
    content = content,
  )
}
