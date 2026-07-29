package com.quotacheck.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sync_runs", indices = [Index(value = ["finishedAt"])])
data class SyncRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long?,
    val trigger: String,
    val result: String?,
    val errorCategory: String?,
    val consecutiveFailureCount: Int,
)
