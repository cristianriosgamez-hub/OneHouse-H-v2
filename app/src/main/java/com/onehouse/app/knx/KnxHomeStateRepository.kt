package com.onehouse.app.knx

import com.onehouse.app.feature.h08.H08Safety

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.onehouse.app.device.ControlKind
import com.onehouse.app.device.ImportedKnxDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import kotlin.math.pow

/** Estado KNX agregado que comparten la portada y todas las estancias. */
data class KnxHomeSnapshot(
    val devices: List<ImportedKnxDevice>,
    val states: Map<String, KnxStateRepository.State>,
    val lightsOn: Int,
    val lightsTotal: Int,
    val blindsOpen: Int,
    val blindsTotal: Int,
    val climate: KnxClimateSnapshot
) {
    fun devicesForRoom(roomName: String): List<ImportedKnxDevice> {
        val aliases = roomAliases(roomName)
        return devices.filter { device ->
            val normalized = normalize(device.roomName)
            normalized.isNotBlank() && aliases.any { alias ->
                normalized.contains(alias) || alias.contains(normalized)
            }
        }
    }

    /**
     * Devuelve únicamente un estado confirmado por el objeto de lectura.
     *
     * Cuando el dispositivo dispone de dirección de estado nunca usamos como
     * respaldo la dirección de mando: un GroupValueWrite antiguo podría hacer
     * aparecer una luz encendida aunque el actuador esté realmente apagado.
     */
    fun booleanValue(device: ImportedKnxDevice): Boolean? = when (device.controlKind) {
        ControlKind.BOOLEAN_SWITCH -> KnxLightStateResolver.confirmedBoolean(device, states)
        ControlKind.ALARM,
        ControlKind.SENSOR,
        ControlKind.READ_ONLY,
        ControlKind.METER,
        ControlKind.TEMPERATURE -> KnxSensorStateResolver.confirmedBoolean(device, states)
        else -> KnxSensorStateResolver.confirmedBoolean(device, states)
    }

    fun numericValue(device: ImportedKnxDevice): Float? = when (device.controlKind) {
        ControlKind.BLIND -> KnxBlindStateResolver.confirmedPositionPercent(device, states)
        ControlKind.TEMPERATURE,
        ControlKind.SENSOR,
        ControlKind.METER,
        ControlKind.ALARM,
        ControlKind.READ_ONLY -> KnxSensorStateResolver.confirmedNumeric(device, states)
        else -> KnxSensorStateResolver.confirmedNumeric(device, states)
    }

    fun booleanAt(address: String): Boolean? =
        states[address]
            ?.takeIf(StateFreshness::isTrusted)
            ?.booleanValue

    fun numericAt(address: String, dpt: String): Float? {
        val raw = states[address]
            ?.takeIf(StateFreshness::isTrusted)
            ?.rawValue
            ?: return null
        return if (KnxDptResolver.mainNumber(dpt) == 9) {
            // Las temperaturas DPT 9 deben ocupar exactamente dos bytes.
            if (raw.length != 4) null else KnxValueDecoder.decode(raw, dpt)
        } else {
            KnxValueDecoder.decode(raw, dpt)
        }
    }

    fun hasTrustedState(address: String): Boolean =
        states[address]?.let(StateFreshness::isTrusted) == true


    companion object {
        private fun normalize(value: String): String = value
            .lowercase(Locale.ROOT)
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .trim()

        private fun roomAliases(roomName: String): Set<String> = when (normalize(roomName)) {
            "entrada" -> setOf("entrada", "recibidor")
            "pasillo" -> setOf("pasillo", "distribuidor")
            "trastero" -> setOf("trastero", "almacen")
            "bano" -> setOf("bano", "aseo")
            "cocina" -> setOf("cocina")
            "habitacion 1" -> setOf("habitacion 1", "dormitorio 1", "estudio")
            "comedor" -> setOf("comedor", "salon", "sala")
            "suite" -> setOf("suite", "principal", "dormitorio principal")
            else -> setOf(normalize(roomName))
        }
    }
}

data class KnxClimateSnapshot(
    val powered: Boolean?,
    val currentTemperature: Float?,
    val targetTemperature: Float?,
    val mode: String?,
    val fanSpeed: String?
)

class KnxHomeStateRepository(context: Context) {
    companion object {
        /**
         * Aviso desde la pantalla de conexión: si la primera carga falló por no
         * existir túnel, la relanza inmediatamente al quedar KNX disponible.
         */
        fun notifyConnectionAvailable() {
            if (!H08Safety.READ_ONLY) PeriodicKnxStateRefresh.onConnectionAvailable()
        }

        /**
         * Direcciones explícitas que forman parte de la carga inicial actual.
         * Se expone de forma controlada para que BackupManager pueda contar
         * exactamente las mismas GAs sin duplicar la lista ni acceder al objeto
         * interno PeriodicKnxStateRefresh.
         */
        fun initialLoadExplicitStateAddresses(): List<String> =
            PeriodicKnxStateRefresh.explicitStateAddresses()
    }

