package com.quotacheck.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Immutable quota observation. All timestamps are UTC epoch milliseconds. */
@Entity(
    tableName = "usage_samples",
    indices = [Index(value = ["poolId"]), Index(value = ["receivedAt"]), Index(value = ["cycleEndAt"])],
)
data class UsageSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poolId: String,
    val remainingFraction: Double,
    val totalUnits: Double?,
    val usedUnits: Double?,
    val remainingUnits: Double?,
    val cycleEndAt: Long?,
    val receivedAt: Long,
)
