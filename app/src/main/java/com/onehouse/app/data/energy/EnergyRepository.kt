package com.onehouse.app.data.energy

import com.onehouse.app.data.local.EnergyReadingDao
import com.onehouse.app.data.local.EnergyReadingEntity
import java.util.Calendar
import kotlinx.coroutines.flow.Flow

class EnergyRepository(private val dao: EnergyReadingDao) {

    fun observeAll(): Flow<List<EnergyReadingEntity>> = dao.observeAll()

    fun observe(type: MeterType): Flow<List<EnergyReadingEntity>> =
        dao.observeByType(type.storageValue)

    suspend fun save(reading: EnergyReadingEntity) {
        if (reading.id == 0L) dao.insert(reading) else dao.update(reading)
    }

    suspend fun delete(reading: EnergyReadingEntity) = dao.delete(reading)

    suspend fun count(): Int = dao.count()

    suspend fun replaceImported(readings: List<EnergyReadingEntity>) =
        dao.replaceBySource(ReadingSource.EXCEL.storageValue, readings)

    fun buildSummaries(
        readings: List<EnergyReadingEntity>,
        now: Long = System.currentTimeMillis()
    ): List<MeterSummary> {
        val currentYear = calendarField(now, Calendar.YEAR)
        val currentMonth = calendarField(now, Calendar.MONTH)

        return MeterType.entries.map { type ->
            val meterReadings = readings
                .filter { it.meterType == type.storageValue }
                .sortedBy { it.timestamp }

            val monthConsumption = meterReadings
                .filter {
                    calendarField(it.timestamp, Calendar.YEAR) == currentYear &&
                        calendarField(it.timestamp, Calendar.MONTH) == currentMonth
                }
                .sumOf { it.consumption ?: 0.0 }

            val currentYearReadings = meterReadings.filter {
                calendarField(it.timestamp, Calendar.YEAR) == currentYear
            }
            val previousYearReadings = meterReadings.filter {
                calendarField(it.timestamp, Calendar.YEAR) == currentYear - 1
            }

            val currentTotal = currentYearReadings.sumOf { it.consumption ?: 0.0 }
            val previousTotal = previousYearReadings.sumOf { it.consumption ?: 0.0 }

            MeterSummary(
                type = type,
                latestReading = meterReadings.lastOrNull(),
                monthConsumption = monthConsumption,
                yearConsumption = currentTotal,
                yearCost = currentYearReadings.sumOf { it.cost ?: 0.0 },
                yearVariationPercent = percentageVariation(currentTotal, previousTotal)
            )
        }
    }


    fun buildOverview(
        readings: List<EnergyReadingEntity>,
        summaries: List<MeterSummary>,
        now: Long = System.currentTimeMillis()
    ): EnergyOverview {
        val currentYear = calendarField(now, Calendar.YEAR)
        val currentYearReadings = readings.filter {
            calendarField(it.timestamp, Calendar.YEAR) == currentYear
        }
        val byType = summaries.associateBy { it.type }
        val electricityKwh =
            (byType[MeterType.ENDESA]?.yearConsumption ?: 0.0) +
                (byType[MeterType.CLIMATIZATION]?.yearConsumption ?: 0.0) * 1_000.0
        val waterM3 =
            (byType[MeterType.ACS]?.yearConsumption ?: 0.0) +
                (byType[MeterType.AGBAR]?.yearConsumption ?: 0.0)

        return EnergyOverview(
            electricityYearKwh = electricityKwh,
            waterYearM3 = waterM3,
            totalYearCost = summaries.sumOf { it.yearCost },
            metersWithData = summaries.count { it.latestReading != null },
            totalReadings = currentYearReadings.size,
            lastUpdatedAt = readings.maxOfOrNull { it.timestamp }
        )
    }

    fun readingsForPeriod(
        readings: List<EnergyReadingEntity>,
        type: MeterType,
        period: EnergyPeriod,
        now: Long = System.currentTimeMillis()
    ): List<EnergyReadingEntity> {
        val currentYear = calendarField(now, Calendar.YEAR)
        val currentMonth = calendarField(now, Calendar.MONTH)

        return readings
            .asSequence()
            .filter { it.meterType == type.storageValue }
            .filter { reading ->
                when (period) {
                    EnergyPeriod.MONTH ->
                        calendarField(reading.timestamp, Calendar.YEAR) == currentYear &&
                            calendarField(reading.timestamp, Calendar.MONTH) == currentMonth

                    EnergyPeriod.YEAR ->
                        calendarField(reading.timestamp, Calendar.YEAR) == currentYear

                    EnergyPeriod.ALL -> true
                }
            }
            .sortedBy { it.timestamp }
            .toList()
    }

    fun buildStatistics(
        allReadings: List<EnergyReadingEntity>,
        type: MeterType,
        periodReadings: List<EnergyReadingEntity>,
        now: Long = System.currentTimeMillis()
    ): EnergyStatistics {
        val consumptions = periodReadings.mapNotNull { it.consumption }
        val currentYear = calendarField(now, Calendar.YEAR)
        val currentYearTotal = allReadings
            .filter {
                it.meterType == type.storageValue &&
                    calendarField(it.timestamp, Calendar.YEAR) == currentYear
            }
            .sumOf { it.consumption ?: 0.0 }
        val previousYearTotal = allReadings
            .filter {
                it.meterType == type.storageValue &&
                    calendarField(it.timestamp, Calendar.YEAR) == currentYear - 1
            }
            .sumOf { it.consumption ?: 0.0 }

        return EnergyStatistics(
            average = consumptions.takeIf { it.isNotEmpty() }?.average(),
            maximum = consumptions.maxOrNull(),
            minimum = consumptions.minOrNull(),
            totalConsumption = consumptions.sum(),
            totalCost = periodReadings.sumOf { it.cost ?: 0.0 },
            variationPercent = percentageVariation(currentYearTotal, previousYearTotal)
        )
    }

    fun buildChartPoints(readings: List<EnergyReadingEntity>): List<EnergyChartPoint> =
        readings.mapNotNull { reading ->
            reading.consumption?.let { EnergyChartPoint(reading.timestamp, it) }
        }

    fun buildYearlyChartPoints(readings: List<EnergyReadingEntity>): List<EnergyChartPoint> =
        readings
            .asSequence()
            .mapNotNull { reading ->
                reading.consumption?.let { consumption ->
                    calendarField(reading.timestamp, Calendar.YEAR) to consumption
                }
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (year, values) ->
                val timestamp = Calendar.getInstance().apply {
                    clear()
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, Calendar.JANUARY)
                    set(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis
                EnergyChartPoint(timestamp, values.sum())
            }

    private fun percentageVariation(current: Double, previous: Double): Double? =
        if (previous > 0.0) ((current - previous) / previous) * 100.0 else null

    private fun calendarField(timestamp: Long, field: Int): Int =
        Calendar.getInstance().apply { timeInMillis = timestamp }.get(field)
}
