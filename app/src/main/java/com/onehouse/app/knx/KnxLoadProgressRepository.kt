package com.onehouse.app.knx

import com.onehouse.app.device.ControlKind
import com.onehouse.app.device.ImportedKnxDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class KnxLoadKind(val label: String) {
    LIGHT("Luces"),
    BLIND("Persianas"),
    CLIMATE("Climatización"),
    TEMPERATURE("Temperaturas"),
    SENSOR("Sensores"),
    METER("Mediciones"),
    OTHER("Otros")
}

data class KnxLoadTarget(
    val address: String,
    val rooms: Set<String>,
    val kinds: Set<KnxLoadKind>,
    val names: Set<String>
)

data class KnxLoadProgressSnapshot(
    val targets: Map<String, KnxLoadTarget> = emptyMap(),
    val receivedAddresses: Set<String> = emptySet(),
    val noResponseAddresses: Set<String> = emptySet(),
    val currentAddress: String? = null,
    val finished: Boolean = false
) {
    val eventAddresses: Set<String> get() = targets.keys.filterTo(mutableSetOf(), KnxStartupReadPolicy::isEvent)
    val waitingEventAddresses: Set<String> get() = eventAddresses - receivedAddresses
    val total: Int get() = (targets.keys - eventAddresses).size
    val received: Int get() = (receivedAddresses.intersect(targets.keys) - eventAddresses).size
    val noResponse: Int get() = noResponseAddresses.size
    val completed: Int get() = received
    val resolved: Int get() = ((receivedAddresses + noResponseAddresses).intersect(targets.keys) - eventAddresses).size
    val pending: Int get() = (total - resolved).coerceAtLeast(0)
    /**
     * Porcentaje REAL de estados recibidos.
     *
     * Solo cuenta estados de lectura realmente recibidos. Los eventos se
     * muestran aparte y su silencio nunca se interpreta como un valor.
     */
    val percent: Int
        get() = when {
            total <= 0 -> 0
            else -> ((completed * 100f) / total).toInt().coerceIn(0, 100)
        }
}

/**
 * Progreso visible de la primera carga KNX del proceso actual.
 *
 * El porcentaje representa únicamente direcciones con un estado KNX realmente
 * recibido y válido. Las GAs que agotan sus reintentos quedan separadas como
 * "sin respuesta" y nunca hacen avanzar artificialmente el porcentaje.
 */
object KnxLoadProgressRepository {
    private val mutableProgress = MutableStateFlow(KnxLoadProgressSnapshot())
    val progress: StateFlow<KnxLoadProgressSnapshot> = mutableProgress.asStateFlow()

    @Synchronized
    fun begin(targets: Map<String, KnxLoadTarget>) {
        mutableProgress.value = KnxLoadProgressSnapshot(targets = targets)
    }

    @Synchronized
    fun setCurrent(address: String?) {
        val current = mutableProgress.value
        if (current.finished) return
        mutableProgress.value = current.copy(currentAddress = address)
    }

    @Synchronized
    fun syncReceived(states: Map<String, KnxStateRepository.State>) {
        val current = mutableProgress.value
        if (current.targets.isEmpty()) return
        val received = current.targets.keys.filterTo(mutableSetOf()) { address ->
            states[address]?.let(StateFreshness::isTrusted) == true
        }
        mutableProgress.value = current.copy(
            receivedAddresses = received,
            noResponseAddresses = current.noResponseAddresses - received
        )
    }

    /**
     * REV3: reconcilia en tiempo real una GA que quedó pendiente al cerrar la
     * carga inicial y cuyo telegrama válido llega después. No genera ninguna
     * lectura adicional: únicamente corrige la fotografía de progreso usando
     * el mismo telegrama que ya ha aceptado la caché KNX central.
     *
     * Los eventos recibidos después de la carga también actualizan su indicador.
     */
    @Synchronized
    fun reconcileAcceptedState(state: KnxStateRepository.State) {
        val current = mutableProgress.value
        if (!current.finished || current.targets.isEmpty()) return

        val address = state.groupAddress
        if (address !in current.targets) return
        if (!StateFreshness.isTrusted(state)) return
        if (address in current.receivedAddresses) return

        mutableProgress.value = current.copy(
            receivedAddresses = current.receivedAddresses + address,
            noResponseAddresses = current.noResponseAddresses - address
        )
    }

    @Synchronized
    fun finish(finalNoResponse: Collection<String>, states: Map<String, KnxStateRepository.State>) {
        val current = mutableProgress.value
        if (current.targets.isEmpty()) return
        val received = current.targets.keys.filterTo(mutableSetOf()) { address ->
            states[address]?.let(StateFreshness::isTrusted) == true
        }
        val unresolved = (finalNoResponse + current.targets.keys)
            .asSequence()
            .filter { it in current.targets }
            .filterNot { it in received }
            .filterNot(KnxStartupReadPolicy::isEvent)
            .toSet()

        mutableProgress.value = current.copy(
            receivedAddresses = received,
            noResponseAddresses = unresolved,
            currentAddress = null,
            finished = true
        )
    }

