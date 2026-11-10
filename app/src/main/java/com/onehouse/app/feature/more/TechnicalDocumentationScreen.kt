package com.onehouse.app.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onehouse.app.design.AzulClaro
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.OneHouseCard
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario

@Composable
fun TechnicalDocumentationScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = TextoPrincipal)
            }
            Column {
                Text("Documentación técnica", color = TextoPrincipal, style = MaterialTheme.typography.headlineSmall)
                Text("Arquitectura y decisiones de OneHouse", color = TextoSecundario)
            }
        }

        DocSection("Estado del documento", "REV22 es la revisión de limpieza final sobre REV21. REV20 FIX UI Final queda como baseline estable previo y REV21 incorpora el refinamiento visual de Climatización sin alterar el motor KNX. La instalación real ha validado arranque, conexión local/remota, carga inicial, climatización y Backup/Restore.")
        DocSection("Arquitectura", "OneHouse es una aplicación Android con interfaz Jetpack Compose. El motor KNX centraliza conexión, lectura inicial, estados en tiempo real y envío de comandos. La UI consume repositorios de estado y no debe inventar valores KNX cuando no existe un dato real recibido.")
        DocSection("KNX/IP", "Transporte actual: KNXnet/IP Tunnelling sobre UDP 3671. La aplicación negocia un canal de túnel antes de leer o escribir direcciones de grupo. Wi-Fi local y acceso remoto por datos móviles han sido validados en la instalación real.")
        DocSection("Carga inicial KNX", "La referencia actual es 36 GA resueltas para completar el 100%. El porcentaje representa estados de lectura y no órdenes globales. Los mandos 1/1/50, 2/1/50, 2/1/53, 5/5/3, 5/5/13, 5/5/14 y 2/3/1 no se contabilizan como estados de arranque. La lluvia se trata como sensor por evento y su ausencia se presenta como estado sin lluvia, sin bloquear el cierre de la carga.")
        DocSection("Climatización", "Climatización central validada con estados KNX reales: encendido, consigna, modo, ventilador y temperaturas. Auto utiliza el modo KNX existente y ha sido probado en instalación. Velocidades de ventilador conservadas según la base estable: Baja 25%, Media 37% y Alta 100%. El rango visual de consigna es 16,0–34,0 °C.")
        DocSection("Mantenimiento", "El estado de la electroválvula se lee por 2/4/1 y el mando se realiza por 2/3/1. Los mandos generales permanecen fuera de la carga inicial y sus efectos se verifican mediante los estados individuales. La interfaz muestra Electroválvula con controles ON/OFF sin alterar la política KNX validada.")
        DocSection("Consumos", "ACS utiliza la lectura acumulada KNX 15/5/67 (DPT9, kW) y forma parte de la carga inicial. Endesa, Agbar y Climatización mantienen su gestión de consumos en la aplicación; ACS y Climatización no muestran coste.")
        DocSection("Backup y restauración", "OneHouse dispone de Backup/Restore propio para configuración admitida, KNX y programación. La restauración ha sido probada en dispositivo real y recupera el estado guardado. El backup automático del sistema Android se desactiva en REV22 para evitar restauraciones externas de preferencias que compitan con el mecanismo controlado por OneHouse.")
        DocSection("Estado validado", "PASSED: compilación de la base REV20, arranques repetidos, carga KNX hasta 100%, funcionamiento por Wi-Fi y 5G, modo Auto de climatización y Backup/Restore. REV22 elimina de la interfaz el bloque de diagnóstico Estado de ejecución y actualiza esta documentación sin modificar el motor KNX validado.")
    }
}

@Composable
private fun DocSection(title: String, body: String) {
    OneHouseCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = AzulClaro, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(body, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
