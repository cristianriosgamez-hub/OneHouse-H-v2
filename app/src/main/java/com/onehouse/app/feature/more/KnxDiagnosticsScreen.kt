package com.onehouse.app.feature.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.design.AzulClaro
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.OneHouseCard
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.knx.AppKnxConfigurationRepository
import com.onehouse.app.knx.KnxAddressBook
import com.onehouse.app.knx.KnxCommand
import com.onehouse.app.knx.KnxCommandExecutor
import com.onehouse.app.knx.KnxCommandType
import com.onehouse.app.knx.KnxGroupAddress
import com.onehouse.app.knx.KnxPerformanceMetrics
import com.onehouse.app.knx.KnxSessionStatisticsRepository
import com.onehouse.app.knx.KnxStateRepository
import com.onehouse.app.knx.KnxTelegramEvent
import com.onehouse.app.knx.KnxTelegramMonitorRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class DiagnosticObject(
    val room: String,
    val name: String,
    val category: String,
    val dpt: String,
    val address: String,
    val kind: String,
    val canWriteBoolean: Boolean,
    val readPolicy: KnxDiagnosticReadPolicy = KnxDiagnosticReadPolicy.STATE
)

@Composable
fun KnxDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val projectRepository = remember { AppKnxConfigurationRepository(context.applicationContext).also(KnxAddressBook::apply) }
    val stateRepository = remember { KnxStateRepository(context.applicationContext) }
    val monitorRepository = remember { KnxTelegramMonitorRepository(context.applicationContext) }
    val sessionStatisticsRepository = remember { KnxSessionStatisticsRepository(context.applicationContext) }
    val executor = remember { KnxCommandExecutor(context.applicationContext) }

    var states by remember { mutableStateOf(stateRepository.snapshot()) }
    var events by remember { mutableStateOf(monitorRepository.recent(30)) }
    var monitorSummary by remember { mutableStateOf(monitorRepository.summary()) }
    var sessionStatistics by remember { mutableStateOf(sessionStatisticsRepository.snapshot()) }
    val sessionBaseline = remember { sessionStatisticsRepository.snapshot() }
    var performanceMetrics by remember { mutableStateOf(KnxPerformanceMetrics.snapshot()) }
    val performanceBaseline = remember { KnxPerformanceMetrics.snapshot() }
    var filter by remember { mutableStateOf("") }
    var testingAddress by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val stateCloseable = stateRepository.observe { states = it }
        val eventCloseable = monitorRepository.observe {
            events = it.take(30)
            monitorSummary = monitorRepository.summary()
            sessionStatistics = sessionStatisticsRepository.snapshot()
            performanceMetrics = KnxPerformanceMetrics.snapshot()
        }
        onDispose {
            stateCloseable.close()
            eventCloseable.close()
            executor.close()
        }
    }

    val objects = remember {
        // Lluvia 15/0/15 is a readable KNX state. Do not classify it as event-only:
        // diagnostics must allow a real GroupValueRead and show the received value.
        val eventAddresses = emptySet<String>()
        val globalObjects = KnxAddressBook.entries.map { entry ->
            DiagnosticObject(
                room = entry.room,
                name = entry.name,
                category = entry.category,
                dpt = entry.dpt,
                address = projectRepository.globalAddress(entry.key, entry.defaultAddress),
                kind = entry.kind,
                canWriteBoolean = entry.kind.contains("mando", ignoreCase = true) && entry.dpt.startsWith("1.")
            )
        }
        val roomObjects = projectRepository.loadProject()?.rooms.orEmpty().flatMap { room ->
            room.devices.filter { device -> AppKnxObjectFilter.isVisible(room.name, device) }.flatMap { device ->
                val reads = device.readAddresses.map { address ->
                    DiagnosticObject(
                        room = room.name,
                        name = device.name,
                        category = device.category.displayName,
                        dpt = device.dataPointType.orEmpty().ifBlank { "Sin DPT" },
                        address = address,
                        kind = "Lectura/estado",
                        canWriteBoolean = false
                    )
                }
                val writes = device.writeAddresses.filterNot { it in device.readAddresses }.map { address ->
                    val dpt = device.dataPointType.orEmpty().ifBlank { "Sin DPT" }
                    DiagnosticObject(
                        room = room.name,
                        name = device.name,
                        category = device.category.displayName,
                        dpt = dpt,
                        address = address,
                        kind = "Mando/escritura",
                        canWriteBoolean = dpt.startsWith("1.")
                    )
                }
                reads + writes
            }
        }
        (globalObjects + roomObjects)
            .map { it.copy(readPolicy = diagnosticReadPolicy(it.kind, it.address, eventAddresses)) }
            .distinctBy { listOf(it.room, it.name, it.address, it.kind) }
    }

    val invalidAddresses = objects.map { it.address }
        .filterNot(AppKnxConfigurationRepository::isValidGroupAddress)
        .distinct()
    val duplicateAddresses = objects.map { it.address }
        .filter(AppKnxConfigurationRepository::isValidGroupAddress)
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    val visible = objects.filter { item ->
        filter.isBlank() || listOf(item.room, item.name, item.category, item.dpt, item.address)
            .any { it.contains(filter, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.padding(end = 6.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = TextoPrincipal
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Diagnóstico KNX", color = TextoPrincipal, style = MaterialTheme.typography.headlineMedium)
                    Text("Estado, actividad y herramientas de diagnóstico", color = TextoSecundario)
                }
            }

            OneHouseCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resumen de la instalación", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Objetos de OneHouse: ${objects.size}", color = TextoSecundario)
                    Text("Direcciones con valor recibido: ${states.keys.count { address -> objects.any { it.address == address } }}", color = TextoSecundario)
                    Text("Mando y eventos pueden no responder a lecturas. Un valor recibido puede proceder de una respuesta o de un telegrama espontáneo.", color = TextoSecundario, fontSize = 12.sp)
                    Text("Telegramas recientes: ${events.size}", color = TextoSecundario)
                    Text(
                        text = when {
                            invalidAddresses.isNotEmpty() -> "Direcciones no válidas: ${invalidAddresses.joinToString()}"
                            duplicateAddresses.isNotEmpty() -> "Direcciones compartidas: ${duplicateAddresses.joinToString()}"
                            else -> "Configuración verificada · sin errores de formato"
                        },
                        color = if (invalidAddresses.isEmpty()) AzulClaro else MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }

            OneHouseCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Salud de comunicaciones", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Historial pasivo: observa KNX sin generar telegramas adicionales.",
                        color = TextoSecundario,
                        fontSize = 12.sp
                    )
                    Text(
                        "Eventos: ${monitorSummary.total} · TX ${monitorSummary.outgoing} · RX ${monitorSummary.incoming} · Sistema ${monitorSummary.system}",
                        color = TextoSecundario
                    )
                    Text(
                        text = if (monitorSummary.errors == 0) {
                            "Errores registrados: 0"
                        } else {
                            "Errores registrados: ${monitorSummary.errors}"
                        },
                        color = if (monitorSummary.errors == 0) AzulClaro else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    monitorSummary.lastActivity?.let { last ->
                        Text(
                            "Última actividad: ${formatDateTime(last.timestampMillis)} · ${eventKindLabel(last)}",
                            color = TextoSecundario,
                            fontSize = 12.sp
                        )
                    }
                    monitorSummary.lastError?.let { lastError ->
                        Text(
                            "Último error: ${formatDateTime(lastError.timestampMillis)} · ${lastError.detail ?: eventKindLabel(lastError)}",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        "Desde abrir Diagnóstico · conexiones ${sessionStatistics.connections - sessionBaseline.connections} · errores conexión ${sessionStatistics.connectionErrors - sessionBaseline.connectionErrors} · operaciones ${sessionStatistics.telegramsSent - sessionBaseline.telegramsSent} · errores operación ${sessionStatistics.operationErrors - sessionBaseline.operationErrors}",
                        color = AzulClaro,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Histórico acumulado · conexiones ${sessionStatistics.connections} · errores conexión ${sessionStatistics.connectionErrors} · operaciones ${sessionStatistics.telegramsSent} · errores operación ${sessionStatistics.operationErrors}",
                        color = TextoSecundario,
                        fontSize = 12.sp
                    )
                    Text(
                        "ACK gateway histórico ${sessionStatistics.gatewayAcks} · último/media ${sessionStatistics.lastAckMillis?.let { "$it ms" } ?: "—"} / ${sessionStatistics.averageAckMillis?.let { "$it ms" } ?: "—"}",
                        color = TextoSecundario,
                        fontSize = 12.sp
                    )
                    OutlinedButton(
                        onClick = { copyDiagnosticsToClipboard(context, monitorRepository, performanceMetrics, performanceBaseline, sessionStatistics, sessionBaseline) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copiar diagnóstico")
                    }
                }
            }

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Buscar estancia, objeto, DPT o X/X/X") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            visible.forEach { item ->
                val state = states[item.address]
                val age = state?.let { formatAge(System.currentTimeMillis() - it.timestampMillis) }
                OneHouseCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = TextoPrincipal, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                Text("${item.room} · ${item.category}", color = TextoSecundario, fontSize = 13.sp)
                            }
                            Text(
                                text = item.readPolicy.label(state != null),
                                color = if (state != null) AzulClaro else TextoSecundario,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Text("${item.kind}: ${item.address}", color = TextoPrincipal)
                        Text("DPT: ${item.dpt}", color = TextoSecundario)
                        Text(item.readPolicy.explanation, color = TextoSecundario, fontSize = 12.sp)
                        if (state == null) {
                            Text("Último valor: ---", color = TextoSecundario)
                        } else {
                            Text("Último valor: ${displayValue(state)}", color = TextoPrincipal)
                            Text("Recibido: $age · origen ${state.sourceAddress} · ${state.apci}", color = TextoSecundario, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                executeDiagnosticCommand(
                                    context = context,
                                    executor = executor,
                                    item = item,
                                    type = KnxCommandType.READ,
                                    onStarted = { testingAddress = item.address },
                                    onFinished = { testingAddress = null }
                                )
                            },
                            enabled = testingAddress == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (testingAddress == item.address) "Leyendo…" else "Probar lectura")
                        }

                        if (item.canWriteBoolean) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        executeDiagnosticCommand(
                                            context = context,
                                            executor = executor,
                                            item = item,
                                            type = KnxCommandType.ON,
                                            onStarted = { testingAddress = item.address },
                                            onFinished = { testingAddress = null }
                                        )
                                    },
                                    enabled = testingAddress == null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Probar ON")
                                }
                                OutlinedButton(
                                    onClick = {
                                        executeDiagnosticCommand(
                                            context = context,
                                            executor = executor,
                                            item = item,
                                            type = KnxCommandType.OFF,
                                            onStarted = { testingAddress = item.address },
                                            onFinished = { testingAddress = null }
                                        )
                                    },
                                    enabled = testingAddress == null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Probar OFF")
                                }
                            }
                            Text(
                                "Escritura habilitada únicamente para objetos binarios de mando.",
                                color = TextoSecundario,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Text("Actividad KNX reciente · últimos 30", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (events.isEmpty()) {
                Text("Todavía no hay operaciones KNX registradas.", color = TextoSecundario)
            } else {
                events.forEach { event -> TelegramEventCard(event) }
                OutlinedButton(onClick = { monitorRepository.clear() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpiar monitor")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun executeDiagnosticCommand(
    context: android.content.Context,
    executor: KnxCommandExecutor,
    item: DiagnosticObject,
    type: KnxCommandType,
    onStarted: () -> Unit,
    onFinished: () -> Unit
) {
    val address = runCatching { KnxGroupAddress.parse(item.address) }.getOrNull()
    if (address == null) {
        Toast.makeText(context, "Dirección KNX inválida", Toast.LENGTH_SHORT).show()
        return
    }

    onStarted()
    executor.execute(
        KnxCommand(
            type = type,
            destination = address,
            dpt = item.dpt
        ),
        retryReadOnce = false
    ) { result ->
        onFinished()
        val message = when (result) {
            is KnxCommandExecutor.Result.Success -> when (type) {
                KnxCommandType.READ -> result.busValue?.let { "Respuesta: $it" }
                    ?: item.readPolicy.readWithoutValueMessage()
                KnxCommandType.ON -> "Orden ON enviada y verificada"
                KnxCommandType.OFF -> "Orden OFF enviada y verificada"
                else -> "Operación KNX completada"
            }
            is KnxCommandExecutor.Result.Failure -> result.message
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun TelegramEventCard(event: KnxTelegramEvent) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    OneHouseCard {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${formatter.format(Date(event.timestampMillis))} · ${statusLabel(event.status)}",
                color = TextoPrincipal,
                fontWeight = FontWeight.SemiBold
            )
            Text("${directionLabel(event.direction)} · ${kindLabel(event.kind)}", color = TextoSecundario, fontSize = 12.sp)
            event.groupAddress?.let { Text("GA $it${event.value?.let { value -> " · $value" }.orEmpty()}", color = TextoPrincipal) }
            event.detail?.let { Text(it, color = TextoSecundario, fontSize = 12.sp) }
        }
    }
}

private fun copyDiagnosticsToClipboard(
    context: Context,
    monitorRepository: KnxTelegramMonitorRepository,
    metrics: KnxPerformanceMetrics.Snapshot,
    metricsBaseline: KnxPerformanceMetrics.Snapshot,
    session: KnxSessionStatisticsRepository.Snapshot,
    sessionBaseline: KnxSessionStatisticsRepository.Snapshot
) {
    val summary = monitorRepository.summary()
    val text = buildString {
        appendLine("OneHouse · Diagnóstico KNX")
        appendLine("Generado: ${formatDateTime(System.currentTimeMillis())}")
        appendLine("Eventos: ${summary.total} · TX ${summary.outgoing} · RX ${summary.incoming} · Sistema ${summary.system} · Errores ${summary.errors}")
        appendLine("Desde abrir Diagnóstico · túnel: aperturas ${metrics.tunnelOpenAttempts - metricsBaseline.tunnelOpenAttempts} · reutilizados ${metrics.tunnelReuses - metricsBaseline.tunnelReuses} · conectados ${metrics.tunnelConnectSuccesses - metricsBaseline.tunnelConnectSuccesses} · código36 ${metrics.tunnelRejected36 - metricsBaseline.tunnelRejected36}")
        appendLine("Desde abrir Diagnóstico · sesión: conexiones ${session.connections - sessionBaseline.connections} · erroresConexión ${session.connectionErrors - sessionBaseline.connectionErrors} · operaciones ${session.telegramsSent - sessionBaseline.telegramsSent} · erroresOperación ${session.operationErrors - sessionBaseline.operationErrors}")
        appendLine("Histórico túnel: aperturas ${metrics.tunnelOpenAttempts} · reutilizados ${metrics.tunnelReuses} · conectados ${metrics.tunnelConnectSuccesses} · código36 ${metrics.tunnelRejected36} · cierres ${metrics.tunnelDisconnects}")
        appendLine("Histórico sesión: conexiones ${session.connections} · erroresConexión ${session.connectionErrors} · operaciones ${session.telegramsSent} · ACK ${session.gatewayAcks} · erroresOperación ${session.operationErrors} · retransmisiones ${session.retransmissions}")
        appendLine()
        append(monitorRepository.exportText())
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico KNX OneHouse", text))
    Toast.makeText(context, "Diagnóstico KNX copiado", Toast.LENGTH_SHORT).show()
}

private fun formatDateTime(timestampMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

private fun eventKindLabel(event: KnxTelegramEvent): String =
    "${directionLabel(event.direction)} · ${kindLabel(event.kind)} · ${statusLabel(event.status)}"

private fun directionLabel(direction: KnxTelegramEvent.Direction): String = when (direction) {
    KnxTelegramEvent.Direction.OUTGOING -> "TX"
    KnxTelegramEvent.Direction.INCOMING -> "RX"
    KnxTelegramEvent.Direction.SYSTEM -> "SISTEMA"
}

private fun kindLabel(kind: KnxTelegramEvent.Kind): String = when (kind) {
    KnxTelegramEvent.Kind.CONNECT -> "CONEXIÓN"
    KnxTelegramEvent.Kind.GROUP_VALUE_READ -> "LECTURA"
    KnxTelegramEvent.Kind.GROUP_VALUE_WRITE -> "ESCRITURA"
    KnxTelegramEvent.Kind.GROUP_VALUE_RESPONSE -> "RESPUESTA"
    KnxTelegramEvent.Kind.DISCONNECT -> "DESCONEXIÓN"
}

private fun statusLabel(status: KnxTelegramEvent.Status): String = when (status) {
    KnxTelegramEvent.Status.PENDING -> "PENDIENTE"
    KnxTelegramEvent.Status.CONFIRMED -> "OK"
    KnxTelegramEvent.Status.RECEIVED -> "RECIBIDO"
    KnxTelegramEvent.Status.ERROR -> "ERROR"
}

private fun displayValue(state: KnxStateRepository.State): String = when {
    state.booleanValue != null -> if (state.booleanValue) "Encendido / 1" else "Apagado / 0"
    !state.rawValue.isNullOrBlank() -> "0x${state.rawValue}"
    else -> "---"
}

private fun formatSourceCounts(values: Map<String, Long>): String =
    if (values.isEmpty()) "—" else values.entries.joinToString(" · ") { (source, count) -> "$source=$count" }

private fun formatMicros(value: Long?): String = value?.let { "$it µs" } ?: "—"

private fun formatAge(milliseconds: Long): String {
    val safe = milliseconds.coerceAtLeast(0L)
    return when {
        safe < 1_000L -> "ahora"
        safe < 60_000L -> "hace ${TimeUnit.MILLISECONDS.toSeconds(safe)} s"
        safe < 3_600_000L -> "hace ${TimeUnit.MILLISECONDS.toMinutes(safe)} min"
        else -> "hace ${TimeUnit.MILLISECONDS.toHours(safe)} h"
    }
}
