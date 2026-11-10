package com.onehouse.app.feature.climate

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin

internal val ClimateBackgroundTop = Color(0xFF071421)
internal val ClimateBackgroundBottom = Color(0xFF02070D)
internal val ClimateCard = Color(0xFF0A1723)
internal val ClimateCardSecondary = Color(0xFF0D2130)
internal val ClimateBorder = Color(0xFF17364B)
internal val ClimateBlue = Color(0xFF168EFF)
internal val ClimateCyan = Color(0xFF19D3FF)
internal val ClimateOrange = Color(0xFFFF8A24)
internal val ClimateRed = Color(0xFFFF3B30)
internal val ClimatePurple = Color(0xFF8E62FF)
internal val ClimateGreen = Color(0xFF21D991)
internal val ClimateYellow = Color(0xFFFFB52E)
internal val ClimateText = Color(0xFFF7FAFC)
internal val ClimateTextSecondary = Color(0xFF9AAEBC)
internal val ClimateMuted = Color(0xFF5A7181)

enum class ClimateIconType {
    COLD,
    HEAT,
    FAN,
    DRY,
    AUTO,
    POWER,
    HUMIDITY,
    THERMOSTAT,
    AIR_QUALITY
}

enum class ClimateMode(
    val label: String,
    val symbol: String,
    val accent: Color,
    val iconType: ClimateIconType
) {
    COLD("Frío", "❄", ClimateBlue, ClimateIconType.COLD),
    HEAT("Calor", "☀", ClimateYellow, ClimateIconType.HEAT),
    FAN("Vent.", "✣", ClimateGreen, ClimateIconType.FAN),
    DRY("Dry", "◉", ClimateCyan, ClimateIconType.DRY),
    AUTO("Auto", "A", ClimatePurple, ClimateIconType.AUTO)
}

enum class FanSpeed(val label: String) {
    LOW("Baja"),
    MEDIUM("Media"),
    HIGH("Alta")
}


@Composable
internal fun ClimateVectorIcon(
    type: ClimateIconType,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (type) {
            ClimateIconType.COLD -> drawColdIcon(color)
            ClimateIconType.HEAT -> drawHeatIcon(color)
            ClimateIconType.FAN -> drawFanIcon(color)
            ClimateIconType.DRY -> drawDryIcon(color)
            ClimateIconType.AUTO -> drawAutoIcon(color)
            ClimateIconType.POWER -> drawPowerIcon(color)
            ClimateIconType.HUMIDITY -> drawHumidityIcon(color)
            ClimateIconType.THERMOSTAT -> drawThermostatIcon(color)
            ClimateIconType.AIR_QUALITY -> drawAirQualityIcon(color)
        }
    }
}

