package com.quotacheck.app.feature.history

import com.quotacheck.app.core.database.DailyUsageAggregate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryViewModelTest {
    @Test fun dayUsesDeviceZoneMidnightBoundary() {
        val zone = ZoneId.of("Asia/Bangkok")
        val clock = Clock.fixed(Instant.parse("2030-01-02T01:00:00Z"), zone)
        val bounds = HistoryPeriod.Day.bounds(clock)
        assertEquals(Instant.parse("2030-01-01T17:00:00Z"), bounds.from)
        assertEquals(Instant.parse("2030-01-02T01:00:00Z"), bounds.until)
    }

    @Test fun weekStartsMondayInDeviceZone() {
        val zone = ZoneId.of("Asia/Bangkok")
        val clock = Clock.fixed(Instant.parse("2030-01-06T06:00:00Z"), zone)
        assertEquals(Instant.parse("2029-12-30T17:00:00Z"), HistoryPeriod.Week.bounds(clock, locale = Locale.UK).from)
    }

    @Test fun sundayFirstLocaleUsesSundayAtNewYearBoundary() {
        val zone = ZoneId.of("UTC")
        val clock = Clock.fixed(Instant.parse("2021-01-01T12:00:00Z"), zone)
        assertEquals(Instant.parse("2020-12-27T00:00:00Z"), HistoryPeriod.Week.bounds(clock, zone, Locale.US).from)
    }

    @Test fun isoWeekGroupsNewYearDaysTogether() {
        val rows = listOf(
            DailyUsageAggregate(Instant.parse("2020-12-31T12:00:00Z").toEpochMilli(), 1, .5, null),
            DailyUsageAggregate(Instant.parse("2021-01-01T12:00:00Z").toEpochMilli(), 1, .4, null),
        )
        assertEquals(1, HistoryUiState.bars(HistoryPeriod.Week, rows, ZoneId.of("UTC"), Locale.UK).size)
    }

    @Test fun monthStartsAtDeviceZoneMonthBoundary() {
        val zone = ZoneId.of("Asia/Bangkok")
        val clock = Clock.fixed(Instant.parse("2030-01-02T01:00:00Z"), zone)
        assertEquals(Instant.parse("2029-12-31T17:00:00Z"), HistoryPeriod.Month.bounds(clock).from)
    }

    @Test fun nullableUnitsFallBackToPercentageConsumed() {
        assertEquals(32.0, HistoryUiState.consumed(null, .68), 0.0)
        assertEquals(48.5, HistoryUiState.consumed(48.5, .68), 0.0)
    }

    @Test fun weeklyAggregationWeightsDailyAveragesBySampleCount() {
        val rows = listOf(
            DailyUsageAggregate(0, 1, .99, null),
            DailyUsageAggregate(86_400_000, 100, .01, null),
        )
        val bar = HistoryUiState.bars(HistoryPeriod.Week, rows, ZoneId.of("UTC"), Locale.UK).single()
        assertEquals(98.0, bar.consumed, 0.01)
    }

    @Test fun partialNullUnitDaysWeightOnlySamplesWithUnits() {
        val rows = listOf(
            DailyUsageAggregate(0, 100, .1, 20.0, usedUnitSampleCount = 1),
            DailyUsageAggregate(86_400_000, 100, .1, null, usedUnitSampleCount = 0),
        )
        val bar = HistoryUiState.bars(HistoryPeriod.Week, rows, ZoneId.of("UTC"), Locale.UK).single()
        assertEquals(20.0, bar.consumed, 0.0)
        assertTrue(!bar.isPercentage)
    }

    @Test fun aggregatesBecomeTimelineBarsAndEmptyHistoryStaysEmpty() {
        val bars = HistoryUiState.bars(HistoryPeriod.Day, daily = listOf(DailyUsageAggregate(0, 2, .4, null)))
        assertEquals(60.0, bars.single().consumed, 0.0)
        assertTrue(bars.single().isPercentage)
        assertTrue(HistoryUiState().isEmpty)
    }
}
