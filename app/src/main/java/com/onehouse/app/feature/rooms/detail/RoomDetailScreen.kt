package com.onehouse.app.feature.rooms.detail

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onehouse.app.R
import com.onehouse.app.feature.h08.H08Safety
import com.onehouse.app.feature.h08.H082LightControlCoordinator
import com.onehouse.knxlab.knx.H07LightControlRunner
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.device.ImportedKnxDevice
import com.onehouse.app.feature.home.state.HomeStateMapper
import com.onehouse.app.knx.KnxAddressBook
import com.onehouse.app.knx.KnxCommand
import com.onehouse.app.knx.KnxCommandExecutor
import com.onehouse.app.knx.KnxCommandType
import com.onehouse.app.knx.KnxGroupAddress
import com.onehouse.app.knx.KnxHomeStateRepository

@Composable
fun RoomDetailScreen(roomType: RoomType, onBack: () -> Unit) {
    val context = LocalContext.current
    val commandExecutor = remember { KnxCommandExecutor(context) }
    val homeRepository = remember { KnxHomeStateRepository(context) }
    val homeSnapshot by homeRepository.stateFlow.collectAsStateWithLifecycle(initialValue = homeRepository.snapshot())
    val roomState = remember(homeSnapshot, roomType) { HomeStateMapper.room(homeSnapshot, roomType) }
    val lightControlStatus by H082LightControlCoordinator.state.collectAsStateWithLifecycle()
    val hReadStatus by com.onehouse.app.feature.h08.H08Coordinator.status.collectAsStateWithLifecycle()
    var confirmPilotLight by remember { mutableStateOf<Pair<H07LightControlRunner.Light, Boolean>?>(null) }

    var contentVisible by remember(roomType) { mutableStateOf(false) }
    var lastBlindCommand by remember(roomType) { mutableStateOf(BlindCommand.STOP) }
    val pendingLights = remember(roomType) { mutableStateMapOf<String, Boolean>() }

    // Conserva la predicción local hasta que el estado real del mismo dispositivo
    // confirme el valor solicitado. Los telegramas de otros objetos ya no hacen
    // volver el botón prematuramente a su estado anterior.
    LaunchedEffect(homeSnapshot.states, roomState.lights) {
        pendingLights.keys.toList().forEach { deviceId ->
            val confirmed = roomState.lights.firstOrNull { it.device.id == deviceId }
            val requested = pendingLights[deviceId]
            if (confirmed != null && requested != null && confirmed.isOn == requested) {
                pendingLights.remove(deviceId)
            }
        }
    }
    LaunchedEffect(roomType) { contentVisible = true }

    DisposableEffect(commandExecutor) {
        onDispose { commandExecutor.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior, Color.Black)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        RoomHeader(title = HomeStateMapper.roomName(roomType), onBack = onBack)
        Spacer(Modifier.height(16.dp))

        RoomHeroCard(
            type = roomType,
            lightOn = roomState.lights.any { light ->
                pendingLights[light.device.id] ?: light.isOn
            },
            imageRes = roomImage(roomType)
        )

        Spacer(Modifier.height(14.dp))

        RoomControlAnimated(contentVisible) {
            Column {
                if (roomType == RoomType.DINING_ROOM || roomType == RoomType.SUITE) {
                    RoomSensorValueCard(
                        title = "Temperatura interior",
                        value = roomState.temperatureCelsius?.let { "%.1f".format(it) },
                        unit = "°C",
                        symbol = "°",
                        accent = if (roomType == RoomType.DINING_ROOM) Color(0xFF168EFF) else Color(0xFF8E62FF)
                    )
                    Spacer(Modifier.height(12.dp))
                } else {
                    roomState.climate?.let { climate ->
                        RoomClimateCard(
                            powered = climate.powered,
                            currentTemperature = climate.currentTemperature,
                            targetTemperature = climate.targetTemperature,
                            mode = climate.mode,
                            fanSpeed = climate.fanSpeed
                        )
                        Spacer(Modifier.height(12.dp))
                    } ?: roomState.temperatureCelsius?.let { temperature ->
                        RoomTemperatureCard(temperature = temperature)
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (roomType == RoomType.KITCHEN && H08Safety.READ_ONLY) {
                    Text(
                        "H09.1 TEST · Luz y Fluorescente de Cocina: eventos pasivos desde GA de estado; " +
                            "los mandos requieren lectura H limpia y monitor detenido.",
                        color = Color(0xFFFFC778)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(lightControlStatus.message, color = Color(0xFFC9E2F5))
                    Spacer(Modifier.height(12.dp))
                }
                val expectedLights = expectedLightControls(roomType)
                val matchedDeviceIds = mutableSetOf<String>()
                // roomState ya contiene exclusivamente controles pertenecientes
                // a OneHouse; la depuración se realiza antes de crear dispositivos.
                val visibleLights = roomState.lights

                expectedLights.forEachIndexed { index, spec ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    val realLight = visibleLights.firstOrNull { light ->
                        light.device.id !in matchedDeviceIds && spec.matches(light.device.name)
                    }
                    realLight?.device?.id?.let(matchedDeviceIds::add)

                    val stateKey = realLight?.device?.id ?: "visual:${roomType.name}:${spec.title}"
                    val pilotLight = if (roomType == RoomType.KITCHEN) when (spec.title) {
                        "Luz" -> H07LightControlRunner.Light.KITCHEN
                        "Fluorescente" -> H07LightControlRunner.Light.FLUORESCENT
                        else -> null
                    } else null
                    val observedPilot = pilotLight?.let { homeSnapshot.booleanAt(it.statusGa) }
                    val pilotReady = pilotLight != null && observedPilot != null &&
                        H082LightControlCoordinator.canControl(context, pilotLight, realLight?.device)
                    val enabled = if (H08Safety.READ_ONLY) {
                        observedPilot ?: realLight?.isOn ?: false
                    } else pendingLights[stateKey] ?: realLight?.isOn ?: false
                    RoomLightCard(
                        title = spec.title,
                        symbol = spec.symbol,
                        enabled = enabled,
                        interactive = !H08Safety.READ_ONLY || pilotReady,
                        onEnabledChange = { requested ->
                            if (H08Safety.READ_ONLY) {
                                if (pilotReady && pilotLight != null && observedPilot != requested) {
                                    confirmPilotLight = pilotLight to requested
                                }
                            } else {
                                pendingLights[stateKey] = requested
                                realLight?.device?.let { device ->
                                    executeBoolean(commandExecutor, device, requested) { success ->
                                        if (!success) pendingLights.remove(stateKey)
                                    }
                                }
                            }
                        }
                    )
                }

                // Cualquier objeto KNX adicional de la estancia sigue mostrándose y conserva su mando.
                visibleLights
                    .filterNot { it.device.id in matchedDeviceIds }
                    .forEachIndexed { index, light ->
                        if (expectedLights.isNotEmpty() || index > 0) Spacer(Modifier.height(12.dp))
                        val enabled = pendingLights[light.device.id] ?: light.isOn
                        RoomLightCard(
                            title = light.device.name,
                            enabled = enabled,
                            interactive = !H08Safety.READ_ONLY,
                            onEnabledChange = { requested ->
                                pendingLights[light.device.id] = requested
                                executeBoolean(commandExecutor, light.device, requested) { success ->
                                    if (!success) pendingLights.remove(light.device.id)
                                }
                            }
                        )
                    }

                if (roomHasBlind(roomType)) {
                    Spacer(Modifier.height(12.dp))
                    val blind = roomState.blind
                    RoomBlindCard(
                        lastCommand = lastBlindCommand,
                        positionPercent = blind?.positionPercent,
                        onCommand = { command ->
                            if (H08Safety.READ_ONLY) return@RoomBlindCard
                            lastBlindCommand = command
                            val type = when (command) {
                                BlindCommand.UP -> KnxCommandType.UP
                                BlindCommand.STOP -> KnxCommandType.STOP
                                BlindCommand.DOWN -> KnxCommandType.DOWN
                            }
                            blind?.device?.commands?.firstOrNull { it.type == type }?.let { knxCommand ->
                                commandExecutor.execute(knxCommand) { }
                            }
                        }
                    )
                }

                if (roomType == RoomType.DINING_ROOM) {
                    Spacer(Modifier.height(12.dp))
                    RoomSensorValueCard(
                        title = "CO₂",
                        value = roomState.co2Ppm?.let { "%.0f".format(it) },
                        unit = "ppm",
                        symbol = "CO₂"
                    )
                    Spacer(Modifier.height(12.dp))
                    RoomSensorValueCard(
                        title = "Humedad relativa",
                        value = roomState.humidityPercent?.let { "%.0f".format(it) },
                        unit = "%",
                        symbol = "≋"
                    )
                }

                when (roomType) {
                    RoomType.KITCHEN,
                    RoomType.BATHROOM -> {
                        Spacer(Modifier.height(12.dp))
                        RoomFloodSensorCard(floodDetected = roomState.floodDetected)
                    }
                    RoomType.ENTRANCE -> {
                        Spacer(Modifier.height(12.dp))
                        RoomPirCard(
                            blocked = roomState.pirBlocked,
                            onBlockedChange = { requested ->
                                val command = KnxCommand(
                                    type = if (requested) KnxCommandType.ON else KnxCommandType.OFF,
                                    destination = KnxGroupAddress.parse(KnxAddressBook.Indoor.PIR_BLOCK_ENTRANCE),
                                    dpt = "1.001"
                                )
                                commandExecutor.execute(
                                    command = command,
                                    verificationAddress = KnxAddressBook.Indoor.PIR_BLOCK_ENTRANCE
                                ) { }
                            }
                        )
                    }
                    RoomType.HALLWAY -> {
                        Spacer(Modifier.height(12.dp))
                        RoomFireSensorCard(fireDetected = roomState.fireDetected)
                    }
                    else -> Unit
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }

    val pending = confirmPilotLight
    if (pending != null) {
        val (light, wantedOn) = pending
        AlertDialog(
            onDismissRequest = { confirmPilotLight = null },
            title = { Text("OneHouse H08.2 TEST · ${light.label}") },
            text = { Text(
                "¿Confirmas ${if (wantedOn) "ENCENDER" else "APAGAR"} ${light.label}? " +
                    "Se enviará UNA orden a ${light.commandGa} con la lectura H " +
                    "${hReadStatus.lastRunId}. El ACK no demuestra actuación física. " +
                    "No se reintentará automáticamente. Después pulsa Leer H."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    val device = roomState.lights.firstOrNull { state ->
                        H082LightControlCoordinator.isDeviceWhitelisted(state.device, light)
                    }?.device
                    H082LightControlCoordinator.execute(context, light, device, wantedOn)
                    confirmPilotLight = null
                }) { Text("Enviar una orden") }
            },
            dismissButton = { TextButton(onClick = { confirmPilotLight = null }) { Text("Cancelar") } }
        )
    }
}

private fun executeBoolean(
    executor: KnxCommandExecutor,
    device: ImportedKnxDevice,
    enabled: Boolean,
    onFinished: (Boolean) -> Unit
) {
    val type = if (enabled) KnxCommandType.ON else KnxCommandType.OFF
    val command = device.commands.firstOrNull { it.type == type }
    if (command == null) {
        onFinished(false)
        return
    }
    executor.execute(command) { result ->
        onFinished(result is KnxCommandExecutor.Result.Success)
    }
}

@DrawableRes
private fun roomImage(roomType: RoomType): Int? = when (roomType) {
    RoomType.BATHROOM -> R.drawable.room_bathroom
    RoomType.KITCHEN -> R.drawable.room_kitchen
    RoomType.BEDROOM_1 -> R.drawable.room_bedroom_1
    RoomType.DINING_ROOM -> R.drawable.room_dining
    RoomType.SUITE -> R.drawable.room_suite
    RoomType.ENTRANCE -> R.drawable.room_entrance
    RoomType.HALLWAY -> R.drawable.room_hallway
    RoomType.STORAGE -> R.drawable.room_storage
}


private data class ExpectedLightControl(
    val title: String,
    val aliases: List<String>,
    val symbol: String = "☼"
) {
    fun matches(deviceName: String): Boolean {
        val normalizedName = normalizeControlName(deviceName)
        return aliases.any { alias ->
            val normalizedAlias = normalizeControlName(alias)
            normalizedName.contains(normalizedAlias) || normalizedAlias.contains(normalizedName)
        }
    }
}

private fun expectedLightControls(roomType: RoomType): List<ExpectedLightControl> = when (roomType) {
    RoomType.ENTRANCE -> listOf(
        ExpectedLightControl("Luz", listOf("luz entrada", "entrada", "luz"))
    )
    RoomType.HALLWAY -> listOf(
        ExpectedLightControl("Luz", listOf("luz pasillo", "pasillo", "luz"))
    )
    RoomType.STORAGE -> listOf(
        ExpectedLightControl("Luz", listOf("luz trastero", "trastero", "luz"))
    )
    RoomType.BATHROOM -> listOf(
        ExpectedLightControl("Luz", listOf("luz baño", "luz bano", "baño", "bano", "luz"))
    )
    RoomType.KITCHEN -> listOf(
        ExpectedLightControl("Luz", listOf("luz cocina", "cocina")),
        ExpectedLightControl("Fluorescente", listOf("fluorescente", "luz fluorescente"))
    )
    RoomType.BEDROOM_1 -> listOf(
        ExpectedLightControl("Luz", listOf("luz habitacion 1", "luz dormitorio 1", "luz principal", "techo")),
        ExpectedLightControl("Mesita", listOf("mesita", "luz mesita", "cabecero"))
    )
    RoomType.DINING_ROOM -> listOf(
        ExpectedLightControl("Luz salón", listOf("luz salon", "salon")),
        ExpectedLightControl("Luz comedor", listOf("luz comedor", "comedor")),
        ExpectedLightControl("Luz lámpara", listOf("lampara", "luz lampara"), "♧")
    )
    RoomType.SUITE -> listOf(
        ExpectedLightControl("Luz", listOf("luz suite", "luz principal", "techo")),
        ExpectedLightControl("Mesita 1", listOf("mesita 1", "mesita izquierda", "cabecero 1"), "♧"),
        ExpectedLightControl("Mesita 2", listOf("mesita 2", "mesita derecha", "cabecero 2"), "♧")
    )
}

private fun roomHasBlind(roomType: RoomType): Boolean = roomType in setOf(
    RoomType.KITCHEN,
    RoomType.BEDROOM_1,
    RoomType.DINING_ROOM,
    RoomType.SUITE
)

private fun normalizeControlName(value: String): String = value
    .lowercase()
    .replace("á", "a")
    .replace("é", "e")
    .replace("í", "i")
    .replace("ó", "o")
    .replace("ú", "u")
    .replace("ñ", "n")
    .trim()
