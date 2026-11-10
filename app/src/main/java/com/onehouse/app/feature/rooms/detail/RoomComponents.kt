package com.onehouse.app.feature.rooms.detail

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Blinds
import androidx.compose.material.icons.rounded.Dining
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario

private val RoomBlue = Color(0xFF168EFF)
private val RoomGreen = Color(0xFF21D991)
private val RoomOrange = Color(0xFFFF8A24)
private val RoomRed = Color(0xFFFF4D45)
private val RoomCardTop = Color(0xFF0D2130)
private val RoomCardBottom = Color(0xFF091722)

@Composable
internal fun RoomHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Volver",
                tint = TextoPrincipal
            )
        }
        Text(
            text = title,
            color = TextoPrincipal,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun RoomHeroCard(
    type: RoomType,
    lightOn: Boolean,
    @DrawableRes imageRes: Int? = null
) {
    val brightness by animateFloatAsState(
        targetValue = if (lightOn) 1f else 0.76f,
        label = "roomHeroBrightness"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (imageRes == null) 190.dp else 238.dp)
            .alpha(brightness)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(26.dp))
    ) {
        if (imageRes != null) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f))
                        )
                    )
            )
        } else {
            val title = when (type) {
                RoomType.ENTRANCE -> "Bienvenida a casa"
                RoomType.HALLWAY -> "Zona de paso"
                RoomType.STORAGE -> "Espacio de almacenaje"
                RoomType.BATHROOM -> "Baño"
                RoomType.KITCHEN -> "Cocina"
                RoomType.BEDROOM_1 -> "Habitación 1"
                RoomType.DINING_ROOM -> "Comedor"
                RoomType.SUITE -> "Suite"
            }
            val subtitle = when (type) {
                RoomType.ENTRANCE -> "Entrada principal"
                RoomType.HALLWAY -> "Pasillo interior"
                RoomType.STORAGE -> "Trastero"
                RoomType.BATHROOM -> "Zona de baño"
                RoomType.KITCHEN -> "Zona de cocina"
                RoomType.BEDROOM_1 -> "Zona de descanso"
                RoomType.DINING_ROOM -> "Zona social"
                RoomType.SUITE -> "Descanso principal"
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                RoomBlue.copy(alpha = if (lightOn) 0.30f else 0.12f),
                                RoomCardTop,
                                Color(0xFF06111B)
                            )
                        )
                    )
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    RoomIconBox(icon = roomIcon(type), accent = RoomBlue, size = 62)
                    Column {
                        Text(title, color = TextoPrincipal, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = TextoSecundario, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (lightOn) "Iluminación encendida" else "Iluminación apagada",
                            color = if (lightOn) RoomBlue else TextoSecundario,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RoomLightCard(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    symbol: String = "☼",
    interactive: Boolean = true
) {
    val accent by animateColorAsState(
        targetValue = if (enabled) RoomBlue else TextoSecundario,
        label = "lightAccent"
    )
    RoomPremiumCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoomIconBox(icon = roomLightIcon(title), accent = accent)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled) "Encendida" else "Apagada",
                    color = accent,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = interactive,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = RoomBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF183041),
                    uncheckedBorderColor = BordeTarjeta
                )
            )
        }
    }
}

@Composable
internal fun RoomTemperatureCard(temperature: Float) {
    RoomPremiumCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoomIconBox(icon = Icons.Rounded.Thermostat, accent = RoomBlue)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Temperatura interior", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Actual", color = TextoSecundario, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format("%.1f °C", temperature),
                    color = RoomOrange,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("⌂  Confort", color = RoomBlue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun RoomBlindCard(
    lastCommand: BlindCommand,
    positionPercent: Float? = null,
    onCommand: (BlindCommand) -> Unit
) {
    RoomPremiumCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoomIconBox(icon = Icons.Rounded.Blinds, accent = RoomBlue)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Persiana", color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    positionPercent?.let { "Posición real ${it.toInt()} %" } ?: when (lastCommand) {
                        BlindCommand.UP -> "Subiendo"
                        BlindCommand.STOP -> "Detenida"
                        BlindCommand.DOWN -> "Bajando"
                    },
                    color = TextoSecundario,
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlindButton("↑", lastCommand == BlindCommand.UP) { onCommand(BlindCommand.UP) }
                BlindButton("Ⅱ", lastCommand == BlindCommand.STOP) { onCommand(BlindCommand.STOP) }
                BlindButton("↓", lastCommand == BlindCommand.DOWN) { onCommand(BlindCommand.DOWN) }
            }
        }
    }
}

