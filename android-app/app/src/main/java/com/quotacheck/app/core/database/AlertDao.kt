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

    @Query("SELECT deliveredAt FROM alert_events WHERE alertKey = :alertKey")
    suspend fun deliveredAt(alertKey: String): Long?

    @Query("SELECT * FROM alert_events WHERE deliveredAt IS NULL OR deliveredAt < 0")
    suspend fun pendingDeliveries(): List<AlertEventEntity>

    @Query(
        """
        UPDATE alert_events
        SET deliveredAt = :claimToken
        WHERE alertKey = :alertKey
          AND (deliveredAt IS NULL OR (deliveredAt < 0 AND -deliveredAt <= :staleBefore))
        """,
    )
    suspend fun claimDelivery(alertKey: String, claimToken: Long, staleBefore: Long): Int

    @Query(
        "UPDATE alert_events SET deliveredAt = :deliveredAt " +
            "WHERE alertKey = :alertKey AND deliveredAt = :claimToken",
    )
    suspend fun markDelivered(alertKey: String, claimToken: Long, deliveredAt: Long): Int

    @Query(
        "UPDATE alert_events SET deliveredAt = NULL " +
            "WHERE alertKey = :alertKey AND deliveredAt = :claimToken",
    )
    suspend fun releaseDeliveryClaim(alertKey: String, claimToken: Long): Int

    @Query("SELECT COUNT(*) FROM alert_events")
    suspend fun count(): Int
}
