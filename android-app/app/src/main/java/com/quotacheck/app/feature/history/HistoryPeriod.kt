package com.quotacheck.app.feature.history

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale

enum class HistoryPeriod(val label: String) {
    Day("Day"), Week("Week"), Month("Month");

    fun bounds(
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = clock.zone,
        locale: Locale = Locale.getDefault(),
    ): HistoryBounds {
        val now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId)
        val start = when (this) {
            Day -> now.toLocalDate().atStartOfDay(zoneId)
            Week -> now.toLocalDate().minusDays(
                ((now.dayOfWeek.value - WeekFields.of(locale).firstDayOfWeek.value + 7) % 7).toLong(),
            ).atStartOfDay(zoneId)
            Month -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(zoneId)
        }
        return HistoryBounds(start.toInstant(), now.toInstant())
    }
}

data class HistoryBounds(val from: Instant, val until: Instant)
