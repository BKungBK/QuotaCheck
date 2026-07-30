package com.quotacheck.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = AccentOnColor,
    primaryContainer = AccentPrimaryMuted,
    onPrimaryContainer = AccentPrimary,
    secondary = InkMuted,
    onSecondary = Ink,
    background = BackgroundDark,
    onBackground = Ink,
    surface = SurfaceDark,
    onSurface = Ink,
    surfaceContainerHigh = SurfaceRaisedDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkMuted,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = StatusCritical,
    onError = BackgroundDark,
    errorContainer = SurfaceVariantDark,
    onErrorContainer = StatusCritical,
)

private val LightColors = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = AccentOnColor,
    primaryContainer = AccentPrimaryMuted,
    onPrimaryContainer = AccentPrimary,
    secondary = InkMutedLight,
    onSecondary = InkLight,
    background = BackgroundLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceContainerHigh = SurfaceRaisedLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkMutedLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = StatusCritical,
    onError = BackgroundLight,
    errorContainer = SurfaceVariantLight,
    onErrorContainer = StatusCritical,
)

@Composable
fun QuotaCheckTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = QuotaCheckTypography,
        shapes = QuotaCheckShapes,
        content = content,
    )
}
