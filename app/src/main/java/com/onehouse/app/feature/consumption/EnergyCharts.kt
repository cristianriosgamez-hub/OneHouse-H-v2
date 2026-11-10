package com.onehouse.app.feature.consumption

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.data.energy.EnergyChartPoint
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun EnergyAnalyticsCard(
    points: List<EnergyAnalyticsPoint>,
    projectedValue: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val current = points.lastOrNull()?.value ?: 0.0
    val previous = points.dropLast(1).lastOrNull()?.value ?: 0.0
    val variation = if (previous > 0.0) ((current - previous) / previous) * 100.0 else null
    val trendColor = when {
        variation == null -> TextoSecundario
        variation <= 0.0 -> EnergyGreen
        else -> EnergyRed
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EnergyCard, RoundedCornerShape(24.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Evolución energética", color = TextoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Electricidad · últimos 12 meses", color = TextoSecundario, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatNumber(current)} kWh", color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    variation?.let {
                        val arrow = if (it <= 0.0) "↓" else "↑"
                        "$arrow ${String.format(Locale.getDefault(), "%.1f", abs(it))}% mensual"
                    } ?: "Sin comparativa",
                    color = trendColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        DashboardEnergyChart(
            points = points,
            projectedValue = projectedValue,
            accent = EnergyBlue,
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.filterIndexed { index, _ -> index % 3 == 0 || index == points.lastIndex }.forEach { point ->
                Text(shortMonth(point.timestamp), color = TextoSecundario, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun DashboardEnergyChart(
    points: List<EnergyAnalyticsPoint>,
    projectedValue: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty() || points.all { it.value == 0.0 }) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Todavía no hay datos suficientes", color = TextoSecundario, fontSize = 13.sp)
        }
        return
    }
    var chartVisible by remember(points) { mutableStateOf(false) }
    LaunchedEffect(points) { chartVisible = true }
    val chartAlpha by animateFloatAsState(
        targetValue = if (chartVisible) 1f else 0f,
        animationSpec = tween(520),
        label = "dashboardEnergyChartAlpha"
    )
    val values = points.map { it.value }
    val chartValues = if (projectedValue > 0.0) values + projectedValue else values
    val min = chartValues.minOrNull() ?: 0.0
    val max = chartValues.maxOrNull() ?: 0.0
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0

    Canvas(modifier = modifier) {
        val horizontalPadding = 8.dp.toPx()
        val verticalPadding = 12.dp.toPx()
        val widthAvailable = size.width - horizontalPadding * 2
        val heightAvailable = size.height - verticalPadding * 2

        repeat(4) { index ->
            val y = verticalPadding + heightAvailable * index / 3f
            drawLine(BordeTarjeta.copy(alpha = 0.45f), Offset(horizontalPadding, y), Offset(size.width - horizontalPadding, y), 1.dp.toPx())
        }

        val linePath = Path()
        val areaPath = Path()
        points.forEachIndexed { index, point ->
            val x = horizontalPadding + widthAvailable * index / points.lastIndex.coerceAtLeast(1).toFloat()
            val normalized = ((point.value - min) / range).toFloat()
            val y = verticalPadding + heightAvailable * (1f - normalized)
            if (index == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, size.height - verticalPadding)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(size.width - horizontalPadding, size.height - verticalPadding)
        areaPath.close()
        drawPath(areaPath, Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f * chartAlpha), Color.Transparent)), style = Fill)
        drawPath(linePath, accent.copy(alpha = chartAlpha), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

        if (projectedValue > 0.0 && points.isNotEmpty()) {
            val lastValue = points.last().value
            val startY = verticalPadding + heightAvailable * (1f - ((lastValue - min) / range).toFloat())
            val endY = verticalPadding + heightAvailable * (1f - ((projectedValue - min) / range).toFloat())
            drawLine(
                color = EnergyOrange.copy(alpha = chartAlpha),
                start = Offset(size.width - horizontalPadding - widthAvailable / points.lastIndex.coerceAtLeast(1), startY),
                end = Offset(size.width - horizontalPadding, endY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
            drawCircle(EnergyOrange.copy(alpha = chartAlpha), 4.dp.toPx(), Offset(size.width - horizontalPadding, endY))
        }
    }
}

@Composable
internal fun EnergyLineChart(
    points: List<EnergyChartPoint>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No hay datos en este periodo", color = TextoSecundario, fontSize = 13.sp)
        }
        return
    }
    val values = points.map { it.value }
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier = modifier) {
        val horizontalPadding = 8.dp.toPx()
        val verticalPadding = 12.dp.toPx()
        val widthAvailable = size.width - horizontalPadding * 2
        val heightAvailable = size.height - verticalPadding * 2
        repeat(4) { index ->
            val y = verticalPadding + heightAvailable * index / 3f
            drawLine(BordeTarjeta.copy(alpha = 0.5f), Offset(horizontalPadding, y), Offset(size.width - horizontalPadding, y), 1.dp.toPx())
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = if (points.size == 1) size.width / 2f else horizontalPadding + widthAvailable * index / points.lastIndex.toFloat()
            val normalized = ((point.value - min) / range).toFloat()
            val y = verticalPadding + heightAvailable * (1f - normalized)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { index, point ->
            val x = if (points.size == 1) size.width / 2f else horizontalPadding + widthAvailable * index / points.lastIndex.toFloat()
            val normalized = ((point.value - min) / range).toFloat()
            val y = verticalPadding + heightAvailable * (1f - normalized)
            drawCircle(accent, 3.5.dp.toPx(), Offset(x, y))
        }
    }
}

private fun shortMonth(timestamp: Long): String =
    SimpleDateFormat("MMM", Locale.getDefault())
        .format(Date(timestamp))
        .replaceFirstChar { it.uppercase() }
