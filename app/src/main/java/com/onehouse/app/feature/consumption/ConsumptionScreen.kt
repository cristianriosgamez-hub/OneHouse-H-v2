package com.onehouse.app.feature.consumption

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.data.energy.EnergyHistorySeeder
import com.onehouse.app.data.energy.EnergyRepository
import com.onehouse.app.data.local.OneHouseDatabase
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.feature.rooms.detail.RoomHeader
import com.onehouse.app.knx.KnxAddressBook
import com.onehouse.app.knx.KnxCentralEngine
import com.onehouse.app.knx.KnxValueDecoder

@Composable
fun ConsumptionScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val database = remember(applicationContext) {
        OneHouseDatabase.getInstance(applicationContext)
    }
    val repository = remember(database) { EnergyRepository(database.energyReadingDao()) }
    val viewModel = remember(repository) { EnergyViewModel(repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analyticsPoints by viewModel.analyticsPoints.collectAsStateWithLifecycle()
    val smartEnergy by viewModel.smartEnergy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val knxResources = remember(applicationContext) { KnxCentralEngine.get(applicationContext) }
    val knxStates by knxResources.stateRepository.stateFlow.collectAsStateWithLifecycle()
    val acsKnxValue = remember(knxStates) {
        knxStates[KnxAddressBook.Consumption.ACS_ACCUMULATED]
            ?.rawValue
            ?.let { KnxValueDecoder.decode(it, "9.001") }
            ?.toDouble()
    }

    LaunchedEffect(repository) {
        EnergyHistorySeeder(applicationContext, repository).seedIfNeeded()
    }

    DisposableEffect(viewModel) {
        onDispose(viewModel::close)
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.selectedType != null) {
            EnergyMeterDetailScreen(
                state = state,
                onBack = viewModel::closeMeter,
                onPeriodSelected = viewModel::selectPeriod,
                onAddReading = viewModel::addReading,
                onEditReading = viewModel::editReading,
                onDeleteReading = viewModel::requestDelete,
                acsKnxValue = acsKnxValue
            )
        } else {
            EnergyDashboardScreen(
                isLoading = state.isLoading,
                summaries = state.summaries,
                overview = state.overview,
                analyticsPoints = analyticsPoints,
                smartEnergy = smartEnergy,
                onBack = onBack,
                onMeterSelected = viewModel::openMeter
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        )
    }

    if (state.isEditorVisible) {
        state.selectedType?.let { type ->
            EnergyReadingDialog(
                type = type,
                reading = state.editorReading,
                acsKnxValue = acsKnxValue,
                onDismiss = viewModel::dismissEditor,
                onSave = viewModel::saveReading
            )
        }
    }

    if (state.readingPendingDeletion != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Eliminar lectura") },
            text = { Text("La lectura se eliminará definitivamente de la base de datos.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Eliminar", color = EnergyRed)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EnergyDashboardScreen(
    isLoading: Boolean,
    summaries: List<com.onehouse.app.data.energy.MeterSummary>,
    overview: com.onehouse.app.data.energy.EnergyOverview,
    analyticsPoints: List<EnergyAnalyticsPoint>,
    smartEnergy: SmartEnergyState,
    onBack: (() -> Unit)?,
    onMeterSelected: (com.onehouse.app.data.energy.MeterType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior, Color.Black)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        if (onBack != null) {
            RoomHeader("Consumos", onBack)
        } else {
            Text(
                "Consumos",
                color = TextoPrincipal,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        }

        Text(
            "Consulta el consumo, histórico y coste de cada suministro",
            color = TextoSecundario,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(18.dp))

        if (isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = EnergyBlue)
                Spacer(Modifier.height(12.dp))
                Text("Cargando históricos…", color = TextoSecundario)
            }
        }

        AnimatedVisibility(
            visible = !isLoading,
            enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 10 }
        ) {
            Column {
                // Vista simplificada temporal: solo se muestran los contadores.
                // Se conservan los datos y componentes de analítica para poder
                // reactivarlos en una entrega futura sin afectar al histórico.
                Text(
                    "Suministros",
                    color = TextoPrincipal,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                val orderedSummaries = summaries.sortedBy { summary ->
                    when (summary.type) {
                        com.onehouse.app.data.energy.MeterType.ENDESA -> 0
                        com.onehouse.app.data.energy.MeterType.AGBAR -> 1
                        com.onehouse.app.data.energy.MeterType.CLIMATIZATION -> 2
                        com.onehouse.app.data.energy.MeterType.ACS -> 3
                    }
                }
                orderedSummaries.chunked(2).forEach { rowSummaries ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowSummaries.forEach { summary ->
                            EnergySummaryCard(
                                summary = summary,
                                modifier = Modifier.weight(1f),
                                onClick = { onMeterSelected(summary.type) }
                            )
                        }
                        if (rowSummaries.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        Spacer(Modifier.height(90.dp))
    }
}
