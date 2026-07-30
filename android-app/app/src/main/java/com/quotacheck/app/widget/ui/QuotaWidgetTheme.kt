package com.quotacheck.app.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.unit.ColorProvider
import com.quotacheck.app.core.designsystem.AccentPrimary
import com.quotacheck.app.core.designsystem.BackgroundDark
import com.quotacheck.app.core.designsystem.Ink
import com.quotacheck.app.core.designsystem.InkMuted
import com.quotacheck.app.core.designsystem.ProviderClaude
import com.quotacheck.app.core.designsystem.ProviderFallback
import com.quotacheck.app.core.designsystem.ProviderGemini
import com.quotacheck.app.core.designsystem.StatusCritical
import com.quotacheck.app.core.designsystem.StatusHealthy
import com.quotacheck.app.core.designsystem.StatusWarning
import com.quotacheck.app.core.designsystem.SurfaceDark

object WidgetColors {
    val background = ColorProvider(BackgroundDark)
    val surface = ColorProvider(SurfaceDark)
    val textPrimary = ColorProvider(Ink)
    val textMuted = ColorProvider(InkMuted)
    val accent = ColorProvider(AccentPrimary)

    val statusHealthy = ColorProvider(StatusHealthy)
    val statusWarning = ColorProvider(StatusWarning)
    val statusCritical = ColorProvider(StatusCritical)

    fun statusColor(remainingFraction: Double): ColorProvider = when {
        remainingFraction <= 0.10 -> statusCritical
        remainingFraction <= 0.20 -> statusWarning
        else -> statusHealthy
    }

    fun providerColor(name: String): ColorProvider = when {
        name.contains("claude", ignoreCase = true) -> ColorProvider(ProviderClaude)
        name.contains("gemini", ignoreCase = true) -> ColorProvider(ProviderGemini)
        else -> ColorProvider(ProviderFallback)
    }
}

@Composable
fun QuotaWidgetTheme(content: @Composable () -> Unit) {
    GlanceTheme {
        content()
    }
}
