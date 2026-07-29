package com.quotacheck.app.feature.history

import com.quotacheck.app.core.database.DailyUsageAggregate
import com.quotacheck.app.core.database.PeriodUsageAggregate
import com.quotacheck.app.core.model.QuotaPool
import java.time.Instant
import java.time.ZoneId
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

data class HistoryBar(
    val startAt: Instant,
    val consumed: Double,
    val isPercentage: Boolean,
    val label: String = "",
)

data class HistoryUiState(
    val pools: List<QuotaPool> = emptyList(),
    val selectedPoolId: String? = null,
    val period: HistoryPeriod = HistoryPeriod.Day,
    val bars: List<HistoryBar> = emptyList(),
) {
    val selectedPool get() = pools.firstOrNull { it.poolId == selectedPoolId }
    val isEmpty get() = selectedPoolId == null || bars.isEmpty()

    companion object {
        fun bars(
            period: HistoryPeriod,
            daily: List<DailyUsageAggregate> = emptyList(),
            zoneId: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault(),
        ): List<HistoryBar> = when (period) {
            HistoryPeriod.Day -> daily.map(::bar)
            HistoryPeriod.Week -> daily.groupBy { weekKey(it.dayStartAt, zoneId, locale) }.values.map { rows -> bar(rows) }
            HistoryPeriod.Month -> daily.groupBy { YearMonth.from(Instant.ofEpochMilli(it.dayStartAt).atZone(zoneId)) }.values.map { rows -> bar(rows) }
        }

        private fun weekKey(at: Long, zoneId: ZoneId, locale: Locale): Pair<Int, Int> {
            val date = Instant.ofEpochMilli(at).atZone(zoneId).toLocalDate()
            val fields = WeekFields.of(locale)
            return date.get(fields.weekBasedYear()) to date.get(fields.weekOfWeekBasedYear())
        }

        private fun bar(row: DailyUsageAggregate) = HistoryBar(
            Instant.ofEpochMilli(row.dayStartAt), consumed(row.averageUsedUnits, row.averageRemainingFraction), row.averageUsedUnits == null,
        )

        private fun bar(rows: List<DailyUsageAggregate>): HistoryBar {
            val sampleCount = rows.sumOf(DailyUsageAggregate::sampleCount)
            val remaining = rows.sumOf { it.averageRemainingFraction * it.sampleCount } / sampleCount
            val usedCount = rows.sumOf(DailyUsageAggregate::usedUnitSampleCount)
            val used = if (usedCount == 0) null else rows.sumOf { (it.averageUsedUnits ?: 0.0) * it.usedUnitSampleCount } / usedCount
            return HistoryBar(Instant.ofEpochMilli(rows.minOf(DailyUsageAggregate::dayStartAt)), consumed(used, remaining), used == null)
        }

        /** Absolute units win; percentage consumption is the portable fallback. */
        fun consumed(usedUnits: Double?, remainingFraction: Double): Double =
            usedUnits ?: ((1 - remainingFraction.coerceIn(0.0, 1.0)) * 100).roundToInt().toDouble()
    }
}
