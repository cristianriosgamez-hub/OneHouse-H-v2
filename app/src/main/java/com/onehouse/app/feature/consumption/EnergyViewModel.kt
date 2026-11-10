package com.onehouse.app.feature.consumption

import com.onehouse.app.data.energy.EnergyDashboardState
import com.onehouse.app.data.energy.EnergyPeriod
import com.onehouse.app.data.energy.EnergyRepository
import com.onehouse.app.data.energy.MeterType
import com.onehouse.app.data.local.EnergyReadingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class EnergyAnalyticsPoint(
    val timestamp: Long,
    val value: Double
)

enum class SmartEnergyLevel { NORMAL, HIGH, CRITICAL }

data class SmartEnergyState(
    val monthlyGoalKwh: Double = 350.0,
    val currentMonthKwh: Double = 0.0,
    val projectedMonthKwh: Double = 0.0,
    val goalProgress: Float = 0f,
    val level: SmartEnergyLevel = SmartEnergyLevel.NORMAL,
    val alertMessage: String? = null,
    val recommendation: String = "Añade lecturas para generar recomendaciones personalizadas."
)

class EnergyViewModel(
    private val repository: EnergyRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var readingsJob: Job? = null
    private var allReadings: List<EnergyReadingEntity> = emptyList()

    private val _state = MutableStateFlow(EnergyDashboardState())
    val state: StateFlow<EnergyDashboardState> = _state.asStateFlow()

    private val _analyticsPoints = MutableStateFlow<List<EnergyAnalyticsPoint>>(emptyList())
    val analyticsPoints: StateFlow<List<EnergyAnalyticsPoint>> = _analyticsPoints.asStateFlow()

    private val _smartEnergy = MutableStateFlow(SmartEnergyState())
    val smartEnergy: StateFlow<SmartEnergyState> = _smartEnergy.asStateFlow()

    init {
        observeReadings()
    }

    fun openMeter(type: MeterType) {
        _state.value = _state.value.copy(selectedType = type)
        rebuildSelectedMeter()
    }

    fun closeMeter() {
        _state.value = _state.value.copy(
            selectedType = null,
            editorReading = null,
            isEditorVisible = false,
            readingPendingDeletion = null
        )
    }

    fun selectPeriod(period: EnergyPeriod) {
        _state.value = _state.value.copy(selectedPeriod = period)
        rebuildSelectedMeter()
    }

    fun addReading() {
        _state.value = _state.value.copy(editorReading = null, isEditorVisible = true)
    }

    fun editReading(reading: EnergyReadingEntity) {
        _state.value = _state.value.copy(editorReading = reading, isEditorVisible = true)
    }

    fun dismissEditor() {
        _state.value = _state.value.copy(editorReading = null, isEditorVisible = false)
    }

    fun requestDelete(reading: EnergyReadingEntity) {
        _state.value = _state.value.copy(readingPendingDeletion = reading)
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(readingPendingDeletion = null)
    }

    fun saveReading(reading: EnergyReadingEntity) {
        scope.launch {
            val normalized = normalizeCalculatedValues(reading)
            runCatching { repository.save(normalized) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        editorReading = null,
                        isEditorVisible = false,
                        message = "Lectura guardada"
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(message = "No se pudo guardar la lectura")
                }
        }
    }


    private fun normalizeCalculatedValues(reading: EnergyReadingEntity): EnergyReadingEntity {
        val type = MeterType.fromStorage(reading.meterType)
        val previous = allReadings
            .asSequence()
            .filter { it.meterType == reading.meterType && it.id != reading.id && it.timestamp < reading.timestamp }
            .maxByOrNull { it.timestamp }

        return if (type == MeterType.ACS) {
            val currentMeter = reading.meterValue
            val previousMeter = previous?.meterValue
            val calculatedConsumption = if (currentMeter != null && previousMeter != null) {
                (currentMeter - previousMeter).takeIf { it >= 0.0 }
            } else reading.consumption
            reading.copy(consumption = calculatedConsumption)
        } else {
            val consumption = reading.consumption
            val previousMeter = previous?.meterValue ?: 0.0
            val calculatedMeter = consumption?.let { previousMeter + it }
            reading.copy(meterValue = calculatedMeter)
        }
    }

    fun confirmDelete() {
        val reading = _state.value.readingPendingDeletion ?: return
        scope.launch {
            runCatching { repository.delete(reading) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        readingPendingDeletion = null,
                        message = "Lectura eliminada"
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        readingPendingDeletion = null,
                        message = "No se pudo eliminar la lectura"
                    )
                }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun close() {
        readingsJob?.cancel()
        scope.cancel()
    }

    private fun observeReadings() {
        readingsJob?.cancel()
        readingsJob = scope.launch {
            repository.observeAll().collect { readings ->
                allReadings = readings
                val summaries = repository.buildSummaries(readings)
                val analytics = buildElectricityAnalytics(readings)
                _analyticsPoints.value = analytics
                _smartEnergy.value = buildSmartEnergy(analytics)
                _state.value = _state.value.copy(
                    isLoading = false,
                    summaries = summaries,
                    overview = repository.buildOverview(readings, summaries)
                )
                rebuildSelectedMeter()
            }
        }
    }

    private fun buildElectricityAnalytics(
        readings: List<EnergyReadingEntity>,
        now: Long = System.currentTimeMillis()
    ): List<EnergyAnalyticsPoint> {
        val monthStarts = (11 downTo 0).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, -offset)
            }.timeInMillis
        }

        return monthStarts.map { monthStart ->
            val month = Calendar.getInstance().apply { timeInMillis = monthStart }
            val yearValue = month.get(Calendar.YEAR)
            val monthValue = month.get(Calendar.MONTH)
            val totalKwh = readings.asSequence()
                .filter { reading ->
                    val calendar = Calendar.getInstance().apply { timeInMillis = reading.timestamp }
                    calendar.get(Calendar.YEAR) == yearValue &&
                        calendar.get(Calendar.MONTH) == monthValue
                }
                .sumOf { reading ->
                    when (MeterType.fromStorage(reading.meterType)) {
                        MeterType.ENDESA -> reading.consumption ?: 0.0
                        MeterType.CLIMATIZATION -> (reading.consumption ?: 0.0) * 1_000.0
                        else -> 0.0
                    }
                }
            EnergyAnalyticsPoint(monthStart, totalKwh)
        }
    }

    private fun buildSmartEnergy(
        analytics: List<EnergyAnalyticsPoint>,
        now: Long = System.currentTimeMillis()
    ): SmartEnergyState {
        val currentMonth = analytics.lastOrNull()?.value ?: 0.0
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val elapsedDays = calendar.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(elapsedDays)
        val projected = if (currentMonth > 0.0) currentMonth / elapsedDays * totalDays else 0.0

        val completedMonths = analytics.dropLast(1).map { it.value }.filter { it > 0.0 }
        val baseline = completedMonths.takeLast(6).average().takeIf { !it.isNaN() && it > 0.0 } ?: 350.0
        val goal = (baseline * 0.95).coerceAtLeast(100.0)
        val progress = (projected / goal).toFloat().coerceAtLeast(0f)
        val level = when {
            progress >= 1.15f -> SmartEnergyLevel.CRITICAL
            progress >= 0.95f -> SmartEnergyLevel.HIGH
            else -> SmartEnergyLevel.NORMAL
        }
        val alert = when (level) {
            SmartEnergyLevel.NORMAL -> null
            SmartEnergyLevel.HIGH -> "La proyección mensual está cerca del objetivo."
            SmartEnergyLevel.CRITICAL -> "La proyección supera el objetivo en ${kotlin.math.round((progress - 1f) * 100).toInt()}%."
        }
        val recommendation = when {
            currentMonth <= 0.0 -> "Añade lecturas para generar recomendaciones personalizadas."
            level == SmartEnergyLevel.CRITICAL -> "Reduce climatización en horas punta y revisa consumos en espera."
            level == SmartEnergyLevel.HIGH -> "Ajusta un grado la climatización para recuperar margen este mes."
            projected < goal * 0.8 -> "Buen ritmo: mantén tus hábitos actuales."
            else -> "El consumo está controlado; concentra cargas fuera de horas punta."
        }
        return SmartEnergyState(
            monthlyGoalKwh = goal,
            currentMonthKwh = currentMonth,
            projectedMonthKwh = projected,
            goalProgress = progress,
            level = level,
            alertMessage = alert,
            recommendation = recommendation
        )
    }

    private fun rebuildSelectedMeter() {
        val currentState = _state.value
        val type = currentState.selectedType ?: return
        val selectedTypeReadings = allReadings.filter { it.meterType == type.storageValue }
        val filtered = repository.readingsForPeriod(
            readings = allReadings,
            type = type,
            period = currentState.selectedPeriod
        )
        _state.value = currentState.copy(
            selectedReadings = filtered,
            allSelectedReadings = selectedTypeReadings,
            chartPoints = if (currentState.selectedPeriod == EnergyPeriod.ALL) {
                repository.buildYearlyChartPoints(filtered)
            } else {
                repository.buildChartPoints(filtered)
            },
            statistics = repository.buildStatistics(
                allReadings = allReadings,
                type = type,
                periodReadings = filtered
            )
        )
    }
}
