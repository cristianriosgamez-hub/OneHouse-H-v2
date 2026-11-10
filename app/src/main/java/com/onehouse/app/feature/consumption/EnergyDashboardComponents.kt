package com.onehouse.app.feature.consumption

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.data.energy.EnergyOverview
import com.onehouse.app.data.energy.EnergyPeriod
import com.onehouse.app.data.energy.MeterSummary
import com.onehouse.app.data.energy.MeterType
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.design.OneHouseCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


internal val EnergyBlue = Color(0xFF168EFF)
internal val EnergyOrange = Color(0xFFFF9A3D)
internal val EnergyRed = Color(0xFFFF4D56)
internal val EnergyAqua = Color(0xFF35D6C7)
internal val EnergyGreen = Color(0xFF5BD18A)
internal val EnergyCard = Color(0xFF0A1926)
internal val EnergyCardAlt = Color(0xFF0D2130)

@Composable
internal fun EnergyOverviewCard(
    overview: EnergyOverview,
    summaries: List<MeterSummary>,
    modifier: Modifier = Modifier
) {
    val endesa = summaries.firstOrNull { it.type == MeterType.ENDESA }
    val climate = summaries.firstOrNull { it.type == MeterType.CLIMATIZATION }
    val electricityMonthKwh =
        (endesa?.monthConsumption ?: 0.0) +
            (climate?.monthConsumption ?: 0.0) * 1_000.0
    val yearlyVariation = endesa?.yearVariationPercent
    val status = when {
        overview.totalReadings == 0 -> "Sin datos"
        yearlyVariation == null -> "Consumo estable"
        yearlyVariation <= 0.0 -> "Consumo eficiente"
        yearlyVariation < 10.0 -> "Consumo normal"
        else -> "Consumo elevado"
    }
    val statusColor = when {
        overview.totalReadings == 0 -> TextoSecundario
        yearlyVariation == null -> EnergyBlue
        yearlyVariation <= 0.0 -> EnergyGreen
        yearlyVariation < 10.0 -> EnergyOrange
        else -> EnergyRed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        EnergyBlue.copy(alpha = 0.24f),
                        EnergyCard,
                        EnergyCardAlt
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .border(1.dp, EnergyBlue.copy(alpha = 0.38f), RoundedCornerShape(28.dp))
            .padding(21.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CONSUMO ELÉCTRICO ANUAL",
                    color = EnergyBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "${formatNumber(overview.electricityYearKwh)} kWh",
                    color = TextoPrincipal,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                TrendLabel(yearlyVariation)
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(EnergyBlue.copy(alpha = 0.18f), RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("ϟ", color = EnergyBlue, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactMetric(
                "Este mes",
                "${formatNumber(electricityMonthKwh)} kWh",
                Modifier.weight(1f)
            )
            CompactMetric(
                "Coste anual",
                "${formatNumber(overview.totalYearCost)} €",
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                status,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            overview.lastUpdatedAt?.let {
                Text("Actualizado ${formatDate(it)}", color = TextoSecundario, fontSize = 10.sp)
            }
        }
    }
}

@Composable
internal fun EnergyDistributionCard(
    summaries: List<MeterSummary>,
    modifier: Modifier = Modifier
) {
    val endesa = summaries.firstOrNull { it.type == MeterType.ENDESA }?.yearConsumption ?: 0.0
    val climateMwh =
        summaries.firstOrNull { it.type == MeterType.CLIMATIZATION }?.yearConsumption ?: 0.0
    val climate = climateMwh * 1_000.0
    val total = (endesa + climate).takeIf { it > 0.0 } ?: 1.0
    val endesaShare = (endesa / total).toFloat().coerceIn(0f, 1f)
    val climateShare = (climate / total).toFloat().coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EnergyCard, RoundedCornerShape(24.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Text(
            "Distribución eléctrica",
            color = TextoPrincipal,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Reparto anual entre consumo general y climatización",
            color = TextoSecundario,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(18.dp))

        DistributionRow(
            title = "Consumo general",
            value = endesa,
            share = endesaShare,
            accent = EnergyBlue
        )
        Spacer(Modifier.height(14.dp))
        DistributionRow(
            title = "Climatización",
            value = climate,
            share = climateShare,
            accent = EnergyOrange
        )
    }
}

@Composable
private fun DistributionRow(
    title: String,
    value: Double,
    share: Float,
    accent: Color
) {
    val animatedShare = animateFloatAsState(
        targetValue = share.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 360f, dampingRatio = 0.84f),
        label = "energyDistributionShare"
    ).value

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(accent, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, color = TextoPrincipal, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            "${(share * 100).toInt()}%",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(Modifier.height(7.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .background(EnergyCardAlt, RoundedCornerShape(50))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedShare)
                .background(accent, RoundedCornerShape(50))
        )
    }
    Spacer(Modifier.height(5.dp))
    Text("${formatNumber(value)} kWh", color = TextoSecundario, fontSize = 10.sp)
}

@Composable
internal fun EnergySummaryCard(
    summary: MeterSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = accentFor(summary.type)
    val showsCost = summary.type != MeterType.ACS && summary.type != MeterType.CLIMATIZATION
    OneHouseCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = meterIcon(summary.type),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        summary.type.shortTitle,
                        color = TextoPrincipal,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        summary.latestReading?.let { relativeUpdateText(it.timestamp) } ?: "Sin lecturas",
                        color = TextoSecundario,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Abrir detalle",
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("Este mes", color = TextoSecundario, fontSize = 10.sp)
            Text(
                "${formatNumber(summary.monthConsumption)} ${summary.type.unit}",
                color = TextoPrincipal,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(11.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BordeTarjeta.copy(alpha = 0.65f))
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Año", color = TextoSecundario, fontSize = 9.sp)
                    Text(
                        "${formatNumber(summary.yearConsumption)} ${summary.type.unit}",
                        color = TextoPrincipal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showsCost) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Coste", color = TextoSecundario, fontSize = 9.sp)
                        Text(
                            "${formatNumber(summary.yearCost)} €",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            TrendLabel(summary.yearVariationPercent)
        }
    }
}

@Composable
private fun CompactMetric(title: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(EnergyCardAlt, RoundedCornerShape(15.dp))
            .padding(11.dp)
    ) {
        Text(title, color = TextoSecundario, fontSize = 10.sp)
        Text(
            value,
            color = TextoPrincipal,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
internal fun TrendLabel(variation: Double?) {
    val color = when {
        variation == null -> TextoSecundario
        variation <= 0 -> EnergyGreen
        else -> EnergyRed
    }
    val text = variation?.let {
        val arrow = if (it <= 0) "↓" else "↑"
        "$arrow ${String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(it))}% frente al año anterior"
    } ?: "Sin comparativa anual"
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
}

@Composable
internal fun PeriodSelector(
    selected: EnergyPeriod,
    onSelected: (EnergyPeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EnergyCardAlt, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        EnergyPeriod.entries.filter { it != EnergyPeriod.MONTH }.forEach { period ->
            val active = selected == period
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) EnergyBlue else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelected(period) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    period.label,
                    color = if (active) Color.White else TextoSecundario,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

internal fun meterIcon(type: MeterType): ImageVector = when (type) {
    MeterType.ENDESA -> Icons.Rounded.Bolt
    MeterType.AGBAR -> Icons.Rounded.WaterDrop
    MeterType.CLIMATIZATION -> Icons.Rounded.AcUnit
    MeterType.ACS -> Icons.Rounded.LocalFireDepartment
}

private fun relativeUpdateText(timestamp: Long): String {
    val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        elapsed < hour -> "Hace ${maxOf(1L, elapsed / minute)} min"
        elapsed < day -> "Hace ${elapsed / hour} h"
        elapsed < 2L * day -> "Ayer"
        elapsed < 8L * day -> "Hace ${elapsed / day} días"
        else -> formatDate(timestamp)
    }
}

internal fun accentFor(type: MeterType): Color = when (type) {
    MeterType.ENDESA -> EnergyBlue
    MeterType.CLIMATIZATION -> EnergyOrange
    MeterType.ACS -> EnergyRed
    MeterType.AGBAR -> EnergyAqua
}

internal val displayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
internal val inputDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
internal val monthDateFormat = SimpleDateFormat("MMM yy", Locale.getDefault())

internal fun formatDate(timestamp: Long): String = displayDateFormat.format(Date(timestamp))
internal fun formatMonth(timestamp: Long): String = monthDateFormat.format(Date(timestamp))
internal fun formatNumber(value: Double): String {
    val symbols = java.text.DecimalFormatSymbols(Locale.getDefault())
    return java.text.DecimalFormat("0.##", symbols).apply { isGroupingUsed = false }.format(value)
}
internal fun plainNumber(value: Double): String =
    java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')
internal fun formatNumberFixed2(value: Double): String {
    val symbols = java.text.DecimalFormatSymbols(Locale.getDefault())
    return java.text.DecimalFormat("0.00", symbols).apply { isGroupingUsed = false }.format(value)
}
internal fun plainNumberFixed2(value: Double): String =
    java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')
internal fun String.toNormalizedDoubleOrNull(): Double? =
    trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()

@Composable
internal fun SmartEnergyCard(
    state: SmartEnergyState,
    modifier: Modifier = Modifier
) {
    val statusColor = when (state.level) {
        SmartEnergyLevel.NORMAL -> EnergyGreen
        SmartEnergyLevel.HIGH -> EnergyOrange
        SmartEnergyLevel.CRITICAL -> EnergyRed
    }
    val statusLabel = when (state.level) {
        SmartEnergyLevel.NORMAL -> "Normal"
        SmartEnergyLevel.HIGH -> "Alto"
        SmartEnergyLevel.CRITICAL -> "Crítico"
    }
    val targetProgress = state.goalProgress.coerceIn(0f, 1f)
    val progress = animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(stiffness = 320f, dampingRatio = 0.82f),
        label = "smartEnergyProgress"
    ).value

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(statusColor.copy(alpha = 0.18f), EnergyCard)
                ),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Smart Energy", color = TextoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Objetivo, alertas y recomendación mensual", color = TextoSecundario, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.17f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmartMetric(
                title = "Proyección",
                value = "${formatNumber(state.projectedMonthKwh)} kWh",
                modifier = Modifier.weight(1f)
            )
            SmartMetric(
                title = "Objetivo",
                value = "${formatNumber(state.monthlyGoalKwh)} kWh",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Progreso mensual", color = TextoSecundario, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                "${(state.goalProgress * 100f).toInt()}%",
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(EnergyCardAlt, RoundedCornerShape(50))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(statusColor, RoundedCornerShape(50))
            )
        }

        state.alertMessage?.let { alert ->
            Spacer(Modifier.height(14.dp))
            Text("⚠ $alert", color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EnergyCardAlt, RoundedCornerShape(16.dp))
                .padding(13.dp)
        ) {
            Text("Recomendación", color = TextoSecundario, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Text(state.recommendation, color = TextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SmartMetric(title: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(EnergyCardAlt, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(title, color = TextoSecundario, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = TextoPrincipal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