    private val appContext = context.applicationContext
    private val centralResources = KnxCentralEngine.get(appContext)
    private val stateRepository = centralResources.stateRepository
    private val subscriptionManager = centralResources.subscriptionManager
    private val appConfiguration = AppKnxConfigurationRepository(appContext).also(KnxAddressBook::apply)
    private val devices: List<ImportedKnxDevice> = appConfiguration.loadProject()
        ?.let(KnxDeviceFactory::create)
        .orEmpty()

    init {
        KnxProcessSession.prepare(stateRepository)
        if (!H08Safety.READ_ONLY) PeriodicKnxStateRefresh.ensureStarted(appContext, devices)
    }

    val stateFlow: Flow<KnxHomeSnapshot> = subscriptionManager
        .observe(allObservedAddresses())
        .map { states ->
            buildSnapshot(devices, states)
        }

    fun snapshot(): KnxHomeSnapshot =
        buildSnapshot(devices, subscriptionManager.snapshot(allObservedAddresses()))

    private fun allObservedAddresses(): Set<String> = buildSet {
        devices.forEach { device ->
            device.readAddresses.forEach { add(it.toString()) }
            device.writeAddresses.forEach { add(it.toString()) }
        }
        addAll(PeriodicKnxStateRefresh.explicitStateAddresses())
    }

    private fun buildSnapshot(
        devices: List<ImportedKnxDevice>,
        states: Map<String, KnxStateRepository.State>
    ): KnxHomeSnapshot {
        val lights = devices.filter { it.controlKind == ControlKind.BOOLEAN_SWITCH }
        val blinds = devices.filter { it.controlKind == ControlKind.BLIND }
        // v1.12.2: el contador de luces usa la misma resolución de estado
        // confirmado que las estancias. No se deduce el estado del último mando.
        val lightOn = lights.count { device ->
            KnxLightStateResolver.confirmedBoolean(device, states) == true
        }
        // v1.12.3: Inicio y Estancias comparten la misma posición real DPT 5.001.
        // No se interpreta el último mando Up/Down/Stop como posición de persiana.
        val blindOpen = blinds.count { device ->
            val value = KnxBlindStateResolver.confirmedPositionPercent(device, states)
            value != null && value < 95f
        }

        // v1.12.7: la climatización ya tiene una única fuente de verdad.
        // Se elimina aquí la antigua ruta semántica/fallback que calculaba en
        // paralelo modo, ventilador, temperaturas y encendido, aunque su resultado
        // ya no se utilizaba desde v1.12.4. Así evitamos mantener dos motores de
        // resolución distintos para el mismo estado KNX.
        val resolvedClimate = KnxClimateStateResolver.resolve(states)

        return KnxHomeSnapshot(
            devices = devices,
            states = states,
            lightsOn = lightOn,
            lightsTotal = lights.size,
            blindsOpen = blindOpen,
            blindsTotal = blinds.size,
            climate = KnxClimateSnapshot(
                powered = resolvedClimate.powered,
                currentTemperature = resolvedClimate.currentTemperature,
                targetTemperature = resolvedClimate.targetTemperature,
                mode = resolvedClimate.mode,
                fanSpeed = resolvedClimate.fanSpeed
            )
        )
    }

}

private object KnxProcessSession {
    private val lock = Any()
    @Volatile private var prepared = false
    @Volatile var startedAtMillis: Long = Long.MAX_VALUE
        private set

    fun prepare(repository: KnxStateRepository) {
        if (prepared) return
        synchronized(lock) {
            if (prepared) return
            // Se elimina una sola vez la caché de la ejecución anterior. Desde
            // este instante cualquier telegrama recibido pertenece a la sesión
            // actual y no puede quedar invalidado por inicializaciones tardías.
            repository.clear()
            startedAtMillis = System.currentTimeMillis()
            prepared = true
        }
    }
}

internal object StateFreshness {
    fun isTrusted(state: KnxStateRepository.State): Boolean =
        state.timestampMillis >= KnxProcessSession.startedAtMillis
}

/**
 * Mantiene actualizados los objetos KNX de lectura mientras la aplicación está
 * activa. Las lecturas se realizan de forma secuencial para no saturar el túnel
 * KNX/IP y se repiten cada minuto al terminar el ciclo anterior.
 */
