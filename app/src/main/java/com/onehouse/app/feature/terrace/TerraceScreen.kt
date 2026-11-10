package com.onehouse.app.feature.terrace

import com.onehouse.app.knx.KnxAddressBook
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.R
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.device.ControlKind
import com.onehouse.app.feature.rooms.detail.RoomHeader
import com.onehouse.app.feature.weather.WeatherUiState
import com.onehouse.app.feature.weather.rememberWeatherState
import com.onehouse.app.knx.KnxCommandExecutor
import com.onehouse.app.knx.KnxCommandType
import com.onehouse.app.knx.KnxHomeStateRepository

private val TerraceBlue = Color(0xFF168EFF)
private val TerraceRed = Color(0xFFFF4D45)
private val TerraceCard = Color(0xE60A1926)

@Composable
fun TerraceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        KnxHomeStateRepository(context.applicationContext)
    }
    val executor = remember(context.applicationContext) {
        KnxCommandExecutor(context.applicationContext)
    }
    val snapshot by repository.stateFlow.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val weather = rememberWeatherState()

    val terraceDevices = remember(snapshot.devices) { snapshot.devicesForRoom("Terraza") }
    val lightDevice = remember(terraceDevices) {
        terraceDevices.firstOrNull { it.controlKind == ControlKind.BOOLEAN_SWITCH }
    }
    val realLightOn = lightDevice?.let(snapshot::booleanValue)
    var pendingLight by remember(lightDevice?.id) { mutableStateOf<Boolean?>(null) }
    val lightOn = pendingLight ?: realLightOn

    LaunchedEffect(realLightOn, pendingLight) {
        if (pendingLight != null && realLightOn == pendingLight) {
            pendingLight = null
        }
    }

    DisposableEffect(executor) {
        onDispose { executor.close() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior, Color.Black)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            RoomHeader(title = "Terraza", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            TerraceHeroImage()
            Spacer(Modifier.height(16.dp))
            WeatherCard(weather)
            Spacer(Modifier.height(16.dp))

            KnxExteriorSensorsCard(
                luminosity = snapshot.numericAt(KnxAddressBook.Terrace.LUMINOSITY, "9.004"),
                windSpeed = snapshot.numericAt(KnxAddressBook.Terrace.WIND_SPEED, "9.005"),
                excessiveWind = snapshot.booleanAt(KnxAddressBook.Terrace.EXCESSIVE_WIND),
                raining = snapshot.booleanAt(KnxAddressBook.Terrace.RAINING)
            )

            Spacer(Modifier.height(16.dp))
            ExteriorLightCard(
                lightOn = lightOn,
                available = lightDevice != null,
                onChange = { requested ->
                    val device = lightDevice
                    if (device != null) {
                        pendingLight = requested
                        val type = if (requested) KnxCommandType.ON else KnxCommandType.OFF
                        device.commands.firstOrNull { it.type == type }?.let { command ->
                            executor.execute(command) { result ->
                                if (result is KnxCommandExecutor.Result.Failure) pendingLight = null
                            }
                        } ?: run { pendingLight = null }
                    }
                }
            )
            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun KnxExteriorSensorsCard(
    luminosity: Float?,
    windSpeed: Float?,
    excessiveWind: Boolean?,
    raining: Boolean?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(TerraceCard)
            .border(1.dp, BordeTarjeta, RoundedCornerShape(26.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Sensores de Terraza", color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (listOf(luminosity, windSpeed, excessiveWind, raining).all { it == null }) {
                "Esperando datos reales del bus KNX"
            } else {
                "Valores recibidos desde la instalación"
            },
            color = TextoSecundario,
            fontSize = 12.sp
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KnxMetric(
                modifier = Modifier.weight(1f),
                symbol = "☀",
                label = "Luminosidad",
                value = luminosity?.let { "%.0f lux".format(it) } ?: "--"
            )
            KnxMetric(
                modifier = Modifier.weight(1f),
                symbol = "≋",
                label = "Viento",
                value = windSpeed?.let { "%.1f".format(it) } ?: "--"
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            KnxMetric(
                modifier = Modifier.weight(1f),
                symbol = "⚠",
                label = "Exceso viento",
                value = when (excessiveWind) {
                    true -> "ALARMA"
                    false -> "Normal"
                    null -> "--"
                },
                alarm = excessiveWind == true
            )
            KnxMetric(
                modifier = Modifier.weight(1f),
                symbol = when (raining) {
                    true -> "☂"
                    false -> "☀"
                    null -> "☀"
                },
                label = "Lluvia",
                value = when (raining) {
                    true -> "Lluvia"
                    false -> "Sin lluvia"
                    null -> "Sin lluvia"
                },
                alarm = raining == true
            )
        }
    }
}

@Composable
private fun KnxMetric(
    modifier: Modifier = Modifier,
    symbol: String,
    label: String,
    value: String,
    alarm: Boolean = false
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, color = if (alarm) TerraceRed else TextoPrincipal, fontSize = 24.sp)
        Text(label, color = TextoSecundario, fontSize = 11.sp)
        Text(
            value,
            color = if (alarm) TerraceRed else TextoPrincipal,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TerraceHeroImage() {
    Image(
        painter = painterResource(R.drawable.room_terrace),
        contentDescription = "Terraza exterior",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(26.dp))
    )
}

@Composable
private fun WeatherCard(weather: WeatherUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(TerraceCard)
            .border(1.dp, BordeTarjeta, RoundedCornerShape(26.dp))
            .padding(22.dp)
    ) {
        Text("Clima exterior", color = TextoPrincipal, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Actualizado ${weather.updatedAt}", color = TextoSecundario, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(weather.temperature, color = TextoPrincipal, fontSize = 58.sp, fontWeight = FontWeight.Light)
                Text(weather.condition, color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(weather.location, color = TextoSecundario, fontSize = 14.sp)
            }
            Text(weather.conditionSymbol, color = Color(0xFFFFC329), fontSize = 82.sp)
        }
    }
}

@Composable
private fun ExteriorLightCard(
    lightOn: Boolean?,
    available: Boolean,
    onChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TerraceCard)
            .border(1.dp, BordeTarjeta, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Text("Luz exterior", color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0x1A55C865), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) { Text("♧", color = Color(0xFFFFC04A), fontSize = 34.sp) }
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Luz exterior", color = TextoPrincipal, fontSize = 17.sp)
                Text(
                    when {
                        !available -> "No encontrada en el proyecto"
                        lightOn == true -> "Encendida"
                        lightOn == false -> "Apagada"
                        else -> "Apagada"
                    },
                    color = if (lightOn == true) TerraceBlue else TextoSecundario,
                    fontSize = 14.sp
                )
            }
            Switch(
                checked = lightOn == true,
                onCheckedChange = onChange,
                enabled = available,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TerraceBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFF183041)
                )
            )
        }
    }
}
