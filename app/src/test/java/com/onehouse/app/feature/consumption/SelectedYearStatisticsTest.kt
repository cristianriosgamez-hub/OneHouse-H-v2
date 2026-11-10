package com.onehouse.app.feature.consumption

import com.onehouse.app.data.local.EnergyReadingEntity
import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class SelectedYearStatisticsTest {
    private fun reading(year: Int, consumption: Double?, cost: Double? = null) = EnergyReadingEntity(
        meterType = "ACS", timestamp = Calendar.getInstance().apply { clear(); set(year, 5, 1) }.timeInMillis,
        meterValue = 9000.0, consumption = consumption, cost = cost, unit = "kW", source = "MANUAL"
    )

    @Test fun selectingOldYearChangesEveryStatisticAndComparison() {
        val readings = listOf(reading(2021, 40.0), reading(2022, 10.0, 2.0), reading(2022, 20.0, 3.0), reading(2026, 999.0))
        val stats = selectedYearStatistics(readings, 2022)
        assertEquals(30.0, stats.totalConsumption, 0.001)
        assertEquals(5.0, stats.totalCost, 0.001)
        assertEquals(15.0, stats.average!!, 0.001)
        assertEquals(20.0, stats.maximum!!, 0.001)
        assertEquals(10.0, stats.minimum!!, 0.001)
        assertEquals(-25.0, stats.variationPercent!!, 0.001)
    }

    @Test fun allYearsDoesNotClaimAnnualComparison() {
        val stats = selectedYearStatistics(listOf(reading(2021, 40.0), reading(2022, 10.0)), null)
        assertEquals(50.0, stats.totalConsumption, 0.001)
        assertNull(stats.variationPercent)
    }

    @Test fun missingValuesAreNotCountedAsZeroButRealZeroIsIncluded() {
        val stats = selectedYearStatistics(listOf(reading(2022, null), reading(2022, 0.0), reading(2022, 10.0)), 2022)
        assertEquals(5.0, stats.average!!, 0.001)
        assertEquals(0.0, stats.minimum!!, 0.001)
        assertNull(stats.variationPercent)
        assertNull(selectedYearStatistics(emptyList(), 2022).average)
        assertNull(selectedYearStatistics(listOf(reading(2021, 0.0), reading(2022, 10.0)), 2022).variationPercent)
    }
}
