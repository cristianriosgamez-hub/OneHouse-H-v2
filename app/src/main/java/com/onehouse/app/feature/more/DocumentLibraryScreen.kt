package com.onehouse.app.feature.more

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.design.AzulClaro
import com.onehouse.app.design.AzulOneHouse
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.OneHouseCard
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Documentos incluidos dentro del APK. Para revisar un PDF en futuras versiones
 * se sustituye exclusivamente el asset correspondiente sin descargar nada de Internet.
 */
enum class OneHouseDocument(
    val heading: String,
    val description: String,
    val assetFile: String,
    val downloadName: String,
    val pageCountLabel: String
) {
    MANUAL(
        heading = "Manual completo de OneHouse",
        description = "Pantallas, estancias, KNX, climatización, radar, consumos, mantenimiento y uso diario.",
        assetFile = "documentacion/manual_onehouse_v2_0_0.pdf",
        downloadName = "OneHouse_Manual_Completo_v2.0.0.pdf",
        pageCountLabel = "24 páginas"
    ),
    AUDITORIA(
        heading = "Auditoría técnica ETS / KNX",
        description = "Arquitectura, direcciones de grupo, configuración de fábrica, comparativa ETS y pruebas.",
        assetFile = "documentacion/auditoria_ets_knx_v2_0_0.pdf",
        downloadName = "OneHouse_Auditoria_ETS_KNX_v2.0.0.pdf",
        pageCountLabel = "19 páginas"
    )
}

@Composable
fun DocumentLibraryScreen(
    onBack: () -> Unit,
    onOpenDocument: (OneHouseDocument) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var documentToSave by rememberSaveable { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Storage Access Framework: el usuario elige dónde guardar; no hacen falta permisos de almacenamiento.
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val doc = OneHouseDocument.entries.firstOrNull { it.name == documentToSave }
        documentToSave = null
        if (uri != null && doc != null) {
            scope.launch {
                saving = true
                status = withContext(Dispatchers.IO) {
                    runCatching {
                        copyBundledPdf(context, doc, uri)
                        "PDF guardado: ${doc.heading}."
                    }.getOrElse { "No se pudo guardar el PDF: ${it.message ?: "Error de almacenamiento"}" }
                }
                saving = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = TextoPrincipal)
            }
            Column(modifier = Modifier.padding(start = 6.dp)) {
                Text("Documentación", color = TextoPrincipal, style = MaterialTheme.typography.headlineSmall)
                Text("Biblioteca oficial de OneHouse", color = TextoSecundario)
            }
        }

        Text(
            "Consulta los documentos directamente en OneHouse o guarda una copia en tu móvil. " +
                "Los dos PDF están incluidos en la aplicación y funcionan sin Internet.",
            color = TextoSecundario,
            style = MaterialTheme.typography.bodyMedium
        )

        OneHouseDocument.entries.forEach { doc ->
            DocumentCard(
                document = doc,
                enabled = !saving,
                onView = { onOpenDocument(doc) },
                onDownload = {
                    status = null
                    documentToSave = doc.name
                    if (!com.onehouse.app.feature.h08.H091MonitorCoordinator.isBusy()) createDocument.launch(doc.downloadName)
                }
            )
        }

        if (status != null) {
            OneHouseCard {
                Text(
                    status.orEmpty(),
                    color = if (status?.startsWith("PDF guardado") == true) AzulClaro else TextoPrincipal,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (saving) {
            Text("Guardando documento…", color = AzulClaro)
        }

        Surface(color = AzulClaro.copy(alpha = 0.09f), shape = RoundedCornerShape(18.dp)) {
            Text(
                "Edición documental: 23/10/2026 · OneHouse v2.0.0 (Entrega 4). " +
                    "Documentos de revisión: se podrán sustituir por la edición definitiva sin cambiar el visor. " +
                    "La documentación técnica histórica de la aplicación se mantiene en Más.",
                color = TextoSecundario,
                modifier = Modifier.padding(16.dp),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun DocumentCard(
    document: OneHouseDocument,
    enabled: Boolean,
    onView: () -> Unit,
    onDownload: () -> Unit
) {
    val icon: ImageVector = if (document == OneHouseDocument.MANUAL) Icons.Rounded.MenuBook else Icons.Rounded.Description
    OneHouseCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = AzulClaro.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AzulClaro,
                        modifier = Modifier.padding(14.dp).size(30.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(document.heading, fontWeight = FontWeight.SemiBold, color = TextoPrincipal, fontSize = 18.sp)
                    Text("PDF · ${document.pageCountLabel} · sin conexión", color = AzulClaro, fontSize = 12.sp)
                }
            }
            Text(document.description, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onView,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(49.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulOneHouse),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Text("Ver", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onDownload,
                    enabled = enabled,
                    modifier = Modifier.weight(1.2f).height(49.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = AzulClaro)
                    Spacer(Modifier.size(6.dp))
                    Text("Descargar", color = AzulClaro, maxLines = 1)
                }
            }
        }
    }
}

private fun copyBundledPdf(context: Context, doc: OneHouseDocument, target: Uri) {
    val output = context.contentResolver.openOutputStream(target)
        ?: error("No se pudo abrir el destino seleccionado")
    output.use { destination ->
        context.assets.open(doc.assetFile).use { original ->
            original.copyTo(destination)
        }
    }
}
