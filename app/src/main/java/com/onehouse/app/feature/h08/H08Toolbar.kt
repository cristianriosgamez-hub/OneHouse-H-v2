package com.onehouse.app.feature.h08

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Panel pequeño SIEMPRE visible sobre las pantallas reales de OneHouse. */
@Composable
fun H08Toolbar() {
    val context = LocalContext.current
    val state by H08Coordinator.status.collectAsState()
    val controlState by H082LightControlCoordinator.state.collectAsState()
    val monitor by H091MonitorCoordinator.state.collectAsState()
    var showConfig by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("3671") }
    var configError by remember { mutableStateOf<String?>(null) }
    var writeError by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            writeError = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(H08Coordinator.exportCsv())
                } ?: error("No se pudo abrir el fichero CSV")
            }.exceptionOrNull()?.message
        }
    }

    val controlExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            writeError = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(H082LightControlCoordinator.exportControlCsv())
                } ?: error("No se pudo abrir el CSV de control")
            }.exceptionOrNull()?.message
        }
    }

    val monitorExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            writeError = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(H091MonitorCoordinator.exportCsv())
                } ?: error("No se pudo abrir el CSV del monitor")
            }.exceptionOrNull()?.message
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF173047))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "H09.2 TEST · H08.2.2 ESTABLE + EVENTOS COCINA",
            color = Color(0xFF90DBFF),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = state.message,
            color = if (state.lastRun != null && !state.success) Color(0xFFFFC88A) else Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedButton(
                onClick = {
                    val values = H08Coordinator.settingsIp()
                    ip = values.first
                    port = values.second
                    configError = null
                    showConfig = true
                },
                enabled = !state.running && !controlState.busy && !H091MonitorCoordinator.isBusy()
            ) { Text("IP KNX") }
            Button(
                onClick = { H08Coordinator.startRead() },
                enabled = state.configured && !state.running && !controlState.busy && !H091MonitorCoordinator.isBusy()
            ) { Text(if (state.running) "Leyendo…" else if (state.lastRun != null && !state.lastRunSuccess) "Reintentar H" else "Leer H") }
            OutlinedButton(
                onClick = {
                    val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                    val id = state.lastRunId.substringAfterLast('-').ifBlank { "0" }
                    exporter.launch("OneHouse_H0822_LECTURA_${id}_$now.csv")
                },
                enabled = !monitor.running && !state.running && state.lastRun != null
            ) { Text("CSV") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(
                enabled = if (monitor.running) !monitor.stopping else
                    H091MonitorCoordinator.permitsReadId(state.lastRunId) && state.lastRunSuccess && state.imported == H08Safety.EXPECTED_READS &&
                        !state.running && !controlState.busy && !H091MonitorCoordinator.isBusy(),
                onClick = {
                    if (monitor.running) H091MonitorCoordinator.stop()
                    else H091MonitorCoordinator.start(context)
                }
            ) { Text(if (monitor.running) "Detener H09" else "Monitor Cocina") }
            OutlinedButton(
                enabled = !monitor.running && monitor.snapshot != null,
                onClick = {
                    val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                    monitorExporter.launch("OneHouse_H092_COCINA_EVENTOS_$now.csv")
                }
            ) { Text("CSV Eventos") }
        }
        Text(
            text = monitor.message,
            color = if (monitor.running) Color(0xFFB9F3C5) else Color(0xFFDCE3EB),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (monitor.snapshot?.connected == true) {
            val values = monitor.snapshot?.values.orEmpty()
            fun label(ga: String): String = when (values[ga]?.value) {
                true -> "ON"
                false -> "OFF"
                null -> "sin evento"
            }
            Text(
                "Eventos recibidos: Luz ${label("1/2/6")} · Fluorescente ${label("1/2/5")}",
                color = Color(0xFFA5E8FF), style = MaterialTheme.typography.labelSmall
            )
        }
        if (controlState.lastResult != null || controlState.busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    controlState.message,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFFFD29A),
                    style = MaterialTheme.typography.labelSmall
                )
                OutlinedButton(
                    enabled = !monitor.running && !controlState.busy && controlState.lastResult != null,
                    onClick = {
                        val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                        controlExporter.launch("OneHouse_H0822_COCINA_CONTROL_$now.csv")
                    }
                ) { Text("CSV Mando") }
            }
        }
        if (state.lastRun != null) {
            Text(
                "Lectura #${state.lastRunSequence} · " +
                    "${if (state.lastRunSuccess) "PASS" else "NO_VALIDADA"} · " +
                    "${state.received}/${state.total} · caché ${state.screenLoadMs ?: "---"} ms · " +
                    "ID ${state.lastRunId}",
                color = if (state.lastRunSuccess) Color(0xFFB9F3C5) else Color(0xFFFFC88A),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (writeError != null) {
            Text("CSV: $writeError", color = Color(0xFFFFC88A), style = MaterialTheme.typography.labelSmall)
        }
        Text(
            "Si falla UDP/EPERM, exporta CSV, espera 30 s, verifica WiFi y reintenta manualmente. " +
                "No hay reconexión automática. H09 monitoriza 90 s bajo activación manual; " +
                "detén el monitor antes de leer o controlar.",
            color = Color(0xFFBDD5E3), style = MaterialTheme.typography.labelSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }

    if (showConfig) {
        AlertDialog(
            onDismissRequest = { showConfig = false },
            title = { Text("Gateway KNX/IP local · H09.2 TEST") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Solo WiFi local, NAT desactivado. Este ajuste es independiente de OneHouse estable.")
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it.trim() },
                        label = { Text("Dirección IPv4") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit).take(5) },
                        label = { Text("Puerto") },
                        singleLine = true
                    )
                    if (configError != null) Text("$configError", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    configError = H08Coordinator.saveEndpoint(ip, port)
                    if (configError == null) showConfig = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showConfig = false }) { Text("Cancelar") } }
        )
    }
}
