package com.onehouse.app.data.consumption

import com.onehouse.app.data.local.AcsConsumptionDao
import com.onehouse.app.data.local.AcsConsumptionEntity
import kotlinx.coroutines.flow.Flow

class AcsConsumptionRepository(
    private val dao: AcsConsumptionDao
) {
    fun observeLatest(): Flow<AcsConsumptionEntity?> = dao.observeLatest()

    fun observeLast30Days(now: Long = System.currentTimeMillis()): Flow<List<AcsConsumptionEntity>> {
        val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
        return dao.observeFrom(now - thirtyDaysMillis)
    }

    suspend fun saveKnxReading(accumulatedValue: Double, timestamp: Long = System.currentTimeMillis()) {
        dao.insert(AcsConsumptionEntity(timestamp = timestamp, accumulatedValue = accumulatedValue))
    }

    suspend fun seedDemoDataIfEmpty() {
        if (dao.count() != 0) return
        val now = System.currentTimeMillis()
        val day = 24L * 60L * 60L * 1000L
        val values = listOf(65.2, 68.4, 73.1, 77.2, 80.8, 86.1, 91.4, 98.4)
        values.forEachIndexed { index, value ->
            dao.insert(
                AcsConsumptionEntity(
                    timestamp = now - (values.lastIndex - index) * 4L * day,
                    accumulatedValue = value
                )
            )
        }
    }
}
