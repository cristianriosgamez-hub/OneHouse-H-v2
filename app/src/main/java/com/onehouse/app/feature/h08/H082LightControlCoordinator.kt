package com.onehouse.app.feature.h08

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.onehouse.app.data.knx.SettingsDataStore
import com.onehouse.app.device.ImportedKnxDevice
import com.onehouse.app.knx.KnxCommandType
import com.onehouse.app.knx.KnxCentralEngine
import com.onehouse.knxlab.knx.H07LightControlRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ÚNICA excepción de escritura de H08.2: luces de Cocina que ya pasaron H07.1.
 * El gestor KNX histórico sigue cerrado por H08Safety.READ_ONLY = true.
 * Una lectura H limpia habilita como máximo UNA escritura, con confirmación
 * expresa en la pantalla real de Cocina, sin reintentos y con túneles separados.
 */
object H082LightControlCoordinator {
    data class ControlState(
        val busy: Boolean = false,
        val consumedRunId: String = "",
        val message: String = "H08.2 TEST: ejecuta Leer H antes de controlar Cocina.",
        val lastResult: H07LightControlRunner.Result? = null,
        val sourceRunId: String = ""
    )

    private val handler = Handler(Looper.getMainLooper())
    private val runner = H07LightControlRunner()
    private val mutableState = MutableStateFlow(ControlState())
    val state = mutableState.asStateFlow()

    fun isBusy(): Boolean = mutableState.value.busy

    fun isDeviceWhitelisted(device: ImportedKnxDevice?, light: H07LightControlRunner.Light): Boolean =
        device != null &&
        device.readAddresses.any { it.toString() == light.statusGa } &&
        device.commands.any { it.type == KnxCommandType.ON && it.destination.toString() == light.commandGa } &&
        device.commands.any { it.type == KnxCommandType.OFF && it.destination.toString() == light.commandGa }

    fun canControl(context: Context, light: H07LightControlRunner.Light, device: ImportedKnxDevice?): Boolean {
        if (!isDeviceWhitelisted(device, light)) return false
        val read = H08Coordinator.status.value
        val control = mutableState.value
        if (!H091MonitorCoordinator.permitsReadId(read.lastRunId) || !read.lastRunSuccess || !read.success || read.running || control.busy || H091MonitorCoordinator.isBusy()) return false
        if (read.imported != H08Safety.EXPECTED_READS || read.lastRunId.isBlank()) return false
        if (read.lastRunId == control.consumedRunId) return false
        if (read.runFinishedAtEpochMillis <= 0 || System.currentTimeMillis() - read.runFinishedAtEpochMillis !in 0L..180_000L) return false
        val app = context.applicationContext
        if (!H08Coordinator.wifiOnly(app)) return false
        val settings = SettingsDataStore(app).read()
        if (read.lastEndpoint != "${settings.localIp}:${settings.localPort}") return false
        // No usar estado de mando ni inferir OFF por ausencia de respuesta.
        val realState = KnxCentralEngine.get(app).stateRepository.get(light.statusGa)
        return realState?.booleanValue != null &&
            realState.timestampMillis >= read.runStartedAtEpochMillis &&
            realState.sourceAddress.isNotBlank()
    }

    /** Solo llamar después de una interacción del usuario y de confirmar el diálogo. */
    fun execute(context: Context, light: H07LightControlRunner.Light, device: ImportedKnxDevice?, on: Boolean): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!canControl(context, light, device)) {
            mutableState.value = mutableState.value.copy(message = "Mando bloqueado: requiere lectura H completa y reciente, GA de estado conocida y permiso sin consumir.")
            return false
        }
        val app = context.applicationContext
        val read = H08Coordinator.status.value
        val settings = SettingsDataStore(app).read()
        val port = settings.localPort.toIntOrNull() ?: return false
        val current = KnxCentralEngine.get(app).stateRepository.get(light.statusGa)?.booleanValue
        if (current == null || current == on) {
            mutableState.value = mutableState.value.copy(message = "No se envía: el estado ya coincide o no está disponible.")
            return false
        }
        // Consumir antes de abrir socket, para evitar doble toque / recomposición.
        mutableState.value = mutableState.value.copy(
            busy = true, consumedRunId = read.lastRunId, sourceRunId = read.lastRunId,
            lastResult = null, message = "Enviando UNA orden a ${light.label}; no repitas ni abras otra lectura."
        )
        val began = runner.execute(settings.localIp, port, on, light) { result ->
            handler.post {
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    lastResult = result,
                    message = result.summary + ". Después de comprobar físicamente, pulsa Leer H para actualizar la pantalla."
                )
            }
        }
        if (!began) {
            // El permiso sigue consumido: si el ejecutor estaba ocupado no debemos reintentar.
            mutableState.value = mutableState.value.copy(busy = false, message = "El ejecutor de luces estaba ocupado. No reintentar sin nueva lectura H.")
        }
        return began
    }

    fun exportSummaryForReadCsv(): String {
        val s = state.value
        val result = s.lastResult
        return if (result == null) "sin_control_o_control_en_curso" else
            "lectura_origen=${s.sourceRunId};dispositivo=${result.light.label};" +
                "escrituras=${result.writeCount};ack=${result.gatewayAck};" +
                "estado_confirmado=${result.confirmedByState};cierre=${result.disconnectStatus}"
    }

    fun exportControlCsv(): String = buildString {
        appendLine("ONEHOUSE_H082_CONTROL_REAL_COCINA_TEST")
        appendLine("permisos_lectura;UNA_ORDEN_TOTAL_POR_LECTURA_H_LIMPIA")
        appendLine("motor_legacy;BLOQUEADO")
        appendLine("automaciones;DESACTIVADAS")
        appendLine("lectura_id_origen;${state.value.sourceRunId}")
        appendLine("lectura_actual;${H08Coordinator.status.value.lastRunId}")
        appendLine("sin_reintentos_escritura;true")
        appendLine("observacion_fisica;PENDIENTE_CONFIRMACION_USUARIO")
        appendLine()
        append(state.value.lastResult?.csv() ?: "NO_HAY_ORDEN_FINALIZADA\n")
    }
}
