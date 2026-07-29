package com.quotacheck.app.core.model

import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaFormatterTest {
    @Test
    fun percentIsClampedAndRounded() {
        assertEquals("68%", QuotaFormatter.percent(0.684))
        assertEquals("0%", QuotaFormatter.percent(-1.0))
        assertEquals("100%", QuotaFormatter.percent(2.0))
    }

    @Test
    fun absoluteValueUsesPlaceholderWhenUnavailable() {
        assertEquals("—", QuotaFormatter.absolute(null, "requests"))
        assertEquals("12.5 requests", QuotaFormatter.absolute(BigDecimal("12.5"), "requests"))
    }

    @Test
    fun resetUsesRelativeTimeAndHandlesMissingValue() {
        val now = Instant.parse("2030-01-01T00:00:00Z")

        assertEquals("in 2h 30m", QuotaFormatter.reset(Instant.parse("2030-01-01T02:30:00Z"), now))
        assertEquals("now", QuotaFormatter.reset(now, now))
        assertEquals("—", QuotaFormatter.reset(null, now))
    }

    @Test
    fun emailIsMaskedOutsideAccountDetail() {
        assertEquals("a***@example.com", QuotaFormatter.maskEmail("account@example.com"))
    }
}
