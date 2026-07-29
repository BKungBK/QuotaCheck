package com.quotacheck.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quotacheck.app.core.database.entity.AlertEventEntity

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: AlertEventEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM alert_events WHERE alertKey = :alertKey)")
    suspend fun exists(alertKey: String): Boolean

    @Query("SELECT COUNT(*) FROM alert_events")
    suspend fun count(): Int
}
