package com.onehouse.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "acs_consumption")
data class AcsConsumptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val accumulatedValue: Double
)
