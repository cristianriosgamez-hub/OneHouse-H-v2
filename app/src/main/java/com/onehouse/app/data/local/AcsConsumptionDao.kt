package com.onehouse.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AcsConsumptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: AcsConsumptionEntity)

    @Query("SELECT * FROM acs_consumption ORDER BY timestamp DESC LIMIT 1")
    fun observeLatest(): Flow<AcsConsumptionEntity?>

    @Query("SELECT * FROM acs_consumption WHERE timestamp >= :fromTimestamp ORDER BY timestamp ASC")
    fun observeFrom(fromTimestamp: Long): Flow<List<AcsConsumptionEntity>>

    @Query("SELECT COUNT(*) FROM acs_consumption")
    suspend fun count(): Int
}
