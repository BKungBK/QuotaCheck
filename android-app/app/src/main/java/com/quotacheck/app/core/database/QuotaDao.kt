package com.quotacheck.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quotacheck.app.core.database.entity.QuotaPoolEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuotaDao {
    @Query("SELECT * FROM quota_pools ORDER BY displayName, poolId")
    fun observeCurrentPools(): Flow<List<QuotaPoolEntity>>

    @Query("DELETE FROM quota_pools")
    suspend fun clearCurrentPools()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrentPools(pools: List<QuotaPoolEntity>)
}
