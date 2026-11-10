package com.onehouse.app.knx

import com.onehouse.app.device.ImportedKnxDevice

/**
 * Resolución central de sensores y estados de solo lectura KNX (v1.12.5).
 *
 * La interfaz consume siempre el telegrama KNX válido más reciente asociado al
 * objeto. Se priorizan las direcciones de lectura y solo se usa una dirección de
 * escritura como respaldo cuando el objeto importado no expone lectura separada.
 */
internal object KnxSensorStateResolver {

    fun stateAddresses(device: ImportedKnxDevice): List<String> {
        val reads = device.readAddresses.map { it.toString() }
        val writes = device.writeAddresses.map { it.toString() }
        return (reads + writes).distinct()
    }

    fun confirmedBoolean(
        device: ImportedKnxDevice,
        states: Map<String, KnxStateRepository.State>
    ): Boolean? = trustedStates(device, states)
        .filter { state -> state.booleanValue != null }
        .maxByOrNull { state -> state.timestampMillis }
        ?.booleanValue

    fun confirmedNumeric(
        device: ImportedKnxDevice,
        states: Map<String, KnxStateRepository.State>
    ): Float? = trustedStates(device, states)
        .sortedByDescending { state -> state.timestampMillis }
        .mapNotNull { state ->
            KnxValueDecoder.decodeFlexible(state.rawValue, device.resolvedDpt)
                ?.takeIf { value -> value.isFinite() }
        }
        .firstOrNull()

    private fun trustedStates(
        device: ImportedKnxDevice,
        states: Map<String, KnxStateRepository.State>
    ): Sequence<KnxStateRepository.State> {
        val readStates = device.readAddresses
            .asSequence()
            .mapNotNull { address -> states[address.toString()]?.takeIf(StateFreshness::isTrusted) }
            .toList()

        // En sensores, alarmas y mediciones la GA de lectura es autoritativa.
        // Solo caemos a escrituras si el importador no proporciona ninguna
        // lectura válida para el objeto.
        if (readStates.isNotEmpty()) return readStates.asSequence()

        return device.writeAddresses
            .asSequence()
            .mapNotNull { address -> states[address.toString()]?.takeIf(StateFreshness::isTrusted) }
    }
}
