package com.quotacheck.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "alert_events",
    primaryKeys = ["alertKey"],
    indices = [Index(value = ["poolId"]), Index(value = ["alertKey"], unique = true)],
)
data class AlertEventEntity(
    val alertKey: String,
    val poolId: String?,
    val cycleId: String?,
    val alertType: String,
    val thresholdPercent: Int?,
    val deliveredAt: Long,
)
