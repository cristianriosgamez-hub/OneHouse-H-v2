package com.onehouse.app.knx

import com.onehouse.app.device.ImportedKnxDevice

/**
 * Resolución central del estado real de luces KNX (v1.12.2).
 *
 * Las pantallas no deducen el estado a partir de una variable local. Se utiliza
 * el telegrama KNX confirmado más reciente asociado al dispositivo. Esto permite
 * reflejar tanto la GA de estado del actuador como los GroupValueWrite emitidos
 * por pulsadores físicos sobre la GA de mando.
 */
internal object KnxLightStateResolver {

    fun stateAddresses(device: ImportedKnxDevice): List<String> =
        (device.readAddresses + device.writeAddresses)
            .map { it.toString() }
            .distinct()

    fun confirmedBoolean(
        device: ImportedKnxDevice,
        states: Map<String, KnxStateRepository.State>
    ): Boolean? {
        // Un pulsador físico suele publicar un GroupValueWrite sobre la GA de mando.
        // Algunos actuadores no emiten después una GA de estado independiente, por
        // lo que ignorar siempre la escritura dejaba la UI congelada. Tomamos el
        // telegrama confirmado más reciente entre lectura y mando; si posteriormente
        // llega la GA de estado del actuador, al ser más nueva pasa a ser autoritativa.
        return stateAddresses(device)
            .asSequence()
            .mapNotNull { address -> states[address]?.takeIf(StateFreshness::isTrusted) }
            .filter { state -> state.booleanValue != null }
            .maxByOrNull { state -> state.timestampMillis }
            ?.booleanValue
    }
}
