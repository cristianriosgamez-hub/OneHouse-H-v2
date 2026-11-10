package com.onehouse.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "energy_readings",
    indices = [Index(value = ["meterType", "timestamp"], unique = true)]
)
data class EnergyReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterType: String,
    val timestamp: Long,
    val meterValue: Double?,
    val consumption: Double?,
    val cost: Double?,
    val unit: String,
    val source: String,
    val note: String? = null
)
