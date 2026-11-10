package com.onehouse.app.feature.consumption

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.data.energy.EnergyDashboardState
import com.onehouse.app.data.energy.MeterType
import com.onehouse.app.data.energy.ReadingSource
import com.onehouse.app.data.local.EnergyReadingEntity
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.feature.rooms.detail.RoomHeader
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun EnergyMeterDetailScreen(
    state: EnergyDashboardState,
    onBack: () -> Unit,
    onPeriodSelected: (com.onehouse.app.data.energy.EnergyPeriod) -> Unit,
    onAddReading: () -> Unit,
    onEditReading: (EnergyReadingEntity) -> Unit,
    onDeleteReading: (EnergyReadingEntity) -> Unit,
    acsKnxValue: Double? = null
) {
    val type = state.selectedType ?: return
    val accent = accentFor(type)
    val latest = state.summaries.firstOrNull { it.type == type }?.latestReading
    // Int.MIN_VALUE means default/latest; null explicitly means all years.
    // Keep a user's selection when a reading is edited or added.
    var yearSelection by rememberSaveable(type) { mutableStateOf<Int?>(Int.MIN_VALUE) }
    val selectedYear = if (yearSelection == Int.MIN_VALUE) {
        state.allSelectedReadings.maxOfOrNull { readingYear(it.timestamp) }
    } else yearSelection
    val selectedStatistics = remember(state.allSelectedReadings, selectedYear) {
        selectedYearStatistics(state.allSelectedReadings, selectedYear)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior, Color.Black)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        RoomHeader(type.title, onBack)
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EnergyCard, RoundedCornerShape(26.dp))
                .border(1.dp, BordeTarjeta, RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = meterIcon(type),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (type == MeterType.ACS) {
                        Text("Lectura actual", color = TextoSecundario, fontSize = 11.sp)
                        Text(
                            acsKnxValue?.let { "${formatNumberFixed2(it)} ${type.unit}" }
                                ?: "Esperando lectura",
                            color = TextoPrincipal,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (acsKnxValue != null) {
                            Text(formatDate(System.currentTimeMillis()), color = TextoSecundario, fontSize = 11.sp)
                        }
                    } else {
                        Text("Última lectura", color = TextoSecundario, fontSize = 11.sp)
                        Text(
                            latest?.meterValue?.let { "${formatNumber(it)} ${type.unit}" } ?: "Sin lectura",
                            color = TextoPrincipal,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        latest?.let {
                            Text(formatDate(it.timestamp), color = TextoSecundario, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(onClick = onAddReading, modifier = Modifier.fillMaxWidth()) {
                Text("Añadir lectura")
            }
        }

        Spacer(Modifier.height(14.dp))
        PeriodSelector(state.selectedPeriod, onPeriodSelected)
        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EnergyCard, RoundedCornerShape(24.dp))
                .border(1.dp, BordeTarjeta, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Text("Evolución", color = TextoPrincipal, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.chartPoints.isNotEmpty()) {
                    if (state.selectedPeriod == com.onehouse.app.data.energy.EnergyPeriod.ALL) {
                        "${formatAxisDate(state.chartPoints.first().timestamp, state.selectedPeriod)} — ${formatAxisDate(state.chartPoints.last().timestamp, state.selectedPeriod)}"
                    } else {
                        "${formatMonth(state.chartPoints.first().timestamp)} — ${formatMonth(state.chartPoints.last().timestamp)}"
                    }
                } else {
                    "Sin datos para mostrar"
                },
                color = TextoSecundario,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(14.dp))
            val visibleChartPoints = if (state.selectedPeriod == com.onehouse.app.data.energy.EnergyPeriod.ALL) {
                state.chartPoints
            } else {
                state.chartPoints.takeLast(36)
            }
            val chartMin = visibleChartPoints.minOfOrNull { it.value } ?: 0.0
            val chartMax = visibleChartPoints.maxOfOrNull { it.value } ?: 0.0
            val chartMid = (chartMin + chartMax) / 2.0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.height(190.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("${formatNumber(chartMax)} ${type.unit}", color = TextoSecundario, fontSize = 9.sp)
                    Text(formatNumber(chartMid), color = TextoSecundario, fontSize = 9.sp)
                    Text(formatNumber(chartMin), color = TextoSecundario, fontSize = 9.sp)
                }
                Spacer(Modifier.width(8.dp))
                EnergyLineChart(
                    points = visibleChartPoints,
                    accent = accent,
                    modifier = Modifier.weight(1f).height(190.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val labels = visibleChartPoints
                labels.firstOrNull()?.let { Text(formatAxisDate(it.timestamp, state.selectedPeriod), color = TextoSecundario, fontSize = 9.sp) }
                if (labels.size > 2) Text(formatAxisDate(labels[labels.size / 2].timestamp, state.selectedPeriod), color = TextoSecundario, fontSize = 9.sp)
                labels.lastOrNull()?.let { Text(formatAxisDate(it.timestamp, state.selectedPeriod), color = TextoSecundario, fontSize = 9.sp) }
            }
        }

        Spacer(Modifier.height(14.dp))
        StatisticsPanel(type, selectedStatistics, selectedYear)
        Spacer(Modifier.height(14.dp))
        ReadingsPanel(
            readings = state.allSelectedReadings,
            selectedYear = selectedYear,
            onYearSelected = { yearSelection = it },
            onEdit = onEditReading,
            onDelete = onDeleteReading
        )
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun StatisticsPanel(type: MeterType, stats: com.onehouse.app.data.energy.EnergyStatistics, year: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EnergyCard, RoundedCornerShape(24.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text("Estadísticas", color = TextoPrincipal, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(year?.let { "Año $it" } ?: "Todos los años", color = TextoSecundario, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailStat("Total", formatNumber(stats.totalConsumption), type.unit, Modifier.weight(1f))
            if (type != MeterType.ACS && type != MeterType.CLIMATIZATION) {
                DetailStat("Coste", formatNumber(stats.totalCost), "€", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailStat("Media", stats.average?.let(::formatNumber) ?: "—", type.unit, Modifier.weight(1f))
            DetailStat("Máximo", stats.maximum?.let(::formatNumber) ?: "—", type.unit, Modifier.weight(1f))
            DetailStat("Mínimo", stats.minimum?.let(::formatNumber) ?: "—", type.unit, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        if (year != null) TrendLabel(stats.variationPercent)
    }
}

@Composable
private fun DetailStat(title: String, value: String, unit: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(EnergyCardAlt, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(title, color = TextoSecundario, fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            "$value $unit",
            color = TextoPrincipal,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ReadingsPanel(
    readings: List<EnergyReadingEntity>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit,
    onEdit: (EnergyReadingEntity) -> Unit,
    onDelete: (EnergyReadingEntity) -> Unit
) {
    var yearMenuExpanded by remember { mutableStateOf(false) }
    val years = remember(readings) {
        readings.asSequence()
            .map { readingYear(it.timestamp) }
            .distinct()
            .sortedDescending()
            .toList()
    }
    val visibleReadings = remember(readings, selectedYear) {
        val ordered = readings.sortedByDescending { it.timestamp }
        selectedYear?.let { year -> ordered.filter { readingYear(it.timestamp) == year } }
            ?: ordered.take(12)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EnergyCard, RoundedCornerShape(24.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Últimas lecturas", color = TextoPrincipal, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Box {
                Row(
                    modifier = Modifier
                        .background(EnergyCardAlt, RoundedCornerShape(14.dp))
                        .clickable { yearMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedYear?.toString() ?: "Todos",
                        color = TextoPrincipal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = "Seleccionar año",
                        tint = TextoSecundario,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = yearMenuExpanded,
                    onDismissRequest = { yearMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Todos") },
                        onClick = {
                            onYearSelected(null)
                            yearMenuExpanded = false
                        }
                    )
                    years.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                onYearSelected(year)
                                yearMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (selectedYear == null) "Mostrando las 12 lecturas más recientes" else "Lecturas de $selectedYear",
            color = TextoSecundario,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(10.dp))
        if (visibleReadings.isEmpty()) {
            Text("No hay lecturas para el año seleccionado", color = TextoSecundario, fontSize = 13.sp)
        }
        visibleReadings.forEachIndexed { index, reading ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EnergyCardAlt, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatDate(reading.timestamp), color = TextoPrincipal, fontWeight = FontWeight.Medium)
                    Text(
                        ReadingSource.entries.firstOrNull { it.storageValue == reading.source }?.label ?: reading.source,
                        color = TextoSecundario,
                        fontSize = 10.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        reading.consumption?.let { "${formatNumber(it)} ${reading.unit}" } ?: "—",
                        color = TextoPrincipal,
                        fontWeight = FontWeight.Bold
                    )
                    reading.cost?.let { Text("${formatNumber(it)} €", color = TextoSecundario, fontSize = 10.sp) }
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (reading.source == ReadingSource.MANUAL.storageValue) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar lectura",
                            tint = EnergyBlue,
                            modifier = Modifier
                                .size(23.dp)
                                .clickable { onEdit(reading) }
                                .padding(2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Borrar lectura",
                        tint = EnergyRed,
                        modifier = Modifier
                            .size(23.dp)
                            .clickable { onDelete(reading) }
                            .padding(2.dp)
                    )
                }
            }
        }
    }
}

private fun readingYear(timestamp: Long): Int =
    java.util.Calendar.getInstance().apply { timeInMillis = timestamp }.get(java.util.Calendar.YEAR)



private fun formatAxisDate(timestamp: Long, period: com.onehouse.app.data.energy.EnergyPeriod): String =
    SimpleDateFormat(if (period == com.onehouse.app.data.energy.EnergyPeriod.ALL) "yyyy" else "MMM yy", Locale.getDefault()).format(Date(timestamp))
