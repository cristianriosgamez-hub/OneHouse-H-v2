package com.onehouse.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EnergyReadingDao {
    @Query("SELECT * FROM energy_readings ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<EnergyReadingEntity>>

    @Query("SELECT * FROM energy_readings WHERE meterType = :meterType ORDER BY timestamp ASC")
    fun observeByType(meterType: String): Flow<List<EnergyReadingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: EnergyReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(readings: List<EnergyReadingEntity>)

    @Query("DELETE FROM energy_readings WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Transaction
    suspend fun replaceBySource(source: String, readings: List<EnergyReadingEntity>) {
        deleteBySource(source)
        insertAll(readings)
    }

    @Update
    suspend fun update(reading: EnergyReadingEntity)

    @Delete
    suspend fun delete(reading: EnergyReadingEntity)

    @Query("SELECT * FROM energy_readings ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<EnergyReadingEntity>

    @Query("SELECT COUNT(*) FROM energy_readings")
    suspend fun count(): Int
}
