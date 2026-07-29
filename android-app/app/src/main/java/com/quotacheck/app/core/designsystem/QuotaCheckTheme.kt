package com.quotacheck.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ActiveGray,
    onPrimary = BackgroundDark,
    secondary = InkMuted,
    background = BackgroundDark,
    onBackground = Ink,
    surface = SurfaceDark,
    onSurface = Ink,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkMuted,
    outline = OutlineDark,
    error = ErrorRed,
    onError = BackgroundDark,
    errorContainer = SurfaceVariantDark,
    onErrorContainer = Ink,
)

private val LightColors = lightColorScheme(
    primary = ActiveGray,
    onPrimary = BackgroundLight,
    secondary = InkMutedLight,
    background = BackgroundLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkMutedLight,
    outline = OutlineLight,
    error = ErrorRed,
    onError = BackgroundLight,
    errorContainer = SurfaceVariantLight,
    onErrorContainer = InkLight,
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