private object PeriodicKnxStateRefresh {
    private const val REFRESH_INTERVAL_MILLIS = 60_000L
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var activeReader: KnxBulkStateReader? = null
    private var activeSignature: String? = null
    private var scheduledSignature: String? = null
    private var scheduledRunnable: Runnable? = null
    private var initialLoadTracked = false
    private var lastContext: Context? = null
    private var lastReadable: List<ImportedKnxDevice> = emptyList()
    private var lastSignature: String? = null

    fun explicitStateAddresses(): List<String> = listOf(
        KnxAddressBook.Climate.POWER_STATE,
        KnxAddressBook.Climate.CURRENT_TEMPERATURE,
        KnxAddressBook.Climate.MODE_STATE,
        KnxAddressBook.Climate.FAN_SPEED_STATE,
        KnxAddressBook.Climate.TARGET_TEMPERATURE_PRIMARY,
        KnxAddressBook.Climate.TARGET_TEMPERATURE_FALLBACK,
        KnxAddressBook.Indoor.CO2_DINING,
        KnxAddressBook.Indoor.HUMIDITY_DINING,
        KnxAddressBook.Indoor.TEMPERATURE_DINING,
        KnxAddressBook.Indoor.TEMPERATURE_SUITE,
        KnxAddressBook.Indoor.PIR_BLOCK_ENTRANCE,
        KnxAddressBook.Indoor.FLOOD_KITCHEN,
        KnxAddressBook.Indoor.FLOOD_BATHROOM,
        KnxAddressBook.Indoor.FIRE_HALLWAY,
        KnxAddressBook.Terrace.LUMINOSITY,
        KnxAddressBook.Terrace.EXCESSIVE_WIND,
        KnxAddressBook.Terrace.WIND_SPEED,
        KnxAddressBook.Terrace.RAINING,
        KnxAddressBook.Consumption.ACS_ACCUMULATED,
        KnxStartupReadPolicy.VALVE_STATE
    ).distinct()

    fun ensureStarted(context: Context, devices: List<ImportedKnxDevice>) {
        val readable = devices.filter { it.canRead }
        val explicitStateAddresses = explicitStateAddresses()
        if (readable.isEmpty() && explicitStateAddresses.isEmpty()) return

        val signature = (
            readable.flatMap { device -> device.readAddresses.map { it.toString() } } +
                explicitStateAddresses
            )
            .distinct()
            .sorted()
            .joinToString("|")

        synchronized(lock) {
            lastContext = context.applicationContext
            lastReadable = readable
            lastSignature = signature
            if (signature == activeSignature || signature == scheduledSignature) return
            startCycleLocked(context.applicationContext, readable, signature)
        }
    }

    fun onConnectionAvailable() {
        synchronized(lock) {
            if (initialLoadTracked || activeReader != null) return
            val context = lastContext ?: return
            val signature = lastSignature ?: return
            if (lastReadable.isEmpty() && explicitStateAddresses().isEmpty()) return

            // Cancela la espera periódica de 60 s: una conexión confirmada debe
            // disparar la carga inicial en ese mismo momento.
            scheduledRunnable?.let(handler::removeCallbacks)
            scheduledRunnable = null
            scheduledSignature = null
            startCycleLocked(context, lastReadable, signature)
        }
    }