private fun DrawScope.drawColdIcon(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.34f
    val stroke = size.minDimension * 0.075f

    repeat(3) { index ->
        val angle = Math.toRadians((index * 60f).toDouble())
        val dx = cos(angle).toFloat() * radius
        val dy = sin(angle).toFloat() * radius

        drawLine(
            color = color,
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = color,
        radius = size.minDimension * 0.07f,
        center = center
    )
}

private fun DrawScope.drawHeatIcon(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val base = size.minDimension
    val stroke = base * 0.07f

    drawCircle(
        color = color,
        radius = base * 0.19f,
        center = center
    )

    repeat(8) { index ->
        val angle = Math.toRadians((index * 45f).toDouble())
        val inner = base * 0.29f
        val outer = base * 0.42f

        drawLine(
            color = color,
            start = Offset(
                center.x + cos(angle).toFloat() * inner,
                center.y + sin(angle).toFloat() * inner
            ),
            end = Offset(
                center.x + cos(angle).toFloat() * outer,
                center.y + sin(angle).toFloat() * outer
            ),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawFanIcon(color: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val base = size.minDimension

    drawCircle(
        color = color,
        radius = base * 0.075f,
        center = center
    )

    repeat(3) { index ->
        val angle = Math.toRadians((index * 120f - 90f).toDouble())
        val bladeCenter = Offset(
            center.x + cos(angle).toFloat() * base * 0.19f,
            center.y + sin(angle).toFloat() * base * 0.19f
        )

        drawOval(
            color = color,
            topLeft = Offset(
                bladeCenter.x - base * 0.11f,
                bladeCenter.y - base * 0.19f
            ),
            size = Size(base * 0.22f, base * 0.38f)
        )
    }
}

private fun DrawScope.drawDryIcon(color: Color) {
    val path = Path()
    val w = size.width
    val h = size.height

    path.moveTo(w * 0.50f, h * 0.08f)
    path.cubicTo(
        w * 0.40f, h * 0.25f,
        w * 0.23f, h * 0.45f,
        w * 0.23f, h * 0.62f
    )
    path.cubicTo(
        w * 0.23f, h * 0.83f,
        w * 0.35f, h * 0.94f,
        w * 0.50f, h * 0.94f
    )
    path.cubicTo(
        w * 0.65f, h * 0.94f,
        w * 0.77f, h * 0.83f,
        w * 0.77f, h * 0.62f
    )
    path.cubicTo(
        w * 0.77f, h * 0.45f,
        w * 0.60f, h * 0.25f,
        w * 0.50f, h * 0.08f
    )
    path.close()

    drawPath(color = color, path = path)
}

private fun DrawScope.drawAutoIcon(color: Color) {
    val stroke = size.minDimension * 0.075f
    val inset = size.minDimension * 0.16f

    drawArc(
        color = color,
        startAngle = 210f,
        sweepAngle = 225f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )

    val path = Path().apply {
        moveTo(size.width * 0.73f, size.height * 0.12f)
        lineTo(size.width * 0.90f, size.height * 0.20f)
        lineTo(size.width * 0.76f, size.height * 0.31f)
        close()
    }
    drawPath(path = path, color = color)

    drawCircle(
        color = color,
        radius = size.minDimension * 0.055f,
        center = Offset(size.width * 0.50f, size.height * 0.50f)
    )
}


private fun DrawScope.drawHumidityIcon(color: Color) {
    val cx = size.width / 2f
    val top = size.height * 0.18f
    val bottom = size.height * 0.84f
    val half = size.width * 0.24f
    val path = Path().apply {
        moveTo(cx, top)
        cubicTo(cx - half * 0.25f, top + size.height * 0.16f, cx - half, top + size.height * 0.29f, cx - half, bottom - size.height * 0.16f)
        cubicTo(cx - half, bottom + size.height * 0.02f, cx - half * 0.45f, bottom, cx, bottom)
        cubicTo(cx + half * 0.45f, bottom, cx + half, bottom + size.height * 0.02f, cx + half, bottom - size.height * 0.16f)
        cubicTo(cx + half, top + size.height * 0.29f, cx + half * 0.25f, top + size.height * 0.16f, cx, top)
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = size.minDimension * 0.075f, cap = StrokeCap.Round))
    drawArc(
        color = color,
        startAngle = 18f,
        sweepAngle = 112f,
        useCenter = false,
        topLeft = Offset(size.width * 0.39f, size.height * 0.49f),
        size = Size(size.width * 0.27f, size.height * 0.22f),
        style = Stroke(width = size.minDimension * 0.065f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawThermostatIcon(color: Color) {
    val stroke = size.minDimension * 0.075f
    val cx = size.width * 0.5f
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.39f, size.height * 0.14f),
        size = Size(size.width * 0.22f, size.height * 0.50f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.11f),
        style = Stroke(width = stroke)
    )
    drawLine(
        color = color,
        start = Offset(cx, size.height * 0.31f),
        end = Offset(cx, size.height * 0.67f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = color,
        radius = size.minDimension * 0.18f,
        center = Offset(cx, size.height * 0.72f),
        style = Stroke(width = stroke)
    )
}

private fun DrawScope.drawAirQualityIcon(color: Color) {
    val stroke = size.minDimension * 0.07f
    val ys = listOf(0.32f, 0.50f, 0.68f)
    ys.forEachIndexed { index, y ->
        val startX = if (index == 1) 0.22f else 0.30f
        val endX = if (index == 1) 0.82f else 0.74f
        val path = Path().apply {
            moveTo(size.width * startX, size.height * y)
            cubicTo(
                size.width * 0.42f, size.height * (y - 0.08f),
                size.width * 0.56f, size.height * (y + 0.08f),
                size.width * endX, size.height * y
            )
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
private fun DrawScope.drawPowerIcon(color: Color) {
    val base = size.minDimension
    val stroke = base * 0.085f
    val inset = base * 0.13f

    drawArc(
        color = color,
        startAngle = -48f,
        sweepAngle = 276f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )

    drawLine(
        color = color,
        start = Offset(size.width / 2f, size.height * 0.08f),
        end = Offset(size.width / 2f, size.height * 0.48f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
}

/**
 * Escala térmica solicitada:
 * 16.0º–25.0º: azul
 * 25.5º–27.0º: naranja
 * 27.5º–30.0º: rojo
 *
 * Entre 25.0º–25.5º y 27.0º–27.5º se aplica un difuminado.
 */
private fun temperatureColor(temperature: Float): Color {
    return when {
        temperature <= 25f -> ClimateBlue

        temperature < 25.5f -> {
            val fraction = ((temperature - 25f) / 0.5f).coerceIn(0f, 1f)
            lerp(ClimateBlue, ClimateOrange, fraction)
        }

        temperature <= 27f -> ClimateOrange

        temperature < 27.5f -> {
            val fraction = ((temperature - 27f) / 0.5f).coerceIn(0f, 1f)
            lerp(ClimateOrange, ClimateRed, fraction)
        }

        else -> ClimateRed
    }
}

@Composable
internal fun ClimateSystemCard(
    enabled: Boolean,
    selectedMode: ClimateMode?,
    onEnabledChange: (Boolean) -> Unit
) {
    val activeAccent by animateColorAsState(
        targetValue = if (enabled) (selectedMode?.accent ?: ClimateGreen) else ClimateMuted,
        label = "systemCardAccent"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.60f,
        label = "systemCardAlpha"
    )

    val statusText = when {
        !enabled -> "Detenido"
        selectedMode == null -> "Sistema encendido"
        selectedMode == ClimateMode.COLD -> "Refrigerando"
        selectedMode == ClimateMode.HEAT -> "Calentando"
        selectedMode == ClimateMode.FAN -> "Ventilando"
        selectedMode == ClimateMode.DRY -> "Deshumidificando"
        else -> "Regulación automática"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(ClimateCardSecondary, ClimateCard)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = ClimateBorder,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            activeAccent.copy(alpha = if (enabled) 0.82f else 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        activeAccent.copy(alpha = if (enabled) 0.10f else 0.04f),
                        RoundedCornerShape(15.dp)
                    )
                    .border(
                        1.dp,
                        activeAccent.copy(alpha = if (enabled) 0.45f else 0.15f),
                        RoundedCornerShape(15.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                ClimateVectorIcon(
                    type = if (enabled) selectedMode?.iconType ?: ClimateIconType.POWER else ClimateIconType.POWER,
                    color = activeAccent.copy(alpha = cardAlpha),
                    modifier = Modifier.size(25.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Climatización central",
                    color = ClimateText.copy(alpha = cardAlpha),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (enabled) selectedMode?.let { "Modo ${it.label}" } ?: "Encendido" else "Sistema desactivado",
                        color = activeAccent.copy(alpha = cardAlpha),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (enabled) activeAccent else ClimateMuted,
                                CircleShape
                            )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = statusText,
                        color = if (enabled) activeAccent else ClimateTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = selectedMode?.accent ?: ClimateMuted,
                    checkedBorderColor = selectedMode?.accent ?: ClimateMuted,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = ClimateCardSecondary,
                    uncheckedBorderColor = ClimateBorder
                )
            )
        }
    }
}

@Composable
internal fun TemperatureControl(
    targetTemperature: Float?,
    enabled: Boolean,
    mode: ClimateMode?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    PremiumCard {
        val safeTargetTemperature = targetTemperature ?: 16f
        val normalizedTarget = ((safeTargetTemperature - 16f) / 18f).coerceIn(0f, 1f)

        val animatedTemperature by animateFloatAsState(
            targetValue = safeTargetTemperature,
            label = "animatedTemperature"
        )

        val progress by animateFloatAsState(
            targetValue = if (enabled) normalizedTarget else 0f,
            label = "dialProgress"
        )

        val targetAccent = if (enabled) {
            temperatureColor(animatedTemperature)
        } else {
            ClimateMuted
        }

        val activeAccent by animateColorAsState(
            targetValue = targetAccent,
            label = "temperatureAccent"
        )

        val contentAlpha by animateFloatAsState(
            targetValue = if (enabled) 1f else 0.42f,
            label = "dialAlpha"
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(236.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(
                        x = (size.width - diameter) / 2f,
                        y = (size.height - diameter) / 2f
                    )

                    val startAngle = 135f
                    val totalSweep = 270f
                    val segmentCount = 144
                    val segmentSweep = totalSweep / segmentCount
                    val activeSegments = (segmentCount * progress).toInt()

                    /*
                     * Dibujamos el anillo por pequeños segmentos.
                     * Cada segmento representa una temperatura entre 16º y 34º.
                     */
                    repeat(segmentCount) { index ->
                        val segmentProgress = index.toFloat() / (segmentCount - 1).toFloat()
                        val representedTemperature = 16f + (18f * segmentProgress)
                        val segmentColor = temperatureColor(representedTemperature)

                        val isActive = index <= activeSegments
                        val alpha = if (isActive && enabled) 1f else 0.16f

                        drawArc(
                            color = segmentColor.copy(alpha = alpha),
                            startAngle = startAngle + (segmentSweep * index),
                            sweepAngle = segmentSweep + 0.35f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )
                    }

                    if (enabled) {
                        val endAngleDegrees = startAngle + (totalSweep * progress)
                        val endAngleRadians = Math.toRadians(endAngleDegrees.toDouble())
                        val radius = diameter / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        val markerCenter = Offset(
                            x = center.x + radius * cos(endAngleRadians).toFloat(),
                            y = center.y + radius * sin(endAngleRadians).toFloat()
                        )

                        drawCircle(
                            color = activeAccent.copy(alpha = 0.15f),
                            radius = 16.dp.toPx(),
                            center = markerCenter
                        )
                        drawCircle(
                            color = activeAccent.copy(alpha = 0.28f),
                            radius = 11.dp.toPx(),
                            center = markerCenter
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 7.dp.toPx(),
                            center = markerCenter
                        )
                        drawCircle(
                            color = activeAccent,
                            radius = 4.dp.toPx(),
                            center = markerCenter
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Temperatura objetivo",
                        color = ClimateTextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = targetTemperature?.let { String.format("%.1f°", it) } ?: "---",
                        color = activeAccent.copy(alpha = contentAlpha),
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        text = when {
                            targetTemperature == null -> "Esperando consigna KNX"
                            enabled -> mode?.let { "${it.label} · Consigna general" } ?: "Modo KNX no disponible"
                            else -> "Control desactivado"
                        },
                        color = activeAccent.copy(alpha = contentAlpha),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TemperatureButton(
                    text = "−",
                    enabled = enabled,
                    accent = activeAccent,
                    onClick = onDecrease,
                    modifier = Modifier.weight(1f)
                )

                Column(
                    modifier = Modifier.weight(1.15f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rango permitido",
                        color = ClimateTextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "16,0° — 34,0°",
                        color = ClimateText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                TemperatureButton(
                    text = "+",
                    enabled = enabled,
                    accent = activeAccent,
                    onClick = onIncrease,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TemperatureButton(
    text: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        if (enabled) ClimateCardSecondary else ClimateCard,
                        ClimateCard
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .border(
                width = 1.dp,
                color = if (enabled) accent else ClimateBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) ClimateText else ClimateMuted,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
internal fun AmbientTemperatures(
    diningTemperature: Float?,
    suiteTemperature: Float?
) {
    Column {
        Text(
            text = "Temperatura ambiente",
            color = ClimateText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(ClimateCardSecondary, ClimateCard)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    color = ClimateBorder,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactAmbientValue(
                room = "Comedor",
                symbol = "⌂",
                accent = ClimateBlue,
                temperature = diningTemperature,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(54.dp)
                    .background(ClimateBorder)
            )

            CompactAmbientValue(
                room = "Suite",
                symbol = "▣",
                accent = ClimatePurple,
                temperature = suiteTemperature,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompactAmbientValue(
    room: String,
    symbol: String,
    accent: Color,
    temperature: Float?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    color = accent.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                color = accent,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = room,
                color = ClimateTextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = temperature?.let { String.format("%.1f°", it) } ?: "---",
                color = ClimateText,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun ClimateModeSelector(
    selectedMode: ClimateMode?,
    enabled: Boolean,
    onModeSelected: (ClimateMode) -> Unit
) {
    Column {
        SectionTitle(
            title = "Modo de funcionamiento",
            subtitle = "Un único modo para toda la vivienda"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClimateMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                val accent by animateColorAsState(
                    targetValue = if (selected) mode.accent else ClimateTextSecondary,
                    label = "modeColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (selected) {
                                mode.accent.copy(alpha = 0.12f)
                            } else {
                                ClimateCard
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) mode.accent else ClimateBorder,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable(enabled = enabled) {
                            onModeSelected(mode)
                        }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mode.symbol,
                        color = accent,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = mode.label,
                        color = if (selected) ClimateText else ClimateTextSecondary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun FanSpeedSelector(
    selectedSpeed: FanSpeed?,
    enabled: Boolean,
    onSpeedSelected: (FanSpeed) -> Unit
) {
    Column {
        SectionTitle(
            title = "Velocidad del ventilador",
            subtitle = "Ajuste central del equipo"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FanSpeed.entries.forEach { speed ->
                val selected = speed == selectedSpeed

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .background(
                            color = if (selected) {
                                ClimateGreen.copy(alpha = 0.13f)
                            } else {
                                ClimateCard
                            },
                            shape = RoundedCornerShape(15.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) ClimateGreen else ClimateBorder,
                            shape = RoundedCornerShape(15.dp)
                        )
                        .clickable(enabled = enabled) {
                            onSpeedSelected(speed)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = speed.label,
                        color = if (selected) ClimateGreen else ClimateTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
internal fun ClimateInformationCard(
    exteriorTemperature: String,
    exteriorCondition: String,
    humidity: Int?,
    co2Ppm: Int?
) {
    val co2Quality = when {
        co2Ppm == null -> null
        co2Ppm < 800 -> "Buena"
        co2Ppm < 1200 -> "Mejorable"
        else -> "Alta"
    }

    val co2Accent = when {
        co2Ppm == null -> ClimateMuted
        co2Ppm < 800 -> ClimateGreen
        co2Ppm < 1200 -> ClimateYellow
        else -> ClimateRed
    }

    PremiumCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatusValue(
                title = "Exterior",
                value = exteriorTemperature.ifBlank { "---" },
                secondaryValue = exteriorCondition.takeIf { it.isNotBlank() },
                iconType = ClimateIconType.THERMOSTAT,
                accent = ClimateBlue,
                modifier = Modifier.weight(1f)
            )
            StatusDivider()
            StatusValue(
                title = "Humedad",
                value = humidity?.let { "$it %" } ?: "---",
                iconType = ClimateIconType.HUMIDITY,
                accent = ClimateCyan,
                modifier = Modifier.weight(1f)
            )
            StatusDivider()
            StatusValue(
                title = "CO₂",
                value = co2Ppm?.let { "$it ppm" } ?: "---",
                secondaryValue = co2Quality,
                iconType = ClimateIconType.AIR_QUALITY,
                accent = co2Accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(78.dp)
            .background(ClimateBorder)
    )
}

@Composable
private fun StatusValue(
    title: String,
    value: String,
    iconType: ClimateIconType,
    accent: Color,
    modifier: Modifier = Modifier,
    secondaryValue: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ClimateVectorIcon(
            type = iconType,
            color = accent,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            color = ClimateTextSecondary,
            fontSize = 9.sp
        )
        Text(
            text = value,
            color = ClimateText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        if (secondaryValue != null) {
            Text(
                text = secondaryValue,
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun ProgrammingCard(
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        ClimateCardSecondary,
                        ClimateCard,
                        ClimateCardSecondary
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .border(
                width = 1.dp,
                color = ClimateBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = ClimateBlue.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "◷",
                    color = ClimateBlue,
                    fontSize = 23.sp
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Programación",
                    color = ClimateText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Horarios del sistema central",
                    color = ClimateTextSecondary,
                    fontSize = 12.sp
                )
            }

            Text(
                text = "›",
                color = ClimateBlue,
                fontSize = 28.sp
            )
        }
    }
}

@Composable
internal fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            color = ClimateText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = ClimateTextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun PremiumCard(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        ClimateCardSecondary,
                        ClimateCard
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                color = ClimateBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        content()
    }
}
