package com.onehouse.app.feature.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.OneHouseCard
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupRestoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { BackupManager(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                status = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = manager.createBackup(currentVersionName(context))
                        writeText(context, uri, json)
                        "Copia creada correctamente."
                    }.getOrElse { "No se pudo crear la copia: ${it.message.orEmpty()}" }
                }
                isBusy = false
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                val result = withContext(Dispatchers.IO) {
                    runCatching { manager.preview(readText(context, uri)) }
                }
                result
                    .onSuccess { preview = it; status = null }
                    .onFailure { status = "Archivo no válido: ${it.message.orEmpty()}" }
                isBusy = false
            }
        }
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
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(enabled = !isBusy, onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = TextoPrincipal
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Backup y restauración",
                        color = TextoPrincipal,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text("Protege y recupera la configuración de OneHouse", color = TextoSecundario)
                }
            }

            OneHouseCard {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Crear copia", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Incluye la configuración KNX activa, la conexión local/remota y la programación de climatización. Los consumos se gestionan desde Importar/Exportar consumos.",
                        color = TextoSecundario
                    )
                    Button(
                        enabled = !isBusy,
                        onClick = {
                            createDocument.launch("OneHouse_backup_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Exportar copia JSON") }
                }
            }

            OneHouseCard {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Restaurar copia", color = TextoPrincipal, fontWeight = FontWeight.SemiBold)
                    Text(
                        "El archivo se valida por estructura e integridad SHA-256 antes de sobrescribir los datos actuales.",
                        color = TextoSecundario
                    )
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = { openDocument.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Seleccionar archivo") }
                }
            }

            if (isBusy) {
                OneHouseCard {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text("Procesando…", color = TextoPrincipal)
                    }
                }
            }

            status?.let {
                OneHouseCard { Text(it, modifier = Modifier.padding(18.dp), color = TextoPrincipal) }
            }
        }
    }

    preview?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!isBusy) preview = null },
            title = { Text("Confirmar restauración") },
            text = {
                val date = DateFormat.getDateTimeInstance().format(Date(selected.summary.createdAtMillis))
                val validation = selected.validation
                val validationText = if (validation.isClean) {
                    "Validación: OK · estructura e integridad verificadas\n"
                } else {
                    "Validación: con avisos\n" +
                        validation.warnings.joinToString(separator = "\n") { "• $it" } + "\n"
                }
                Text(
                    "Fecha: $date\nVersión: ${selected.summary.appVersion}\n" +
                        "Esquema: ${validation.schemaVersion}\n" +
                        "Bloques de configuración: ${validation.compatiblePreferenceFiles}\n" +
                        validationText +
                        "Estados KNX activos: ${selected.summary.knxActiveAddresses}\n" +
                        "Programaciones de clima: ${selected.summary.climateScheduleEvents}\n" +
                        "\nEsta copia pertenece al formato definitivo de la OneHouse actual." +
                        "\nRestauración transaccional: si algo falla, OneHouse recuperará automáticamente la configuración anterior."
                )
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        scope.launch {
                            isBusy = true
                            val result = withContext(Dispatchers.IO) {
                                manager.restore(selected.rawJson)
                            }
                            status = when (result) {
                                is BackupOperationResult.Success -> result.message
                                is BackupOperationResult.Error -> result.message
                            }
                            preview = null
                            isBusy = false
                        }
                    }
                ) { Text("Restaurar") }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !isBusy,
                    onClick = { preview = null }
                ) { Text("Cancelar") }
            }
        )
    }
}

private fun currentVersionName(context: Context): String = runCatching {
    val packageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    packageInfo.versionName ?: "Desconocida"
}.getOrDefault("Desconocida")

private fun writeText(context: Context, uri: Uri, content: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(content) }
        ?: error("No se pudo abrir el archivo de destino.")
}

private fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        ?: error("No se pudo leer el archivo seleccionado.")
