package com.quotacheck.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Current provider state, stored separately from immutable history samples. */
@Entity(
    tableName = "quota_pools",
    indices = [Index(value = ["receivedAt"]), Index(value = ["cycleEndAt"])],
)
data class QuotaPoolEntity(
    @PrimaryKey val poolId: String,
    val displayName: String,
    val windowLabel: String?,
    val unitLabel: String?,
    val totalUnits: Double?,
    val usedUnits: Double?,
    val remainingUnits: Double?,
    val remainingFraction: Double,
    val cycleStartAt: Long?,
    val cycleEndAt: Long?,
    val providerUpdatedAt: Long?,
    val receivedAt: Long,
    val schemaVersion: String,
)
