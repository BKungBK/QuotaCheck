package com.quotacheck.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quotacheck.app.core.database.entity.SyncRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert
    suspend fun insert(run: SyncRunEntity): Long

    @Query("SELECT * FROM sync_runs ORDER BY COALESCE(finishedAt, startedAt) DESC LIMIT 1")
    fun observeLatestSync(): Flow<SyncRunEntity?>

    @Query("SELECT * FROM sync_runs ORDER BY COALESCE(finishedAt, startedAt) DESC LIMIT 1")
    suspend fun latestSync(): SyncRunEntity?
}
