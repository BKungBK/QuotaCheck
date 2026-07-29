package com.quotacheck.app.core.model

import java.math.BigDecimal
import java.time.Instant

/** A provider quota pool as received by the current synchronization. */
data class QuotaPool(
    val poolId: String,
    val displayName: String,
    val windowLabel: String?,
    val unitLabel: String?,
    val totalUnits: BigDecimal?,
    val usedUnits: BigDecimal?,
    val remainingUnits: BigDecimal?,
    val remainingFraction: Double,
    val cycleStartAt: Instant?,
    val cycleEndAt: Instant?,
    val providerUpdatedAt: Instant?,
    val receivedAt: Instant,
    val schemaVersion: String,
)
