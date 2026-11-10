package com.onehouse.app.feature.consumption

import com.onehouse.app.data.energy.EnergyStatistics
import com.onehouse.app.data.local.EnergyReadingEntity
import java.util.Calendar

internal fun consumptionYear(timestamp: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

/** Uses the selected year's period consumption, never the cumulative meter value. */
internal fun selectedYearStatistics(readings: List<EnergyReadingEntity>, year: Int?): EnergyStatistics {
    val selected = if (year == null) readings else readings.filter { consumptionYear(it.timestamp) == year }
    val values = selected.mapNotNull { it.consumption }
    val previous = if (year == null) emptyList() else
        readings.filter { consumptionYear(it.timestamp) == year - 1 }.mapNotNull { it.consumption }
    val previousTotal = previous.sum()
    return EnergyStatistics(
        totalConsumption = values.sum(),
        totalCost = selected.sumOf { it.cost ?: 0.0 },
        average = values.takeIf { it.isNotEmpty() }?.average(),
        maximum = values.maxOrNull(),
        minimum = values.minOrNull(),
        variationPercent = if (year != null && values.isNotEmpty() && previousTotal > 0.0)
            (values.sum() - previousTotal) / previousTotal * 100.0 else null
    )
}
