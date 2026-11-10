package com.onehouse.app.feature.maintenance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Blinds
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.feature.rooms.detail.RoomHeader
import com.onehouse.app.knx.KnxAddressBook
import com.onehouse.app.knx.KnxHomeStateRepository
import com.onehouse.app.knx.KnxCommandExecutor
import com.onehouse.app.knx.StateFreshness
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val MaintenanceCard = Color(0xFF0A1926)
private val Blue = Color(0xFF168EFF)
private val Purple = Color(0xFFBF5BFF)
private val Green = Color(0xFF21D991)
private val Orange = Color(0xFFFF981A)
private val Red = Color(0xFFFF4D45)

@Composable
fun MaintenanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        KnxHomeStateRepository(context.applicationContext)
    }
    val snapshot by repository.stateFlow.collectAsStateWithLifecycle(initialValue = repository.snapshot())

    val kitchenFlood = snapshot.booleanAt(KnxAddressBook.Indoor.FLOOD_KITCHEN)
    val bathroomFlood = snapshot.booleanAt(KnxAddressBook.Indoor.FLOOD_BATHROOM)
    val floodDetected: Boolean? = when {
        kitchenFlood == true || bathroomFlood == true -> true
        kitchenFlood == false && bathroomFlood == false -> false
        else -> null
    }
    val fireDetected = snapshot.booleanAt(KnxAddressBook.Indoor.FIRE_HALLWAY)
    // Provisional ETS polarity agreed for the first real installation test:
    // 0 = closed, 1 = open. A received KNX state takes precedence.
    val executor = remember { KnxCommandExecutor(context.applicationContext) }
    val valveStates by executor.stateFlow.collectAsStateWithLifecycle()
    val valveOpen = valveStates[MaintenanceAddresses.VALVE_STATE]
        ?.takeIf(StateFreshness::isTrusted)?.booleanValue
    // REV19: valve feedback is included in the shared startup/periodic reads.
    DisposableEffect(executor) { onDispose { executor.close() } }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    val lights = MaintenanceAddresses.lightsOffPlan()
    val blinds = MaintenanceAddresses.closeBlindsPlan()
    val general = MaintenanceAddresses.generalOffPlan()
    val climate = booleanMaintenancePlan(KnxAddressBook.Climate.POWER_COMMAND, false, KnxAddressBook.Climate.POWER_STATE)
    fun runPlan(plan: MaintenancePlan) {
        if (busy || plan.unavailable != null || plan.commands.isEmpty()) return
        busy = true
        scope.launch {
            var sent = 0
            val failures = mutableListOf<String>()
            try {
                plan.commands.forEachIndexed { index, item ->
                    operationMessage = "Enviando orden ${index + 1} de ${plan.commands.size}…"
                    val result = suspendCancellableCoroutine<KnxCommandExecutor.Result> { continuation ->
                        executor.execute(item.command, verificationAddress = item.stateAddress) {
                            if (continuation.isActive) continuation.resume(it)
                        }
                    }
                    when (result) {
                        is KnxCommandExecutor.Result.Success -> sent++
                        is KnxCommandExecutor.Result.Failure -> failures += "${item.command.destination}: ${result.message}"
                    }
                }
                operationMessage = "$sent de ${plan.commands.size} órdenes enviadas. Comprueba el estado real en las estancias." +
                    if (failures.isEmpty()) "" else "\n${failures.joinToString("\n")}"
            } finally { busy = false }
        }
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
        RoomHeader("Mantenimiento", onBack)
        Spacer(Modifier.height(16.dp))
        operationMessage?.let { Text(it, color = TextoPrincipal, modifier = Modifier.padding(bottom = 12.dp)) }
        ActionCard(Icons.Rounded.Lightbulb, "Apagado luces", "Apagar todas las luces de la vivienda", Blue, lights, busy) { runPlan(lights) }
        Spacer(Modifier.height(12.dp))
        ActionCard(Icons.Rounded.Home, "Apagado general", "Apagado general: Incluye luces, persianas y clima", Purple, general, busy) { runPlan(general) }
        Spacer(Modifier.height(12.dp))
        ActionCard(Icons.Rounded.AcUnit, "Apagado clima", "Apagar el sistema de climatización", Green, climate, busy) { runPlan(climate) }
        Spacer(Modifier.height(12.dp))
        ActionCard(Icons.Rounded.Blinds, "Cerrar todas las persianas", "Cerrar todas las persianas de la vivienda", Orange, blinds, busy) { runPlan(blinds) }
        Spacer(Modifier.height(12.dp))
        SensorCard(Icons.Rounded.WaterDrop, "Sensor inundación", floodDetected, Color(0xFF28DDE3))
        Spacer(Modifier.height(12.dp))
        SensorCard(Icons.Rounded.LocalFireDepartment, "Sensor incendio", fireDetected, Red)
        Spacer(Modifier.height(12.dp))
        ValveCard(
            open = valveOpen,
            busy = busy,
            onChange = { requestedOpen ->
                runPlan(booleanMaintenancePlan(
                    MaintenanceAddresses.VALVE_COMMAND,
                    requestedOpen,
                    MaintenanceAddresses.VALVE_STATE
                ))
            }
        )
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, subtitle: String, accent: Color, plan: MaintenancePlan, busy: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaintenanceCard, RoundedCornerShape(22.dp)).border(1.dp, BordeTarjeta, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(icon, accent)
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextoSecundario, fontSize = 12.sp)
            plan.unavailable?.let { Text(it, color = TextoSecundario, fontSize = 11.sp) }
        }
        OutlinedButton(onClick = onClick, enabled = !busy && plan.unavailable == null && plan.commands.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Ejecutar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SensorCard(icon: ImageVector, title: String, detected: Boolean?, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaintenanceCard, RoundedCornerShape(22.dp)).border(1.dp, BordeTarjeta, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBox(icon, accent)
        Spacer(Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextoPrincipal, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Estado actual", color = TextoSecundario, fontSize = 12.sp)
        }
        Text(
            when (detected) {
                true -> "⚠ Detección"
                false -> "✓ Sin detecciones"
                null -> "---"
            },
            color = when (detected) {
                true -> Red
                false -> Green
                null -> TextoSecundario
            },
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ValveCard(open: Boolean?, busy: Boolean, onChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(MaintenanceCard, RoundedCornerShape(22.dp)).border(1.dp, BordeTarjeta, RoundedCornerShape(22.dp)).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.Rounded.Settings, Blue)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Electroválvula", color = TextoPrincipal, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Control válvula principal", color = TextoSecundario, fontSize = 11.sp, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Row(modifier = Modifier.border(1.dp, BordeTarjeta, RoundedCornerShape(13.dp))) {
                OutlinedButton(onClick = { onChange(true) }, enabled = !busy) { Text("ON") }
                OutlinedButton(onClick = { onChange(false) }, enabled = !busy) { Text("OFF") }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.fillMaxWidth().background(Blue.copy(alpha = 0.05f), RoundedCornerShape(14.dp)).border(1.dp, BordeTarjeta, RoundedCornerShape(14.dp)).padding(14.dp)
        ) {
            Text(
                when (open) {
                    true -> "La electroválvula está abierta.\nEl suministro de agua está habilitado."
                    false -> "La electroválvula está cerrada.\nEl suministro de agua está interrumpido."
                    null -> "Esperando el estado de la electroválvula."
                },
                color = TextoSecundario,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun IconBox(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier.size(58.dp).background(accent.copy(alpha = 0.11f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(30.dp)
        )
    }
}