    fun buildTargets(
        devices: List<ImportedKnxDevice>,
        addresses: List<String>
    ): Map<String, KnxLoadTarget> {
        val normalizedAddresses = addresses.toSet()
        val descriptors = linkedMapOf<String, MutableDescriptor>()

        fun descriptor(address: String): MutableDescriptor =
            descriptors.getOrPut(address) { MutableDescriptor() }

        devices.forEach { device ->
            val kind = kindFor(device.controlKind)
            val room = device.roomName.trim().ifBlank { "Vivienda" }
            val related = buildList {
                addAll(device.readAddresses.map { it.toString() })
                if (device.controlKind == ControlKind.BOOLEAN_SWITCH ||
                    device.controlKind == ControlKind.CLIMATE
                ) {
                    addAll(device.writeAddresses.map { it.toString() })
                }
            }.distinct()

            // La climatización se describe dirección a dirección más abajo.
            // Evitamos agrupar sus cinco estados bajo un único elemento genérico.
            if (device.controlKind != ControlKind.CLIMATE) {
                related.filter { it in normalizedAddresses }.forEach { address ->
                    descriptor(address).apply {
                        rooms += room
                        kinds += kind
                        names += device.name.trim().ifBlank { kind.label }
                    }
                }
            }
        }

        addresses.forEach { address ->
            if (address !in descriptors) {
                val fallback = explicitDescriptor(address)
                descriptor(address).apply {
                    rooms += fallback.first
                    kinds += fallback.second
                    names += fallback.third
                }
            }
        }

        return addresses.associateWith { address ->
            val item = descriptors.getValue(address)
            KnxLoadTarget(
                address = address,
                rooms = item.rooms.toSet(),
                kinds = item.kinds.toSet(),
                names = item.names.toSet()
            )
        }
    }

    private fun kindFor(controlKind: ControlKind): KnxLoadKind = when (controlKind) {
        ControlKind.BOOLEAN_SWITCH -> KnxLoadKind.LIGHT
        ControlKind.BLIND -> KnxLoadKind.BLIND
        ControlKind.CLIMATE -> KnxLoadKind.CLIMATE
        ControlKind.TEMPERATURE -> KnxLoadKind.TEMPERATURE
        ControlKind.ALARM,
        ControlKind.SENSOR,
        ControlKind.READ_ONLY -> KnxLoadKind.SENSOR
        ControlKind.METER -> KnxLoadKind.METER
        else -> KnxLoadKind.OTHER
    }

    private fun explicitDescriptor(address: String): Triple<String, KnxLoadKind, String> = when (address) {
        KnxStartupReadPolicy.VALVE_STATE -> Triple("Mantenimiento", KnxLoadKind.OTHER, "Estado electroválvula de agua")
        KnxAddressBook.Climate.POWER_STATE ->
            Triple("Climatización", KnxLoadKind.CLIMATE, "Encendido / apagado")
        KnxAddressBook.Climate.CURRENT_TEMPERATURE ->
            Triple("Climatización", KnxLoadKind.CLIMATE, "Temperatura actual")
        KnxAddressBook.Climate.MODE_STATE ->
            Triple("Climatización", KnxLoadKind.CLIMATE, "Modo")
        KnxAddressBook.Climate.FAN_SPEED_STATE ->
            Triple("Climatización", KnxLoadKind.CLIMATE, "Ventilador")
        KnxAddressBook.Climate.TARGET_TEMPERATURE_PRIMARY,
        KnxAddressBook.Climate.TARGET_TEMPERATURE_FALLBACK ->
            Triple("Climatización", KnxLoadKind.CLIMATE, "Consigna")

        KnxAddressBook.Indoor.CO2_DINING -> Triple("Comedor", KnxLoadKind.SENSOR, "CO₂")
        KnxAddressBook.Indoor.HUMIDITY_DINING -> Triple("Comedor", KnxLoadKind.SENSOR, "Humedad")
        KnxAddressBook.Indoor.TEMPERATURE_DINING -> Triple("Comedor", KnxLoadKind.TEMPERATURE, "Temperatura")
        KnxAddressBook.Indoor.TEMPERATURE_SUITE -> Triple("Suite", KnxLoadKind.TEMPERATURE, "Temperatura")
        KnxAddressBook.Indoor.PIR_BLOCK_ENTRANCE -> Triple("Entrada", KnxLoadKind.SENSOR, "PIR")
        KnxAddressBook.Indoor.FLOOD_KITCHEN -> Triple("Cocina", KnxLoadKind.SENSOR, "Inundación")
        KnxAddressBook.Indoor.FLOOD_BATHROOM -> Triple("Baño", KnxLoadKind.SENSOR, "Inundación")
        KnxAddressBook.Indoor.FIRE_HALLWAY -> Triple("Pasillo", KnxLoadKind.SENSOR, "Incendio")
        KnxAddressBook.Terrace.LUMINOSITY -> Triple("Terraza", KnxLoadKind.SENSOR, "Luminosidad")
        KnxAddressBook.Terrace.EXCESSIVE_WIND -> Triple("Terraza", KnxLoadKind.SENSOR, "Viento excesivo")
        KnxAddressBook.Terrace.WIND_SPEED -> Triple("Terraza", KnxLoadKind.SENSOR, "Velocidad del viento")
        KnxAddressBook.Terrace.RAINING -> Triple("Terraza", KnxLoadKind.SENSOR, "Lluvia")
        else -> Triple("Vivienda", KnxLoadKind.OTHER, address)
    }

    private class MutableDescriptor {
        val rooms = linkedSetOf<String>()
        val kinds = linkedSetOf<KnxLoadKind>()
        val names = linkedSetOf<String>()
    }
}