    private fun startCycleLocked(
        context: Context,
        readable: List<ImportedKnxDevice>,
        signature: String
    ) {
        scheduledRunnable?.let(handler::removeCallbacks)
        scheduledRunnable = null
        scheduledSignature = null
        activeReader?.close()

        val reader = KnxBulkStateReader(context)
        activeReader = reader
        activeSignature = signature

        val prioritized = readable.sortedBy { device ->
            when {
                device.controlKind == ControlKind.CLIMATE -> 0
                device.name.contains("daikin", ignoreCase = true) -> 0
                device.name.contains("termostato", ignoreCase = true) -> 0
                device.controlKind == ControlKind.TEMPERATURE -> 1
                device.controlKind == ControlKind.SENSOR -> 2
                device.controlKind == ControlKind.ALARM -> 2
                device.controlKind == ControlKind.METER -> 2
                device.controlKind == ControlKind.READ_ONLY -> 2
                device.controlKind == ControlKind.BOOLEAN_SWITCH -> 3
                device.controlKind == ControlKind.BLIND -> 4
                else -> 5
            }
        }

        // Además de los sensores globales, adelantamos las direcciones de estado
        // de las luces. Así, al abrir la app, los interruptores visibles y el
        // contador de la portada se pintan antes de recorrer todo el proyecto.
        val startupPriorityAddresses = (
            explicitStateAddresses() +
                prioritized
                    .filter { it.controlKind == ControlKind.BOOLEAN_SWITCH }
                    .flatMap { device -> device.readAddresses.map { it.toString() } }
            ).distinct()

        val trackInitialLoad = !initialLoadTracked
        if (trackInitialLoad) {
            // v1.13.0.5: la carga inicial ya no depende de que OneHouse permanezca
            // en primer plano. El servicio foreground mantiene vivo el proceso si
            // el usuario abre otra aplicación mientras todavía quedan GAs/reintentos.
            KnxInitialLoadForegroundService.start(context)
        }
        reader.read(
            devices = prioritized,
            extraReadAddresses = startupPriorityAddresses,
            trackInitialLoadProgress = trackInitialLoad,
            onProgress = { },
            onComplete = { result ->
                reader.close()
                if (trackInitialLoad) {
                    KnxInitialLoadForegroundService.stop(context)
                }
                synchronized(lock) {
                    // Si no llegó a ejecutarse la ronda (p. ej. sin conexión),
                    // NO damos por consumida la carga inicial. Así una conexión
                    // manual posterior puede relanzarla y el 0 % deja de quedarse fijo.
                    if (trackInitialLoad && result.total > 0 && result.completed >= result.total) {
                        initialLoadTracked = true
                    }
                    if (activeReader !== reader) return@synchronized
                    activeReader = null
                    activeSignature = null

                    val next = Runnable {
                        synchronized(lock) {
                            if (scheduledSignature != signature) return@synchronized
                            scheduledRunnable = null
                            scheduledSignature = null
                            startCycleLocked(context, readable, signature)
                        }
                    }
                    scheduledRunnable = next
                    scheduledSignature = signature
                    // v1.12.6.1: la recuperación temprana ya se realiza dentro
                    // de KnxBulkStateReader únicamente sobre las GAs pendientes.
                    // Evitamos repetir aquí una pasada completa a los 8 segundos.
                    handler.postDelayed(next, REFRESH_INTERVAL_MILLIS)
                }
            }
        )
    }
}

object KnxValueDecoder {
    /**
     * Decodifica respetando el DPT indicado y corrige exportaciones de
     * configuraciones donde una magnitud de 4 bytes llega etiquetada como DPT 9.
     */
    fun decodeFlexible(rawHex: String?, dpt: String): Float? {
        val primary = decode(rawHex, dpt)
        val main = KnxDptResolver.mainNumber(dpt)
        val byteCount = rawHex?.length?.div(2) ?: 0

        return when {
            main == 9 && byteCount >= 4 && (primary == null || primary !in -100f..1000f) ->
                decode(rawHex, "14.000") ?: primary
            main == 14 && byteCount == 2 && (primary == null || !primary.isFinite()) ->
                decode(rawHex, "9.001") ?: primary
            else -> primary
        }
    }

    fun decode(rawHex: String?, dpt: String): Float? {
        val bytes = rawHex
            ?.takeIf { it.length >= 2 && it.length % 2 == 0 }
            ?.chunked(2)
            ?.mapNotNull { it.toIntOrNull(16) }
            ?: return rawHex?.toFloatOrNull()

        return when (KnxDptResolver.mainNumber(dpt)) {
            1 -> (bytes.lastOrNull()?.and(1) ?: return null).toFloat()
            5 -> (bytes.lastOrNull() ?: return null) * 100f / 255f
            9 -> decodeDpt9(bytes)
            14 -> decodeDpt14(bytes)
            20 -> bytes.lastOrNull()?.toFloat()
            else -> bytes.lastOrNull()?.toFloat()
        }
    }

    private fun decodeDpt14(bytes: List<Int>): Float? {
        if (bytes.size < 4) return null
        val start = bytes.size - 4
        val bits = ((bytes[start].toLong() and 0xFF) shl 24) or
            ((bytes[start + 1].toLong() and 0xFF) shl 16) or
            ((bytes[start + 2].toLong() and 0xFF) shl 8) or
            (bytes[start + 3].toLong() and 0xFF)
        return Float.fromBits(bits.toInt())
    }

    private fun decodeDpt9(bytes: List<Int>): Float? {
        if (bytes.size < 2) return null
        val raw = (bytes[bytes.size - 2] shl 8) or bytes.last()
        val sign = if ((raw and 0x8000) != 0) -1 else 1
        val exponent = (raw ushr 11) and 0x0F
        var mantissa = raw and 0x07FF
        if (sign < 0) mantissa -= 2048
        return (0.01f * mantissa * 2.0.pow(exponent.toDouble())).toFloat()
    }
}
