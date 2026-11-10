package com.onehouse.app.feature.consumption.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.design.*
import com.onehouse.app.data.energy.MeterType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsumptionTransferScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ConsumptionExcelManager(context.applicationContext) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedTypes by remember {
        mutableStateOf(setOf(MeterType.ENDESA, MeterType.AGBAR, MeterType.CLIMATIZATION, MeterType.ACS))
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = runCatching { manager.export(uri, selectedTypes) }.fold({ "$it lecturas exportadas correctamente." }, { "Error al exportar: ${it.message}" })
            busy = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = runCatching { manager.import(uri) }.fold(
                { "Importación terminada: ${it.newReadings} nuevas, ${it.duplicates} ya existentes, ${it.invalid} incorrectas." },
                { "Error al importar: ${it.message}" }
            )
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "‹",
                    color = TextoPrincipal,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .clickable(enabled = !busy, onClick = onBack)
                        .padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Importar / Exportar consumos",
                        color = TextoPrincipal,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Gestiona en un único Excel el histórico de ENDESA, AGBAR, Climatización y ACS.",
                        color = TextoSecundario
                    )
                }
            }

            OneHouseCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Exportar a Excel", color = TextoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Elige qué contadores quieres incluir. Cada contador se exporta en su propia hoja.", color = TextoSecundario)

                    MeterType.entries.forEach { type ->
                        val checked = type in selectedTypes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selected ->
                                    selectedTypes = if (selected) selectedTypes + type else selectedTypes - type
                                },
                                enabled = !busy
                            )
                            Text(
                                text = when (type) {
                                    MeterType.ENDESA -> "ENDESA"
                                    MeterType.AGBAR -> "AGBAR"
                                    MeterType.CLIMATIZATION -> "CLIMATIZACIÓN"
                                    MeterType.ACS -> "ACS"
                                },
                                color = TextoPrincipal
                            )
                        }
                    }

                    Button(enabled = !busy && selectedTypes.isNotEmpty(), onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        exportLauncher.launch("OneHouse_Consumos_$stamp.xlsx")
                    }, modifier = Modifier.fillMaxWidth()) { Text("Exportar seleccionados") }
                }
            }
            OneHouseCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Importar desde Excel", color = TextoPrincipal, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Importa un Excel exportado por OneHouse. Las lecturas con la misma fecha y contador no se duplican ni sobrescriben.", color = TextoSecundario)
                    OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar Excel") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Surface(color = FondoTarjeta, shape = RoundedCornerShape(16.dp)) { Text(it, Modifier.padding(16.dp), color = TextoPrincipal) } }
            Text("Formato: Fecha · Lectura · Consumo · Coste · Unidad · Origen · Nota. La fecha se exporta como yyyy-MM-dd.", color = TextoSecundario, fontSize = 12.sp)
        }
    }
}
