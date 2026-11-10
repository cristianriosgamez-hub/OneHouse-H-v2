package com.onehouse.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Blinds
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.design.AmarilloEstado
import com.onehouse.app.design.AzulClaro
import com.onehouse.app.design.AzulOneHouse
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.FondoChip
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoMedio
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.OneHouseCard
import com.onehouse.app.design.OneHouseSceneCard
import com.onehouse.app.design.OneHouseSectionTitle
import com.onehouse.app.design.OneHouseStatusItem
import com.onehouse.app.design.TextoDesactivado
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.design.VerdeEstado
import com.onehouse.app.design.RojoEstado
import com.onehouse.app.feature.rooms.RoomItem
import com.onehouse.app.data.knx.KnxConnectionStatus
import com.onehouse.app.data.knx.KnxSettingsRepository
import com.onehouse.app.data.knx.SettingsDataStore
import com.onehouse.app.feature.weather.WeatherUiState
import com.onehouse.app.feature.weather.rememberWeatherState
import com.onehouse.app.feature.home.state.HomeDashboardUiState
import com.onehouse.app.feature.home.state.HomeStateMapper
import com.onehouse.app.feature.settings.SettingsViewModel
import com.onehouse.app.feature.h08.H08Coordinator
import com.onehouse.app.feature.h08.H08Safety
import com.onehouse.app.knx.KnxHomeSnapshot
import com.onehouse.app.knx.NetworkConnectionDetector
import com.onehouse.app.knx.KnxCentralEngine
import com.onehouse.app.knx.KnxHomeStateRepository
import com.onehouse.app.knx.KnxLoadKind
import com.onehouse.app.knx.KnxLoadProgressRepository
import com.onehouse.app.knx.KnxLoadProgressSnapshot
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    favoriteRooms: List<RoomItem>,
    onFavoriteSelected: (String) -> Unit
) {
    val exteriorWeather = rememberWeatherState()
    val context = LocalContext.current
    val homeRepository = remember { KnxHomeStateRepository(context) }
    val homeState by homeRepository.stateFlow.collectAsStateWithLifecycle(initialValue = homeRepository.snapshot())
    val dashboardState = remember(homeState) { HomeStateMapper.dashboard(homeState) }
    val knxLoadProgress by KnxLoadProgressRepository.progress.collectAsStateWithLifecycle()
    var showKnxLoadDetails by remember { mutableStateOf(false) }
    val settingsDataStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val connectionViewModel = remember(context) {
        SettingsViewModel(
            repository = KnxSettingsRepository(settingsDataStore),
            networkDetector = NetworkConnectionDetector(context.applicationContext),
            connectionManager = KnxCentralEngine.get(context.applicationContext).connectionManager
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var connectionStatus by remember { mutableStateOf(KnxConnectionStatus.TESTING) }

    DisposableEffect(connectionViewModel) {
        val observation = connectionViewModel.observe {
            connectionStatus = connectionViewModel.connectionStatus
        }
        onDispose {
            observation.close()
            connectionViewModel.close()
        }
    }

    val h08Status by H08Coordinator.status.collectAsStateWithLifecycle()
    DisposableEffect(lifecycleOwner, connectionViewModel) {
        if (H08Safety.READ_ONLY) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                connectionStatus = KnxConnectionStatus.TESTING
                connectionViewModel.testConnection()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(FondoSuperior, FondoMedio, FondoInferior, Color.Black)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AzulOneHouse.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 34.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OneHouse",
                        color = TextoPrincipal,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when {
                            !knxLoadProgress.finished ->
                                "${homeGreeting()} · Cargando datos KNX"
                            knxLoadProgress.total > 0 &&
                                knxLoadProgress.completed < knxLoadProgress.total ->
                                "${homeGreeting()} · Carga KNX incompleta"
                            else ->
                                "${homeGreeting()} · Tu hogar está listo"
                        },
                        color = TextoSecundario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (H08Safety.READ_ONLY) {
                        Text(
                            text = when {
                                h08Status.running -> "H08 · Lectura en curso"
                                h08Status.success -> "H08 · Última lectura OK (túnel cerrado)"
                                h08Status.lastRun != null -> "H08 · Lectura no validada"
                                else -> "H08 · Pendiente de lectura"
                            },
                            color = TextoSecundario,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        ConnectionStatus(connectionStatus, centered = false)
                    }
                }
                KnxLoadProgressButton(
                    progress = knxLoadProgress,
                    onClick = { showKnxLoadDetails = true }
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            ClimateHeroCard(dashboardState)

            Spacer(modifier = Modifier.height(16.dp))
            QuickStatusGrid(exteriorWeather, dashboardState)

            if (favoriteRooms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(26.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OneHouseSectionTitle(title = "Estancias favoritas", modifier = Modifier.weight(1f))
                    Text(
                        text = "${favoriteRooms.size} guardadas",
                        color = TextoDesactivado,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FavoriteRooms(
                    favoriteRooms = favoriteRooms,
                    raining = homeState.booleanAt(com.onehouse.app.knx.KnxAddressBook.Terrace.RAINING),
                    onFavoriteSelected = onFavoriteSelected
                )
            }
        }
    }

    if (showKnxLoadDetails) {
        KnxLoadProgressDialog(
            progress = knxLoadProgress,
            onDismiss = { showKnxLoadDetails = false }
        )
    }

}

@Composable
private fun KnxLoadProgressButton(
    progress: KnxLoadProgressSnapshot,
    onClick: () -> Unit
) {
    val percentage = progress.percent
    val statusColor = when {
        progress.finished && progress.noResponse == 0 -> VerdeEstado
        progress.finished -> AmarilloEstado
        else -> AzulClaro
    }

    Surface(
        onClick = onClick,
        color = FondoChip.copy(alpha = 0.78f),
        shape = CircleShape,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier.size(58.dp),
                color = statusColor,
                trackColor = BordeTarjeta.copy(alpha = 0.55f),
                strokeWidth = 4.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$percentage%",
                    color = TextoPrincipal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "KNX",
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun KnxLoadProgressDialog(
    progress: KnxLoadProgressSnapshot,
    onDismiss: () -> Unit
) {
    data class ElementKey(
        val room: String,
        val kind: KnxLoadKind,
        val name: String
    )

    data class ElementStatus(
        val key: ElementKey,
        val addresses: Set<String>
    ) {
        val receivedCount: Int
            get() = addresses.count { it in progress.receivedAddresses }
        val waitingEventCount: Int
            get() = addresses.count { it in progress.waitingEventAddresses }
        val noResponseCount: Int
            get() = addresses.count { it in progress.noResponseAddresses }
        val completedCount: Int
            get() = receivedCount
        val pendingCount: Int
            get() = (addresses.size - completedCount - noResponseCount - waitingEventCount).coerceAtLeast(0)
        val fullyReceived: Boolean
            get() = addresses.isNotEmpty() && completedCount == addresses.size
        val hasAnyReceived: Boolean
            get() = completedCount > 0
    }

    val kindRows = remember(progress) {
        KnxLoadKind.entries.mapNotNull { kind ->
            val addresses = progress.targets.values
                .filter { kind in it.kinds }
                .map { it.address }
                .toSet()
            if (addresses.isEmpty()) null else {
                val received = addresses.count {
                    it in progress.receivedAddresses
                }
                val noResponse = addresses.count { it in progress.noResponseAddresses }
                Triple(kind.label, received, Pair(addresses.size, noResponse))
            }
        }
    }

    val elementsByRoom = remember(progress) {
        val grouped = linkedMapOf<ElementKey, MutableSet<String>>()
        progress.targets.values.forEach { target ->
            val rooms = target.rooms.ifEmpty { setOf("Vivienda") }
            val kinds = target.kinds.ifEmpty { setOf(KnxLoadKind.OTHER) }
            val names = target.names.ifEmpty { setOf(target.address) }
            rooms.forEach { room ->
                kinds.forEach { kind ->
                    names.forEach { name ->
                        grouped.getOrPut(ElementKey(room, kind, name)) { linkedSetOf() }
                            .add(target.address)
                    }
                }
            }
        }
        grouped
            .map { (key, addresses) -> ElementStatus(key, addresses.toSet()) }
            .groupBy { it.key.room }
            .toList()
            .sortedBy { it.first.lowercase(Locale.ROOT) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carga KNX") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val statusText = when {
                    progress.finished && progress.total > 0 && progress.completed == progress.total ->
                        "Carga completada · todos los estados de lectura recibidos"
                    progress.finished && progress.received == 0 && progress.total > 0 ->
                        "Carga finalizada sin respuestas KNX"
                    progress.finished && progress.noResponse > 0 ->
                        "Carga finalizada con elementos sin respuesta"
                    progress.finished -> "Carga finalizada"
                    else -> "Cargando estados KNX…"
                }
                Text(statusText, color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append("${progress.received}/${progress.total} estados realmente recibidos")
                        if (progress.waitingEventAddresses.isNotEmpty()) append(" · ${progress.waitingEventAddresses.size} esperando evento (fuera del porcentaje)")
                        append(" · ${progress.noResponse} sin respuesta")
                    },
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!progress.finished && progress.pending > 0) {
                    Text(
                        text = "${progress.pending} pendientes o en recuperación",
                        color = AzulClaro,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (progress.finished && progress.noResponse > 0) {
                    Text(
                        text = "El ${progress.percent}% cuenta solo estados de lectura recibidos. Los mandos y eventos no forman parte del porcentaje.",
                        color = AmarilloEstado,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Por tipo · direcciones KNX", color = AzulClaro, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                kindRows.forEach { (label, received, totals) ->
                    KnxLoadSummaryRow(
                        label = label,
                        received = received,
                        total = totals.first,
                        noResponse = totals.second
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BordeTarjeta.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(14.dp))
                Text("Por estancia · elementos", color = AzulClaro, fontWeight = FontWeight.SemiBold)
                Text(
                    "Estados de lectura y sensores por evento. Los eventos se muestran aparte del porcentaje de carga.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))

                elementsByRoom.forEach { (room, elements) ->
                    val completeElements = elements.count { it.fullyReceived }
                    val withDataElements = elements.count { it.hasAnyReceived }
                    Text(
                        text = "$room · $completeElements/${elements.size} completos · $withDataElements con datos",
                        color = TextoPrincipal,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 5.dp, bottom = 2.dp)
                    )
                    elements.sortedWith(
                        compareBy<ElementStatus> { it.key.kind.label }
                            .thenBy { it.key.name.lowercase(Locale.ROOT) }
                    ).forEach { element ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, top = 2.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val kindLabel = when (element.key.kind) {
                                    KnxLoadKind.LIGHT -> "Luz"
                                    KnxLoadKind.BLIND -> "Persiana"
                                    KnxLoadKind.CLIMATE -> "Climatización"
                                    KnxLoadKind.TEMPERATURE -> "Temperatura"
                                    KnxLoadKind.SENSOR -> "Sensor"
                                    KnxLoadKind.METER -> "Medición"
                                    KnxLoadKind.OTHER -> "Otro"
                                }
                                Text(
                                    text = if (element.addresses.contains(com.onehouse.app.knx.KnxAddressBook.Consumption.ACS_ACCUMULATED)) {
                                        "Consumos · ACS acumulado"
                                    } else "$kindLabel · ${element.key.name}",
                                    color = TextoSecundario,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = element.addresses.sorted().joinToString(" · "),
                                    color = TextoDesactivado,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            val elementStatus = when {
                                element.waitingEventCount > 0 -> "☀ Sin lluvia"
                                element.fullyReceived -> "✓ ${element.receivedCount}/${element.addresses.size}"
                                element.pendingCount > 0 -> "… ${element.receivedCount}/${element.addresses.size}"
                                element.noResponseCount > 0 -> "${element.receivedCount}/${element.addresses.size}"
                                else -> "${element.receivedCount}/${element.addresses.size}"
                            }
                            Text(
                                text = elementStatus,
                                color = when {
                                    element.fullyReceived -> VerdeEstado
                                    element.noResponseCount > 0 -> AmarilloEstado
                                    else -> AzulClaro
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (progress.finished && progress.noResponseAddresses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BordeTarjeta.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("GAs sin respuesta", color = AmarilloEstado, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    progress.noResponseAddresses.sorted().forEach { address ->
                        val target = progress.targets[address]
                        val description = target?.let {
                            val rooms = it.rooms.joinToString(" / ")
                            val names = it.names.joinToString(" / ")
                            "$rooms · $names"
                        } ?: address
                        Text(
                            text = "$address · $description",
                            color = TextoSecundario,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}


@Composable
private fun KnxLoadSummaryRow(
    label: String,
    received: Int,
    total: Int,
    noResponse: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextoSecundario,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = buildString {
                append(received)
                append("/")
                append(total)
                if (noResponse > 0) append(" · $noResponse sin respuesta")
            },
            color = if (noResponse > 0) AmarilloEstado else TextoPrincipal,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ClimateHeroCard(homeState: HomeDashboardUiState) {
    OneHouseCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AzulOneHouse.copy(alpha = 0.22f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = AzulClaro.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Thermostat,
                        contentDescription = null,
                        tint = AzulClaro,
                        modifier = Modifier.padding(10.dp).size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CLIMATIZACIÓN CENTRAL",
                        color = AzulClaro,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (homeState.climate.powered) {
                            true -> "Sistema encendido"
                            false -> "Sistema apagado"
                            null -> "Estado no disponible"
                        },
                        color = if (homeState.climate.powered == true) VerdeEstado else TextoSecundario,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(color = FondoChip, shape = RoundedCornerShape(50)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = climateModeIcon(homeState.climate.mode),
                            contentDescription = null,
                            tint = AzulClaro,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = climateModeLabel(homeState.climate.mode),
                            color = AzulClaro,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = homeState.climateReferenceTemperature?.let {
                        String.format(Locale("es", "ES"), "%.1f", it)
                    } ?: "--,-",
                    color = TextoPrincipal,
                    fontSize = 48.sp,
                    lineHeight = 50.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " °C",
                    color = TextoSecundario,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            Text(
                text = "Temperatura interior de la vivienda",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = BordeTarjeta)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OneHouseStatusItem(
                    title = "Estado",
                    value = when (homeState.climate.powered) {
                        true -> "Encendido"
                        false -> "Apagado"
                        null -> "---"
                    },
                    valueColor = if (homeState.climate.powered == true) VerdeEstado else TextoSecundario,
                    modifier = Modifier.weight(1f)
                )
                OneHouseStatusItem(
                    title = "Consigna",
                    value = homeState.climate.targetTemperature?.let {
                        String.format(Locale("es", "ES"), "%.1f °C", it)
                    } ?: "---",
                    modifier = Modifier.weight(1f)
                )
                OneHouseStatusItem(
                    title = "Ventilador",
                    value = homeState.climate.fanSpeed ?: "---",
                    valueColor = if (homeState.climate.fanSpeed != null) AzulClaro else TextoSecundario,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickStatusGrid(
    weather: WeatherUiState,
    homeState: HomeDashboardUiState
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeStatusCard(
            title = "Luces",
            value = if (homeState.lightsTotal > 0) {
                "${homeState.lightsOn} de ${homeState.lightsTotal} encendidas"
            } else "---",
            detail = "Estado general",
            icon = Icons.Rounded.Lightbulb,
            accent = AmarilloEstado,
            modifier = Modifier.weight(1f).height(150.dp)
        )
        HomeStatusCard(
            title = "Persianas",
            value = if (homeState.blindsTotal > 0) {
                "${homeState.blindsOpen} de ${homeState.blindsTotal} abiertas"
            } else "---",
            detail = "Estado general",
            icon = Icons.Rounded.Blinds,
            accent = AzulClaro,
            modifier = Modifier.weight(1f).height(150.dp)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    ExteriorWeatherCard(
        weather = weather,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ExteriorWeatherCard(
    weather: WeatherUiState,
    modifier: Modifier = Modifier
) {
    OneHouseCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = AzulClaro.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = weatherIcon(weather),
                            contentDescription = null,
                            tint = AzulClaro,
                            modifier = Modifier.padding(7.dp).size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    Column {
                        Text(
                            text = "Exterior",
                            color = TextoSecundario,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                weather.isLoading -> "Actualizando…"
                                weather.temperatureC != null -> weather.condition
                                weather.errorMessage != null -> "Dato no disponible"
                                else -> "Meteorología exterior"
                            },
                            color = TextoDesactivado,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text(
                    text = weather.temperatureC?.let {
                        String.format(Locale("es", "ES"), "%.1f °C", it)
                    } ?: "---",
                    color = TextoPrincipal,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BordeTarjeta)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExteriorMetric(
                    title = "Sensación",
                    value = weather.feelsLikeC?.let { String.format(Locale("es", "ES"), "%.1f °C", it) } ?: "---",
                    modifier = Modifier.weight(1f)
                )
                ExteriorMetric(
                    title = "Humedad",
                    value = weather.humidityPercent?.let { "$it %" } ?: "---",
                    modifier = Modifier.weight(1f)
                )
                ExteriorMetric(
                    title = "Precipitación",
                    value = weather.precipitationMm?.let { String.format(Locale("es", "ES"), "%.1f mm", it) } ?: "---",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExteriorMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = TextoDesactivado,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = TextoPrincipal,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun HomeStatusCard(
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    valueColor: Color = TextoPrincipal,
    onClick: (() -> Unit)? = null
) {
    OneHouseCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accent.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(7.dp).size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = title,
                    color = TextoSecundario,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(13.dp))
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                color = TextoDesactivado,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FavoriteRooms(
    favoriteRooms: List<RoomItem>,
    raining: Boolean?,
    onFavoriteSelected: (String) -> Unit
) {
    if (favoriteRooms.isEmpty()) {
        OneHouseCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "☆", color = AzulClaro, fontSize = 28.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aún no tienes favoritas",
                    color = TextoPrincipal,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Marca una estancia con ☆ para acceder a ella desde aquí.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }

    favoriteRooms.take(4).chunked(2).forEachIndexed { index, rowItems ->
        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowItems.forEach { room ->
                OneHouseSceneCard(
                    name = room.name,
                    symbol = if (room.name == "Terraza") {
                        if (raining == true) "🌧" else "☀"
                    } else room.symbol,
                    modifier = Modifier.weight(1f),
                    onClick = { onFavoriteSelected(room.name) }
                )
            }
            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun homeGreeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 6..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

private fun climateModeLabel(mode: String?): String {
    val normalized = mode?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return when {
        normalized.isBlank() -> "Sin datos"
        normalized in setOf("3", "frio", "frío", "cold", "cool") -> "Frío"
        normalized in setOf("1", "calor", "heat", "heating") -> "Calor"
        normalized in setOf("9", "ventilador", "ventilación", "ventilacion", "vent.", "fan", "fan only") -> "Ventilador"
        normalized in setOf("14", "dry", "seco", "deshumidificación", "deshumidificacion") -> "Dry"
        normalized in setOf("0", "auto", "automatico", "automático") -> "Auto"
        else -> mode.orEmpty()
    }
}

private fun climateModeIcon(mode: String?): ImageVector = when (climateModeLabel(mode)) {
    "Frío" -> Icons.Rounded.AcUnit
    "Calor" -> Icons.Rounded.WbSunny
    "Ventilador" -> Icons.Rounded.Air
    "Dry" -> Icons.Rounded.WaterDrop
    "Auto" -> Icons.Rounded.Thermostat
    else -> Icons.Rounded.Thermostat
}

private fun weatherIcon(weather: WeatherUiState): ImageVector {
    val condition = weather.condition.lowercase(Locale.ROOT)
    return when {
        condition.contains("lluv") || condition.contains("rain") -> Icons.Rounded.WaterDrop
        condition.contains("viento") || condition.contains("wind") -> Icons.Rounded.Air
        condition.contains("sol") || condition.contains("despej") || condition.contains("clear") -> Icons.Rounded.WbSunny
        else -> Icons.Rounded.Home
    }
}

@Composable
private fun ConnectionStatus(status: KnxConnectionStatus, centered: Boolean = true) {
    val label = when (status) {
        KnxConnectionStatus.NOT_TESTED -> "No comprobado"
        KnxConnectionStatus.TESTING -> "Comprobando…"
        KnxConnectionStatus.CONNECTED -> "Conectado"
        KnxConnectionStatus.FAILED -> "Sin conexión"
    }
    val statusColor = when (status) {
        KnxConnectionStatus.CONNECTED -> VerdeEstado
        KnxConnectionStatus.FAILED -> RojoEstado
        KnxConnectionStatus.TESTING -> AzulClaro
        KnxConnectionStatus.NOT_TESTED -> TextoDesactivado
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            color = statusColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
