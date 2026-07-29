package com.quotacheck.app.core.model

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

object QuotaFormatter {
    private const val Unavailable = "—"

    fun percent(fraction: Double): String {
        val normalized = if (fraction.isFinite()) fraction.coerceIn(0.0, 1.0) else 0.0
        return "${(normalized * 100).roundToInt()}%"
    }

    fun absolute(value: BigDecimal?, unitLabel: String?): String {
        if (value == null) return Unavailable

        val number = value.stripTrailingZeros().toPlainString()
        return if (unitLabel.isNullOrBlank()) number else "$number $unitLabel"
    }

    fun reset(resetAt: Instant?, now: Instant): String {
        if (resetAt == null) return Unavailable

        val seconds = Duration.between(now, resetAt).seconds
        if (seconds <= 0) return "now"

        val minutes = (seconds + 59) / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return when {
            hours == 0L -> "in ${minutes}m"
            remainingMinutes == 0L -> "in ${hours}h"
            else -> "in ${hours}h ${remainingMinutes}m"
        }
    }

    fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 0 || atIndex == email.lastIndex) return "***"

        return "${email.first()}***${email.substring(atIndex)}"
    }
}
