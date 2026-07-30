package com.quotacheck.app.core.designsystem

import androidx.compose.ui.graphics.Color

// Dark palette — cool near-black base (not flat gray), gives cards somewhere to sit above.
internal val BackgroundDark = Color(0xFF0F1115)
internal val SurfaceDark = Color(0xFF181B21)
internal val SurfaceRaisedDark = Color(0xFF20242C)
internal val SurfaceVariantDark = Color(0xFF262B34)
internal val OutlineDark = Color(0xFF2E333D)
internal val Ink = Color(0xFFF3F4F6)
internal val InkMuted = Color(0xFF9198A6)

// Light palette
internal val BackgroundLight = Color(0xFFF7F8FA)
internal val SurfaceLight = Color(0xFFFFFFFF)
internal val SurfaceRaisedLight = Color(0xFFFFFFFF)
internal val SurfaceVariantLight = Color(0xFFEDEFF3)
internal val OutlineLight = Color(0xFFDCE0E7)
internal val InkLight = Color(0xFF14161A)
internal val InkMutedLight = Color(0xFF5B6270)

// Brand accent — used for primary actions, selected states, focus. Deliberately a single
// warm-indigo hue so it reads as "this app's color" rather than default Material purple.
internal val AccentPrimary = Color(0xFF6C6BF5)
internal val AccentPrimaryMuted = Color(0xFF2A2A63)
internal val AccentOnColor = Color(0xFFFFFFFF)

// Status — quota remaining. Green/amber/red is the correct semantic here (it mirrors the
// battery/signal mental model people already have), so we lean into it rather than avoid it.
internal val StatusHealthy = Color(0xFF4ADE80)
internal val StatusWarning = Color(0xFFFBBF24)
internal val StatusCritical = Color(0xFFF87171)

// Per-provider identity colors, used as a small dot/accent so pools are scannable at a glance
// without reading the label. Distinct hues, same saturation/lightness family as the accent.
internal val ProviderClaude = Color(0xFFE0834C)
internal val ProviderGemini = Color(0xFF4C9CE0)
internal val ProviderFallback = Color(0xFF8A93A6)

internal fun providerColor(name: String): Color = when {
    name.contains("claude", ignoreCase = true) -> ProviderClaude
    name.contains("gemini", ignoreCase = true) -> ProviderGemini
    else -> ProviderFallback
}

internal fun statusColor(remainingFraction: Double): Color = when {
    remainingFraction <= 0.10 -> StatusCritical
    remainingFraction <= 0.20 -> StatusWarning
    else -> StatusHealthy
}