@Composable
private fun BlindButton(symbol: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(
                if (selected) RoomBlue.copy(alpha = 0.22f) else RoomBlue.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                if (selected) RoomBlue.copy(alpha = 0.70f) else Color.Transparent,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = RoomBlue, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun RoomSensorValueCard(
    title: String,
    value: String?,
    unit: String,
    symbol: String,
    accent: Color = RoomBlue,
    waitingLabel: String = "Esperando datos KNX"
) {
    RoomPremiumCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoomIconBox(symbol = symbol, accent = accent)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (value == null) {
                    Text(waitingLabel, color = TextoSecundario, fontSize = 11.sp)
                }
            }
            Text(
                value?.let { "$it $unit".trim() } ?: "--",
                color = if (value == null) TextoSecundario else accent,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun RoomFloodSensorCard(floodDetected: Boolean?) {
    val accent = when (floodDetected) {
        true -> RoomRed
        false -> RoomGreen
        null -> TextoSecundario
    }
    RoomPremiumCard {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoomIconBox(icon = Icons.Rounded.WaterDrop, accent = accent)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sensor de inundación", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    when (floodDetected) {
                        true -> "⚠ Alarma"
                        false -> "✓ Normal"
                        null -> "--"
                    },
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.07f), RoundedCornerShape(15.dp))
                    .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(15.dp))
                    .padding(14.dp)
            ) {
                Text(
                    when (floodDetected) {
                        true -> "Se ha detectado agua. Revisa la estancia inmediatamente."
                        false -> "No se ha detectado ninguna fuga de agua."
                        null -> "Esperando el estado del sensor desde el bus KNX."
                    },
                    color = if (floodDetected == true) RoomRed else TextoSecundario,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
internal fun RoomFireSensorCard(fireDetected: Boolean?) {
    val accent = when (fireDetected) {
        true -> RoomRed
        false -> RoomGreen
        null -> TextoSecundario
    }
    RoomPremiumCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoomIconBox(icon = Icons.Rounded.LocalFireDepartment, accent = accent)
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Sensor de incendio", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                when (fireDetected) {
                    true -> "⚠ Alarma"
                    false -> "✓ Normal"
                    null -> "--"
                },
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun RoomPirCard(blocked: Boolean?, onBlockedChange: (Boolean) -> Unit) {
    val accent = when (blocked) {
        true -> RoomOrange
        false -> RoomGreen
        null -> TextoSecundario
    }
    RoomPremiumCard {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoomIconBox(symbol = "◖", accent = accent)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sensor de movimiento PIR", color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (blocked) {
                            true -> "Bloqueado"
                            false -> "Funcionamiento automático"
                            null -> "Esperando datos KNX"
                        },
                        color = accent,
                        fontSize = 11.sp
                    )
                }
                if (blocked != null) {
                    Box(
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(13.dp))
                            .border(1.dp, accent, RoundedCornerShape(13.dp))
                            .clickable { onBlockedChange(!blocked) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            if (blocked) "Desbloquear" else "Bloquear",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF081824), RoundedCornerShape(15.dp))
                    .border(1.dp, BordeTarjeta.copy(alpha = 0.75f), RoundedCornerShape(15.dp))
                    .padding(13.dp)
            ) {
                Text(
                    when (blocked) {
                        true -> "El encendido automático por presencia está desactivado."
                        false -> "La luz puede activarse automáticamente cuando el sensor detecta movimiento."
                        null -> "Esperando el estado del PIR desde el bus KNX."
                    },
                    color = TextoSecundario,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
internal fun RoomControlAnimated(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 })
    ) { content() }
}

@Composable
private fun RoomPremiumCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(RoomCardTop, RoomCardBottom)),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, BordeTarjeta, RoundedCornerShape(22.dp))
            .padding(17.dp)
    ) {
        content()
    }
}

@Composable
private fun RoomIconBox(icon: ImageVector, accent: Color, size: Int = 48) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                accent.copy(alpha = 0.13f),
                RoundedCornerShape(if (size > 50) 20.dp else 15.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(if (size > 50) 34.dp else 28.dp)
        )
    }
}

@Composable
private fun RoomIconBox(symbol: String, accent: Color, size: Int = 48) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                accent.copy(alpha = 0.13f),
                RoundedCornerShape(if (size > 50) 20.dp else 15.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = accent,
            fontSize = if (size > 50) 30.sp else 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

private fun roomIcon(type: RoomType): ImageVector = when (type) {
    RoomType.ENTRANCE -> Icons.Rounded.DoorFront
    RoomType.HALLWAY -> Icons.Rounded.MeetingRoom
    RoomType.STORAGE -> Icons.Rounded.Inventory2
    RoomType.BATHROOM -> Icons.Rounded.Bathtub
    RoomType.KITCHEN -> Icons.Rounded.Kitchen
    RoomType.BEDROOM_1 -> Icons.Rounded.Bed
    RoomType.DINING_ROOM -> Icons.Rounded.Dining
    RoomType.SUITE -> Icons.Rounded.Bed
}

private fun roomLightIcon(title: String): ImageVector {
    val normalized = title
        .lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")

    return when {
        normalized.contains("mesita") || normalized.contains("cabecero") -> Icons.Rounded.Bed
        normalized.contains("fluorescente") -> Icons.Rounded.WbSunny
        normalized.contains("lampara") -> Icons.Rounded.Lightbulb
        else -> Icons.Rounded.Lightbulb
    }
}

@Composable
internal fun RoomClimateCard(
    powered: Boolean?,
    currentTemperature: Float?,
    targetTemperature: Float?,
    mode: String?,
    fanSpeed: String?
) {
    RoomPremiumCard {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoomIconBox(icon = Icons.Rounded.AcUnit, accent = RoomBlue)
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Climatización", color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (powered) {
                            true -> "Encendida"
                            false -> "Apagada"
                            null -> "Esperando estado KNX"
                        },
                        color = if (powered == true) RoomGreen else TextoSecundario,
                        fontSize = 12.sp
                    )
                }
                Text(
                    currentTemperature?.let { String.format("%.1f °C", it) } ?: "---",
                    color = RoomOrange,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ClimateValue("Consigna", targetTemperature?.let { String.format("%.1f °C", it) } ?: "---")
                ClimateValue("Modo", mode ?: "---")
                ClimateValue("Ventilador", fanSpeed ?: "---")
            }
        }
    }
}

@Composable
private fun ClimateValue(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = TextoSecundario, fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = TextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
